package ru.wavelink.app.core.net

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import ru.wavelink.app.BuildConfig
import ru.wavelink.app.core.prefs.SettingsStore
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * One client for both Retrofit and the ExoPlayer data source, so that streaming inherits the
     * 401 → refresh → retry behaviour for free.
     */
    @Provides
    @Singleton
    fun okHttpClient(
        authInterceptor: AuthInterceptor,
        authenticator: TokenAuthenticator
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator(authenticator)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        // Generous read timeout: a stream request stays open for the whole track.
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json, settings: SettingsStore): Retrofit =
        Retrofit.Builder()
            .baseUrl(settings.baseUrlBlocking())
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun waveLinkApi(retrofit: Retrofit): WaveLinkApi = retrofit.create(WaveLinkApi::class.java)
}
