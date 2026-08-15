package ru.wavelink.app.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.core.net.toUserMessage
import ru.wavelink.app.library.TrackRepository
import ru.wavelink.app.playtracking.PlaySyncScheduler
import ru.wavelink.app.telegram.TelegramRepository
import javax.inject.Inject
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val connection: PlayerConnection,
    private val tracks: TrackRepository,
    private val telegram: TelegramRepository,
    private val syncScheduler: PlaySyncScheduler
) : ViewModel() {

    val state: StateFlow<PlayerUiState> = connection.state

    /**
     * The player's badges — Public, play count, file size, whether it is downloaded — come from
     * the cached row rather than from the media item, which only carries title and artist.
     */
    val currentTrack: StateFlow<Track?> = connection.state
        .map { it.trackId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else tracks.observeTrack(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val telegramLinked: StateFlow<Boolean> = telegram.status
        .map { it?.botEnabled == true && it.linked }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var feed: ShuffleFeed? = null
    private var refilling = false
    /** Ids of the last tracks put into the queue — what [dropRecentlyPlayed] compares against. */
    private val recentlyQueued = ArrayDeque<String>()

    init {
        connection.connect()
        syncScheduler.schedulePeriodic()
        viewModelScope.launch { telegram.refresh() }

        // The queue is topped up from playback progress, not from scrolling: whenever it gets
        // short, the next page is on its way long before the last track ends.
        viewModelScope.launch {
            connection.state
                .map { it.upcomingTotal }
                .distinctUntilChanged()
                .collect { remaining -> if (remaining <= PREFETCH_AHEAD) refill() }
        }
    }

    /** A queue handed in from a list is finite by nature and replaces the shuffle feed. */
    fun play(track: Track, queue: List<Track>, source: String = "") {
        feed = null
        recentlyQueued.clear()
        connection.play(track, queue, source)
    }

    fun togglePlayPause() = connection.togglePlayPause()
    fun next() = connection.next()
    fun previous() = connection.previous()
    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)
    fun seekBy(deltaMs: Long) = connection.seekBy(deltaMs)
    fun playQueueItem(entry: QueueEntry) = connection.playQueueItem(entry.timelineIndex)

    /** «Очистить» has to kill the feed too, or the queue refills the moment it is emptied. */
    fun clearUpcoming() {
        feed = null
        connection.clearUpcoming()
    }

    fun stop() {
        feed = null
        connection.stop()
    }
    fun dismissError() { _error.value = null }
    fun dismissNotice() { _notice.value = null }

    fun sendCurrentToTelegram() {
        val id = state.value.trackId ?: return
        viewModelScope.launch {
            runCatching { telegram.send(id) }
                .onSuccess { _notice.value = "Отправлено в Telegram" }
                .onFailure { _error.value = it.toUserMessage() }
        }
    }

    /**
     * Starts an endless shuffled queue. Falls back to the local library when the server is
     * unreachable so that discover-shuffle keeps working offline, using the same weight formula.
     * From here on [refill] keeps the queue topped up — see [ShuffleFeed].
     */
    fun shuffle(mode: String, collectionId: String? = null) = viewModelScope.launch {
        feed = null
        recentlyQueued.clear()

        val batch = runCatching { serverBatch(mode, collectionId, seed = null, cursor = 0) }
            .getOrElse { failure ->
                val local = cachedTracks(collectionId)
                if (local.isNullOrEmpty()) {
                    _error.value = failure.toUserMessage()
                    return@launch
                }
                localBatch(local, mode, seed = null, cursor = 0)
            }

        if (batch.tracks.isEmpty()) {
            _error.value = "Нечего перемешивать"
            return@launch
        }

        feed = ShuffleFeed(mode, collectionId, batch.seed, batch.nextCursor, batch.hasMore, batch.local)
        remember(batch.tracks)
        val label = if (mode == "discover") "Умное перемешивание" else "Перемешивание"
        connection.play(batch.tracks.first(), batch.tracks, label)
    }

    /**
     * Pulls the next page once the queue runs short. A spent cycle rolls into a fresh one, which
     * is what makes the queue endless; in discover mode that new cycle is already reweighted by
     * whatever was played in the old one.
     */
    private suspend fun refill() {
        val current = feed ?: return
        if (refilling) return
        refilling = true
        try {
            val rollover = !current.hasMore
            val seed = if (rollover) null else current.seed
            val cursor = if (rollover) 0 else current.cursor

            val batch = if (current.local != null) {
                localBatch(current.local, current.mode, seed, cursor)
            } else {
                runCatching { serverBatch(current.mode, current.collectionId, seed, cursor) }
                    .getOrElse {
                        // The network went away mid-queue: the rest of the session plays offline.
                        val local = cachedTracks(current.collectionId) ?: return
                        if (local.isEmpty()) return
                        localBatch(local, current.mode, seed = null, cursor = 0)
                    }
            }

            if (feed !== current) return          // a new shuffle started while this was in flight
            if (batch.total == 0) { feed = null; return }

            feed = current.copy(
                seed = batch.seed,
                cursor = batch.nextCursor,
                hasMore = batch.hasMore,
                local = batch.local ?: current.local
            )
            if (batch.tracks.isEmpty()) return

            val fresh = dropRecentlyPlayed(batch.tracks, batch.total)
            remember(fresh)
            connection.addToQueue(fresh)
        } finally {
            refilling = false
        }
    }

    private suspend fun serverBatch(
        mode: String, collectionId: String?, seed: Int?, cursor: Int
    ): ShuffleBatch {
        val page = tracks.shuffle(
            mode = mode, limit = PAGE_SIZE, collectionId = collectionId, seed = seed, cursor = cursor
        )
        return ShuffleBatch(page.tracks, page.seed, page.nextCursor, page.hasMore, page.total, local = null)
    }

    /** The offline stand-in for a server page: same cycle-and-cursor shape, drawn from Room. */
    private fun localBatch(local: List<Track>, mode: String, seed: Int?, cursor: Int): ShuffleBatch {
        val cycleSeed = seed ?: Random.nextInt()
        val page = LocalShuffle.page(local, mode, cycleSeed, cursor, PAGE_SIZE)
        val nextCursor = (cursor + page.size).coerceAtMost(local.size)
        return ShuffleBatch(page, cycleSeed, nextCursor, nextCursor < local.size, local.size, local)
    }

    private suspend fun cachedTracks(collectionId: String?): List<Track>? = runCatching {
        if (collectionId != null) tracks.collectionTracksOnce(collectionId) else tracks.libraryOnce()
    }.getOrNull()

    /**
     * Repeats across cycles are the point of an endless queue, so only the tail is deduped — and
     * the window never exceeds the cycle minus one, or a short library would lose every new cycle.
     */
    private fun dropRecentlyPlayed(items: List<Track>, cycleTotal: Int): List<Track> {
        val window = minOf(DEDUPE_TAIL, (cycleTotal - 1).coerceAtLeast(0))
        val tail = recentlyQueued.toList().takeLast(window).toSet()
        val fresh = items.filterNot { it.id in tail }
        return fresh.ifEmpty { items }
    }

    private fun remember(queued: List<Track>) {
        queued.forEach { recentlyQueued.addLast(it.id) }
        while (recentlyQueued.size > DEDUPE_TAIL) recentlyQueued.removeFirst()
    }

    /** Where the queue refills from. Null means a finite queue — a list the user tapped into. */
    private data class ShuffleFeed(
        val mode: String,
        val collectionId: String?,
        val seed: Int,
        val cursor: Int,
        val hasMore: Boolean,
        /** Non-null once the feed fell back to the cache; the rest of the session stays offline. */
        val local: List<Track>?
    )

    private data class ShuffleBatch(
        val tracks: List<Track>,
        val seed: Int,
        val nextCursor: Int,
        val hasMore: Boolean,
        val total: Int,
        val local: List<Track>?
    )

    private companion object {
        const val PAGE_SIZE = 50
        /** Fetch the next page once this few tracks remain ahead of the current one. */
        const val PREFETCH_AHEAD = 15
        const val DEDUPE_TAIL = 50
    }
}
