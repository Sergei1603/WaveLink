package ru.wavelink.app.downloads

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.wavelink.app.core.db.DownloadDao
import ru.wavelink.app.core.db.DownloadEntity
import ru.wavelink.app.core.prefs.SettingsStore
import ru.wavelink.app.player.DownloadCache
import ru.wavelink.app.player.StreamCache
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Listen offline": an explicit, pinned copy of a track, as opposed to the opportunistic stream
 * cache. They are separate [SimpleCache]s — see `PlaybackModule` for why sharing one is a trap —
 * and [reconcile] is what keeps this one honest.
 */
@UnstableApi
@Singleton
class DownloadsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: DownloadManager,
    @DownloadCache private val downloadCache: SimpleCache,
    @StreamCache private val streamCache: SimpleCache,
    private val dao: DownloadDao,
    private val settings: SettingsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reconciled = AtomicBoolean(false)

    init {
        scope.launch {
            // The Wi-Fi-only switch on the profile screen is this, and only this.
            settings.wifiOnlyDownloads.collect { wifiOnly ->
                manager.requirements = Requirements(
                    if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK
                )
            }
        }
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
        send(request)
    }

    fun remove(trackId: String) {
        DownloadService.sendRemoveDownload(
            context, WaveLinkDownloadService::class.java, trackId, /* foreground = */ false
        )
    }

    /**
     * Startup repair, in three parts, because three things can drift apart while the app is dead:
     *
     * 1. **Room ← the download index.** The mirror this screen renders is fed by a listener that
     *    only exists while the process does; the index is the truth.
     * 2. **The pinned cache is pruned to what a download still claims.** Nothing evicts from it,
     *    so anything unclaimed — a download removed while the app was gone, or bytes streamed
     *    into it back when downloads and streaming shared one cache — would sit there forever.
     * 3. **A download whose bytes are missing is re-queued.** `DownloadManager` never verifies
     *    the cache, so a truncated or externally cleared download stays `STATE_COMPLETED`
     *    while offline playback of it fails. Better to say «в очереди» and fetch it again.
     *
     * Runs once per process — the activity calls it, and a rotation must not re-queue anything.
     */
    suspend fun reconcile() = withContext(Dispatchers.IO) {
        if (!reconciled.compareAndSet(false, true)) return@withContext
        val known = runCatching { indexedDownloads() }.getOrNull() ?: return@withContext

        val broken = known.filter { it.state == Download.STATE_COMPLETED && !isIntact(it) }
        val brokenIds = broken.map { it.request.id }.toSet()
        // The row is demoted here rather than left to the manager's listener: offline the manager
        // cannot start anything, and «закреплено» on a track with no bytes is the exact lie this
        // whole routine exists to stop telling.
        dao.replaceAll(
            known.map { download ->
                val row = download.toEntity()
                if (row.trackId in brokenIds) {
                    row.copy(state = Download.STATE_QUEUED, bytesDownloaded = 0, percent = 0f)
                } else {
                    row
                }
            }
        )

        val claimed = known.map { it.cacheKey }.toSet()
        downloadCache.keys.toList().forEach { key ->
            if (key !in claimed) runCatching { downloadCache.removeResource(key) }
        }

        // Re-adding an existing request re-queues it; whatever is still cached is kept and only
        // the missing part is fetched. The index still says COMPLETED, so a re-add that cannot
        // be delivered today is simply found again on the next start.
        broken.forEach { send(it.request) }
    }

    /** Bytes the downloads occupy. Not subject to the size limit — nothing evicts them. */
    fun pinnedBytes(): Long = downloadCache.cacheSpace

    /** Bytes cached opportunistically while streaming. This is what the size limit caps. */
    fun streamCacheBytes(): Long = streamCache.cacheSpace

    /** Drops everything that was cached while streaming; downloads live in the other cache. */
    suspend fun clearStreamCache() = withContext(Dispatchers.IO) {
        streamCache.keys.toList().forEach { key -> runCatching { streamCache.removeResource(key) } }
    }

    /**
     * `startService` from a backgrounded process is refused on Android 8+, and [reconcile] can
     * land there. A download that fails to enqueue is retried on the next start rather than
     * taking the app down with it.
     */
    private fun send(request: DownloadRequest) {
        runCatching {
            DownloadService.sendAddDownload(
                context, WaveLinkDownloadService::class.java, request, /* foreground = */ false
            )
        }
    }

    private fun indexedDownloads(): List<Download> =
        manager.downloadIndex.getDownloads().use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.download) }
        }

    /** Is every byte this download claims still in the pinned cache? */
    private fun isIntact(download: Download): Boolean {
        val key = download.cacheKey
        val unset = C.LENGTH_UNSET.toLong()
        val cached = ContentMetadata.getContentLength(downloadCache.getContentMetadata(key))
        // No recorded length means the cache has no idea this resource exists — treat the
        // index's own figure as the claim to verify, and a missing one as "cannot be intact".
        val length = if (cached != unset) cached else download.contentLength
        return length != unset && downloadCache.isCached(key, 0, length)
    }

    private val Download.cacheKey: String get() = request.customCacheKey ?: request.id

    private fun Download.toEntity() = DownloadEntity(
        trackId = request.id,
        state = state,
        bytesDownloaded = bytesDownloaded,
        percent = percentDownloaded.takeIf { it >= 0f } ?: 0f,
        requestedAtEpoch = startTimeMs
    )
}
