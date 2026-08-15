package ru.wavelink.app.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.core.prefs.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One row of the player's «Далее» list. [timelineIndex] is what a tap seeks to: an endless shuffle
 * replays tracks across cycles, so the same [id] can sit in the timeline several times over and
 * the position is the only thing that identifies the row the user actually pointed at.
 */
data class QueueEntry(
    val id: String,
    val timelineIndex: Int,
    val title: String,
    val artist: String,
    val durationSeconds: Int
)

data class PlayerUiState(
    val trackId: String? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    /** What the queue was started from — the player's «Сейчас играет · …» line. */
    val source: String = "",
    /** The first [UPCOMING_PUBLISH_CAP] items after the current one; the mock's «Далее». */
    val upcoming: List<QueueEntry> = emptyList(),
    /** How many items actually follow the current one — [upcoming] is only the visible head. */
    val upcomingTotal: Int = 0
)

/**
 * The queue is republished twice a second, and an endless shuffle keeps hundreds of items in the
 * timeline — so only the head is copied out. The count the UI shows comes from [PlayerUiState.upcomingTotal].
 */
private const val UPCOMING_PUBLISH_CAP = 100

/** How many played-out items stay behind the current one before the timeline is trimmed. */
private const val MAX_PLAYED_KEPT = 50

/**
 * The UI's handle on [PlaybackService]. Owns the MediaController and republishes the player's
 * state as a flow Compose can collect.
 */
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var connecting = false

    /** Media3 carries no notion of "where this queue came from", so the label lives here. */
    private var source: String = ""

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            controller = runCatching { future.get() }.getOrNull()?.also {
                it.addListener(listener)
                publish(it)
                startTicking()
            }
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    fun play(track: Track, queue: List<Track>, source: String = "") {
        val c = controller ?: run { connect(); null } ?: return
        this.source = source
        val baseUrl = settings.baseUrlBlocking()
        val items = queue.map { it.toMediaItem(baseUrl) }
        val startIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        c.setMediaItems(items, startIndex, 0L)
        c.prepare()
        c.play()
    }

    /** Appends the next shuffle page. Playback is untouched — the queue just gets longer. */
    fun addToQueue(tracks: List<Track>) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        val baseUrl = settings.baseUrlBlocking()
        val firstNew = c.mediaItemCount
        c.addMediaItems(tracks.map { it.toMediaItem(baseUrl) })
        // A page that arrived after the queue had already run dry has to restart playback itself.
        if (c.playbackState == Player.STATE_ENDED) {
            c.seekTo(firstNew, 0L)
            c.prepare()
            c.play()
        }
        trimPlayed(c)
        publish(c)
    }

    /** Jumps to the row the user tapped in the «Далее» list. */
    fun playQueueItem(timelineIndex: Int) {
        val c = controller ?: return
        if (timelineIndex !in 0 until c.mediaItemCount) return
        c.seekToDefaultPosition(timelineIndex)
        c.play()
    }

    /** An endless queue would otherwise carry the whole evening's history in the timeline. */
    private fun trimPlayed(player: Player) {
        val drop = player.currentMediaItemIndex - MAX_PLAYED_KEPT
        if (drop > 0) player.removeMediaItems(0, drop)
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    /** The player's ±15 s jumps, clamped so a skip near either end is not a no-op crash. */
    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val duration = c.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        c.seekTo((c.currentPosition + deltaMs).coerceIn(0L, duration))
    }

    /** «Очистить» drops what has not been played yet and keeps the current track running. */
    fun clearUpcoming() {
        val c = controller ?: return
        val from = c.currentMediaItemIndex + 1
        if (from < c.mediaItemCount) c.removeMediaItems(from, c.mediaItemCount)
        publish(c)
    }

    fun stop() {
        controller?.run { stop(); clearMediaItems() }
        source = ""
        _state.value = PlayerUiState()
    }

    private fun startTicking() = scope.launch {
        while (true) {
            controller?.let { if (it.isPlaying) publish(it) }
            delay(500)
        }
    }

    private fun publish(player: Player) {
        val item = player.currentMediaItem
        val current = player.currentMediaItemIndex
        val upcomingTotal = (player.mediaItemCount - current - 1).coerceAtLeast(0)
        val upcoming = buildList {
            val last = minOf(player.mediaItemCount, current + 1 + UPCOMING_PUBLISH_CAP)
            for (i in current + 1 until last) {
                val media = player.getMediaItemAt(i)
                add(
                    QueueEntry(
                        id = media.mediaId,
                        timelineIndex = i,
                        title = media.mediaMetadata.title?.toString().orEmpty(),
                        artist = media.mediaMetadata.artist?.toString().orEmpty(),
                        durationSeconds = ((media.mediaMetadata.durationMs ?: 0L) / 1000).toInt()
                    )
                )
            }
        }
        _state.value = PlayerUiState(
            trackId = item?.mediaId,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 } ?: 0,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            source = source,
            upcoming = upcoming,
            upcomingTotal = upcomingTotal
        )
    }
}

/**
 * `mediaId` carries the track id, which is also what keys the media cache — see
 * [PlaybackModule.cacheKeyForUri].
 */
fun Track.toMediaItem(baseUrl: String): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(baseUrl.trimEnd('/') + "/api/tracks/" + id + "/stream")
    .setCustomCacheKey(id)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setDurationMs(duration * 1000L)
            .build()
    )
    .build()
