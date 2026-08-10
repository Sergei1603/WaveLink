package ru.wavelink.app.core.net

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import ru.wavelink.app.core.prefs.TokenStore
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Attaches the bearer token to every request except those marked [WaveLinkApi.NO_AUTH]. */
@Singleton
class AuthInterceptor @Inject constructor(private val store: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(WaveLinkApi.NO_AUTH) != null) {
            return chain.proceed(request.newBuilder().removeHeader(WaveLinkApi.NO_AUTH).build())
        }
        val token = store.current() ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder().header("Authorization", "Bearer ${token.accessToken}").build()
        )
    }
}

/**
 * Mirrors the web client's 401 → refresh → retry-once flow.
 *
 * The mutex plays the role of the browser's shared `refreshPromise`: when several requests fail
 * at the same moment only the first one refreshes, and the rest pick up the new token. The
 * `Provider` breaks the OkHttp ↔ Retrofit construction cycle.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val store: TokenStore,
    private val api: Provider<WaveLinkApi>
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Retry exactly once — a second 401 means the refresh token is dead too.
        if (responseCount(response) > 1) return null

        val stale = response.request.header("Authorization")?.removePrefix("Bearer ")

        val fresh = runBlocking {
            mutex.withLock {
                val now = store.current() ?: return@withLock null
                // Someone else already refreshed while we waited for the lock.
                if (stale != null && now.accessToken != stale) return@withLock now

                runCatching { api.get().refresh(body = RefreshBody(now.refreshToken)) }
                    .onSuccess { store.save(it) }
                    .onFailure { store.clear() }
                    .getOrNull()
            }
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${fresh.accessToken}")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
