package ru.wavelink.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import ru.wavelink.app.MainActivity
import ru.wavelink.app.core.prefs.SettingsStore
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
    @Inject lateinit var settings: SettingsStore

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
        // Without a session activity `DefaultMediaNotificationProvider` leaves the notification's
        // content intent null, and the shade widget and lock-screen controls become untappable.
        // MainActivity is `singleTop`, so this returns to the running app rather than restarting it.
        session = MediaSession.Builder(this, exo)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
        // Must be set before the first notification is posted, i.e. before playback can start.
        setMediaNotificationProvider(WaveLinkNotificationProvider(this))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val exo = player
        // Nothing is playing and the user swiped the app away — don't linger as a foreground
        // service. With «Фоновое воспроизведение» off, stop even if something *is* playing:
        // that switch is precisely the promise that closing the app ends the sound.
        if (exo == null || !exo.playWhenReady || exo.mediaItemCount == 0 ||
            !settings.backgroundPlaybackBlocking()
        ) {
            exo?.stop()
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
