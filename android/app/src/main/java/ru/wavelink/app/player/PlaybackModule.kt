package ru.wavelink.app.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import ru.wavelink.app.core.prefs.SettingsStore
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/** The raw HTTP source, without the cache layer — what downloads and cache misses go through. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpstreamDataSource

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    /**
     * Exactly one SimpleCache instance may exist per directory per process — a second one throws
     * at runtime. It is a singleton for that reason; never construct one from a screen.
     */
    @Provides
    @Singleton
    fun simpleCache(
        @ApplicationContext context: Context,
        settings: SettingsStore
    ): SimpleCache {
        val dir = File(context.filesDir, "media")
        return SimpleCache(
            dir,
            LeastRecentlyUsedCacheEvictor(settings.cacheBytesBlocking()),
            StandaloneDatabaseProvider(context)
        )
    }

    /**
     * Streaming reuses the Retrofit OkHttp client, so the bearer token and the
     * 401 → refresh → retry flow apply to audio requests for free.
     */
    @Provides
    @Singleton
    @UpstreamDataSource
    fun upstreamDataSourceFactory(client: OkHttpClient): DataSource.Factory =
        OkHttpDataSource.Factory(client)

    @Provides
    @Singleton
    fun cacheDataSourceFactory(
        cache: SimpleCache,
        @UpstreamDataSource upstream: DataSource.Factory
    ): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        // Key by track id, not URI: switching the base URL (dev ↔ prod) must not orphan the cache.
        .setCacheKeyFactory { spec -> spec.key ?: cacheKeyForUri(spec.uri.toString()) }
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** `.../api/tracks/{id}/stream` → `{id}`. */
    fun cacheKeyForUri(uri: String): String =
        Regex("/api/tracks/([^/]+)/stream").find(uri)?.groupValues?.get(1) ?: uri
}
