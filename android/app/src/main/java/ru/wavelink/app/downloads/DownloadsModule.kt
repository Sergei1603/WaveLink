package ru.wavelink.app.downloads

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Requirements
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.wavelink.app.player.DownloadCache
import ru.wavelink.app.player.PlaybackModule
import ru.wavelink.app.player.UpstreamDataSource
import java.util.concurrent.Executors
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadsModule {

    /**
     * Downloads write into the pinned cache, which has no evictor — see [PlaybackModule]. It is
     * the only writer there, and [DownloadsRepository.reconcile] is what keeps that cache and
     * this manager's index from drifting apart.
     */
    @Provides
    @Singleton
    fun downloadManager(
        @ApplicationContext context: Context,
        @DownloadCache cache: SimpleCache,
        databaseProvider: DatabaseProvider,
        @UpstreamDataSource upstream: DataSource.Factory
    ): DownloadManager = DownloadManager(
        context,
        DefaultDownloadIndex(databaseProvider),
        DefaultDownloaderFactory(
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstream)
                .setCacheKeyFactory(PlaybackModule.trackCacheKey),
            Executors.newFixedThreadPool(2)
        )
    ).apply {
        maxParallelDownloads = 2
        requirements = Requirements(Requirements.NETWORK)
    }
}
