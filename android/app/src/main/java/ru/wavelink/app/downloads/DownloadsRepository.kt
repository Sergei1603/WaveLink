package ru.wavelink.app.downloads

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ru.wavelink.app.core.db.DownloadDao
import ru.wavelink.app.core.db.DownloadEntity
import ru.wavelink.app.core.prefs.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Listen offline": an explicit, pinned copy of a track, as opposed to the opportunistic stream
 * cache. Both live in one [androidx.media3.datasource.cache.SimpleCache]; only the streamed part
 * is evictable.
 */
@UnstableApi
@Singleton
class DownloadsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: DownloadManager,
    private val dao: DownloadDao,
    private val settings: SettingsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        manager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                scope.launch { dao.upsert(download.toEntity()) }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                scope.launch { dao.deleteByTrack(download.request.id) }
            }
        })
    }

    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()

    fun download(trackId: String) {
        val baseUrl = settings.baseUrlBlocking().trimEnd('/')
        val request = DownloadRequest.Builder(
            /* id = */ trackId,
            android.net.Uri.parse("$baseUrl/api/tracks/$trackId/stream")
        )
            // Same key as playback uses, so a downloaded track satisfies the player directly.
            .setCustomCacheKey(trackId)
            .build()
        DownloadService.sendAddDownload(
            context, WaveLinkDownloadService::class.java, request, /* foreground = */ false
        )
    }

    fun remove(trackId: String) {
        DownloadService.sendRemoveDownload(
            context, WaveLinkDownloadService::class.java, trackId, /* foreground = */ false
        )
    }

    private fun Download.toEntity() = DownloadEntity(
        trackId = request.id,
        state = state,
        bytesDownloaded = bytesDownloaded,
        percent = percentDownloaded.takeIf { it >= 0f } ?: 0f,
        requestedAtEpoch = startTimeMs
    )
}
