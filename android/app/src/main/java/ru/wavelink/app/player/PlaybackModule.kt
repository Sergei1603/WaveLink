package ru.wavelink.app.player

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
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

/**
 * The pinned half of on-disk audio: what «Скачать» put there. Only the `DownloadManager` writes
 * into it and nothing evicts from it, so a downloaded track is still playable in a tunnel.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

/** The opportunistic half: whatever was streamed, reclaimed LRU under the user's size limit. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StreamCache

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    /**
     * Downloads and streaming are **two** caches, not one.
     *
     * Media3 has no notion of a pinned span: `CacheSpan` carries no such flag and
     * `LeastRecentlyUsedCacheEvictor` reclaims strictly by last-touch time, so a shared cache
     * lets a listening session quietly eat the tracks the user saved for the plane — and nothing
     * notices, because `DownloadManager` keeps reporting `STATE_COMPLETED` for bytes that are
     * gone. Online it merely re-streams; offline the track is dead. Hence: `NoOpCacheEvictor`
     * for downloads, LRU for the stream cache, and playback reads through both.
     */
    private const val DOWNLOAD_CACHE_DIR = "media"
    private const val STREAM_CACHE_DIR = "media-stream"

    /**
     * One provider for both caches and for the download index. They share the SQLite file but not
     * a table: `CachedContentIndex` suffixes its table with the cache's own uid.
     */
    @Provides
    @Singleton
    fun databaseProvider(@ApplicationContext context: Context): DatabaseProvider =
        StandaloneDatabaseProvider(context)

    /**
     * Exactly one SimpleCache instance may exist per directory per process — a second one throws
     * at runtime. It is a singleton for that reason; never construct one from a screen.
     *
     * It keeps the historic directory name: that is where the tracks users have already
     * downloaded live, and renaming it would orphan every one of them.
     */
    @Provides
    @Singleton
    @DownloadCache
    fun downloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider
    ): SimpleCache = SimpleCache(
        File(context.filesDir, DOWNLOAD_CACHE_DIR),
        NoOpCacheEvictor(),
        databaseProvider
    )

    /**
     * The size limit the profile screen offers applies here and only here. It is read once, at
     * construction: `SimpleCache` takes its evictor at build time, so a new limit takes effect on
     * the next app start.
     */
    @Provides
    @Singleton
    @StreamCache
    fun streamCache(
        @ApplicationContext context: Context,
        settings: SettingsStore,
        databaseProvider: DatabaseProvider
    ): SimpleCache = SimpleCache(
        File(context.filesDir, STREAM_CACHE_DIR),
        LeastRecentlyUsedCacheEvictor(settings.cacheBytesBlocking()),
        databaseProvider
    )

    /**
     * Streaming reuses the Retrofit OkHttp client, so the bearer token and the
     * 401 → refresh → retry flow apply to audio requests for free.
     */
    @Provides
    @Singleton
    @UpstreamDataSource
    fun upstreamDataSourceFactory(client: OkHttpClient): DataSource.Factory =
        OkHttpDataSource.Factory(client)

    /**
     * Playback reads through both caches: downloads first, then the stream cache, then the
     * network. The download layer is **read-only** — pass `null` as its write sink — or streaming
     * would fill a cache that has no evictor and would grow without bound.
     */
    @Provides
    @Singleton
    fun cacheDataSourceFactory(
        @DownloadCache downloadCache: SimpleCache,
        @StreamCache streamCache: SimpleCache,
        @UpstreamDataSource upstream: DataSource.Factory
    ): CacheDataSource.Factory {
        val streaming = CacheDataSource.Factory()
            .setCache(streamCache)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory(trackCacheKey)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(streaming)
            .setCacheKeyFactory(trackCacheKey)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Key by track id, not URI: switching the base URL (dev ↔ prod) must not orphan the cache.
     * Both caches and the downloader use this one instance, because a key that differs between
     * writing and reading is a cache that silently never hits.
     */
    val trackCacheKey = CacheKeyFactory { spec -> spec.key ?: cacheKeyForUri(spec.uri.toString()) }

    /** `.../api/tracks/{id}/stream` → `{id}`. */
    fun cacheKeyForUri(uri: String): String =
        Regex("/api/tracks/([^/]+)/stream").find(uri)?.groupValues?.get(1) ?: uri
}
