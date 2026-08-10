package ru.wavelink.app.downloads

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Requirements
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.wavelink.app.player.UpstreamDataSource
import java.util.concurrent.Executors
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadsModule {

    /**
     * Downloads write into the *same* [SimpleCache] the stream cache uses. Media3 marks
     * downloaded spans so the LRU evictor cannot reclaim them, which is exactly the
     * "cache is evictable, downloads are pinned" split the settings screen describes.
     */
    @Provides
    @Singleton
    fun downloadManager(
        @ApplicationContext context: Context,
        cache: SimpleCache,
        @UpstreamDataSource upstream: DataSource.Factory
    ): DownloadManager {
        val databaseProvider = StandaloneDatabaseProvider(context)
        return DownloadManager(
            context,
            DefaultDownloadIndex(databaseProvider),
            androidx.media3.exoplayer.offline.DefaultDownloaderFactory(
                androidx.media3.datasource.cache.CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(upstream),
                Executors.newFixedThreadPool(2)
            )
        ).apply {
            maxParallelDownloads = 2
            requirements = Requirements(Requirements.NETWORK)
        }
    }
}
