package ru.wavelink.app.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import ru.wavelink.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "wavelink.settings")

/** User-tweakable settings: which server to talk to and how much disk the stream cache may use. */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {

    val baseUrl: Flow<String> = context.dataStore.data.map { it[KEY_BASE_URL] ?: BuildConfig.DEFAULT_API_URL }
    val cacheBytes: Flow<Long> = context.dataStore.data.map { it[KEY_CACHE_BYTES] ?: DEFAULT_CACHE_BYTES }

    /**
     * Read once at startup for the Retrofit/OkHttp graph, which is built eagerly and cannot
     * suspend. Changing the base URL therefore takes effect after an app restart.
     */
    fun baseUrlBlocking(): String = runBlocking { baseUrl.first() }

    fun cacheBytesBlocking(): Long = runBlocking { cacheBytes.first() }

    suspend fun setBaseUrl(value: String) {
        val normalized = value.trim().ifEmpty { BuildConfig.DEFAULT_API_URL }
            .let { if (it.endsWith("/")) it else "$it/" }
        context.dataStore.edit { it[KEY_BASE_URL] = normalized }
    }

    suspend fun setCacheBytes(value: Long) {
        context.dataStore.edit { it[KEY_CACHE_BYTES] = value.coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES) }
    }

    companion object {
        private val KEY_BASE_URL: Preferences.Key<String> = stringPreferencesKey("baseUrl")
        private val KEY_CACHE_BYTES: Preferences.Key<Long> = longPreferencesKey("cacheBytes")

        const val DEFAULT_CACHE_BYTES = 512L * 1024 * 1024
        const val MIN_CACHE_BYTES = 64L * 1024 * 1024
        const val MAX_CACHE_BYTES = 8L * 1024 * 1024 * 1024
    }
}
