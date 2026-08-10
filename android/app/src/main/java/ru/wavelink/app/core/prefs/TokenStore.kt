package ru.wavelink.app.core.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.wavelink.app.core.net.TokenPairDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the JWT pair. Backed by EncryptedSharedPreferences rather than DataStore because the
 * refresh token is a long-lived credential and DataStore has no first-party encryption.
 *
 * Exposes both a [StateFlow] (for the UI) and a synchronous [current] — the OkHttp interceptor
 * and the ExoPlayer data source both need the token off the main thread without suspending.
 */
@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "wavelink.tokens",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _tokens = MutableStateFlow(read())
    val tokens: StateFlow<TokenPairDto?> = _tokens.asStateFlow()

    /** True when a token pair is stored. Deliberately does not require the network. */
    val isSignedIn: Boolean get() = _tokens.value != null

    fun current(): TokenPairDto? = _tokens.value

    @Synchronized
    fun save(pair: TokenPairDto) {
        prefs.edit()
            .putString(KEY_ACCESS, pair.accessToken)
            .putString(KEY_REFRESH, pair.refreshToken)
            .putString(KEY_EXPIRES, pair.accessTokenExpiresAt)
            .apply()
        _tokens.value = pair
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
        _tokens.value = null
    }

    /** The nickname carried in the access token's `username` claim, decoded locally. */
    fun username(): String? {
        val token = _tokens.value?.accessToken ?: return null
        return runCatching {
            val payload = token.split(".")[1]
            val json = String(
                android.util.Base64.decode(
                    payload,
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
                )
            )
            Regex("\"username\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)
        }.getOrNull()
    }

    private fun read(): TokenPairDto? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val expires = prefs.getString(KEY_EXPIRES, null) ?: ""
        return TokenPairDto(access, refresh, expires)
    }

    private companion object {
        const val KEY_ACCESS = "accessToken"
        const val KEY_REFRESH = "refreshToken"
        const val KEY_EXPIRES = "accessTokenExpiresAt"
    }
}
