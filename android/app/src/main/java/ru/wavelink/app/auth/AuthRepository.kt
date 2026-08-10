package ru.wavelink.app.auth

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import ru.wavelink.app.core.net.AuthBody
import ru.wavelink.app.core.net.RefreshBody
import ru.wavelink.app.core.net.TokenPairDto
import ru.wavelink.app.core.net.WaveLinkApi
import ru.wavelink.app.core.prefs.TokenStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: WaveLinkApi,
    private val tokens: TokenStore
) {
    /**
     * Holding a token pair counts as signed in. Deliberately does not probe the network — the
     * app must open into a usable, cached library while offline.
     */
    val isSignedIn: Flow<Boolean> = tokens.tokens.map { it != null }

    val tokenState: StateFlow<TokenPairDto?> = tokens.tokens

    fun username(): String? = tokens.username()

    suspend fun login(username: String, password: String) {
        tokens.save(api.login(body = AuthBody(username.trim(), password)))
    }

    suspend fun register(username: String, password: String) {
        tokens.save(api.register(body = AuthBody(username.trim(), password)))
    }

    suspend fun logout() {
        val refresh = tokens.current()?.refreshToken
        // Revoking is best-effort: a failure must never trap the user in a signed-in state.
        if (refresh != null) runCatching { api.logout(body = RefreshBody(refresh)) }
        tokens.clear()
    }
}
