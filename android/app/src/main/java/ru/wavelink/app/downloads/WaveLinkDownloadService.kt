package ru.wavelink.app.downloads

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.R as Media3R
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground host for Media3's [DownloadManager]. The manager itself is a singleton in the Hilt
 * graph, shared with the playback stack so downloads and streaming use one cache.
 */
@UnstableApi
@AndroidEntryPoint
class WaveLinkDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    Media3R.string.exo_download_notification_channel_name,
    /* channelDescriptionResourceId = */ 0
) {
    @Inject lateinit var manager: DownloadManager

    private val notifications by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager = manager

    /** No scheduler: WorkManager already owns background retries for play events, and a
     *  download that gets interrupted resumes the next time the user opens the app. */
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification = notifications.buildProgressNotification(
        this,
        android.R.drawable.stat_sys_download,
        /* contentIntent = */ null,
        /* message = */ null,
        downloads,
        notMetRequirements
    )

    private companion object {
        const val NOTIFICATION_ID = 4711
        const val CHANNEL_ID = "wavelink_downloads"
    }
}
