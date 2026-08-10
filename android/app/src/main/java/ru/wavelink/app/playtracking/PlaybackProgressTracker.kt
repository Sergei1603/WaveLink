package ru.wavelink.app.playtracking

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.wavelink.app.core.PlayStatsRules
import ru.wavelink.app.core.db.PendingPlayDao
import ru.wavelink.app.core.db.PendingPlayEntity
import ru.wavelink.app.core.db.TrackCounterDao
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Measures how much of each track was actually heard and queues one event per finished session.
 *
 * Why interval coverage rather than a timer: a wall-clock timer would credit paused time and
 * buffering stalls, while a raw `currentPosition` delta would credit the jump a seek produces.
 * Accumulating only small forward steps, merged, gives exactly the portion of the timeline whose
 * audio was rendered.
 */
@Singleton
class PlaybackProgressTracker @Inject constructor(
    private val pendingPlays: PendingPlayDao,
    private val counters: TrackCounterDao,
    private val syncScheduler: PlaySyncScheduler
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private var player: Player? = null
    private var coverage = CoverageSet()
    private var lastPositionMs = 0L
    private var startedAtEpoch = 0L
    private var currentTrackId: String? = null
    private var currentDuration = 0

    private val ticker = object : Runnable {
        override fun run() {
            val p = player ?: return
            tick(p.currentPosition)
            handler.postDelayed(this, TICK_MS)
        }
    }

    fun attach(target: Player) {
        player = target
        target.addListener(object : Player.Listener {

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    // Resync only: a seek must credit nothing.
                    lastPositionMs = newPosition.positionMs
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                finishCurrent()
                begin(mediaItem)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) finishCurrent()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    if (currentTrackId == null) begin(target.currentMediaItem)
                    lastPositionMs = target.currentPosition
                    handler.removeCallbacks(ticker)
                    handler.post(ticker)
                } else {
                    handler.removeCallbacks(ticker)
                    tick(target.currentPosition)
                }
            }
        })
    }

    private fun begin(item: MediaItem?) {
        coverage = CoverageSet()
        lastPositionMs = 0L
        startedAtEpoch = System.currentTimeMillis()
        currentTrackId = item?.mediaId
        currentDuration = (item?.mediaMetadata?.durationMs ?: 0L).let { (it / 1000L).toInt() }
    }

    private fun tick(positionMs: Long) {
        val delta = positionMs - lastPositionMs
        // Only normal forward progress counts; anything larger is a jump we must not credit.
        if (delta in 1..MAX_STEP_MS) coverage.add(lastPositionMs, positionMs)
        lastPositionMs = positionMs
    }

    /**
     * Reports the session in progress, if any. Safe to call more than once — the track id is
     * cleared, so a second call is a no-op until the next track begins.
     *
     * There is deliberately no mid-session checkpoint: emitting the running total more than once
     * would report the same seconds again under a fresh idempotency key and inflate the counts.
     * Losing an in-flight session to process death is the cheaper failure, and Media3 keeps the
     * playback service in the foreground precisely so that rarely happens.
     */
    fun finishCurrent() {
        val trackId = currentTrackId ?: return
        val listened = coverage.coveredSeconds
        currentTrackId = null

        if (listened < PlayStatsRules.MIN_REPORTED_SECONDS) return

        val duration = currentDuration
        val startedAt = startedAtEpoch
        val significant = PlayStatsRules.isSignificant(listened, duration)
        val eventId = UUID.randomUUID().toString()
        coverage = CoverageSet()

        scope.launch {
            pendingPlays.add(
                PendingPlayEntity(
                    clientEventId = eventId,
                    trackId = trackId,
                    startedAtEpoch = startedAt,
                    listenedSeconds = listened,
                    trackDuration = duration.takeIf { it > 0 },
                    countedLocally = significant,
                    attempts = 0
                )
            )
            // Local delta so discover-shuffle reacts immediately, even with no network.
            if (significant) counters.increment(trackId, System.currentTimeMillis())
            syncScheduler.scheduleNow()
        }
    }

    private companion object {
        const val TICK_MS = 1_000L
        /** A step larger than this is a jump, not playback. */
        const val MAX_STEP_MS = 1_500L
    }
}
