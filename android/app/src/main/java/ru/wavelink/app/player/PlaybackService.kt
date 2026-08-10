package ru.wavelink.app.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import ru.wavelink.app.playtracking.PlaybackProgressTracker
import javax.inject.Inject

/**
 * Owns the one ExoPlayer instance and its media session, so playback survives the UI going away
 * and shows up as a system media notification.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var cacheDataSourceFactory: CacheDataSource.Factory
    @Inject lateinit var tracker: PlaybackProgressTracker

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        tracker.attach(exo)
        player = exo
        session = MediaSession.Builder(this, exo).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Nothing is playing and the user swiped the app away — don't linger as a foreground service.
        val exo = player
        if (exo == null || !exo.playWhenReady || exo.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Flush whatever was heard before the process goes away.
        tracker.finishCurrent()
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }
}
