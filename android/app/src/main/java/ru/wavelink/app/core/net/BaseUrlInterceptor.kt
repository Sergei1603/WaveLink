package ru.wavelink.app.core.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import ru.wavelink.app.core.prefs.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrofit resolves endpoints against [PLACEHOLDER_BASE_URL], and this interceptor swaps that
 * placeholder for whatever server the user has configured. Retrofit's own `baseUrl` is fixed at
 * construction time, so binding it directly to the setting meant a new address only took effect
 * after an app restart — which is exactly the trap the sign-in screen's server field would set.
 *
 * The configured path (if any) is kept as a prefix, so `http://host/wavelink/` works as well as
 * a bare host.
 */
@Singleton
class BaseUrlInterceptor @Inject constructor(private val settings: SettingsStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val base = settings.baseUrlBlocking().toHttpUrlOrNull() ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().url(rewrite(request.url, base)).build())
    }

    companion object {
        /**
         * Never contacted: every request is rewritten before it leaves. It only has to be a
         * syntactically valid, path-less base for Retrofit to resolve `api/...` against.
         */
        const val PLACEHOLDER_BASE_URL = "http://wavelink.invalid/"

        /** Pure so it can be tested without standing up a chain. */
        fun rewrite(requestUrl: HttpUrl, base: HttpUrl): HttpUrl = base.newBuilder()
            .encodedPath(base.encodedPath.trimEnd('/') + requestUrl.encodedPath)
            .encodedQuery(requestUrl.encodedQuery)
            .build()
    }
}
