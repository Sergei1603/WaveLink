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

    init {
        connection.connect()
        syncScheduler.schedulePeriodic()
        viewModelScope.launch { telegram.refresh() }
    }

    fun play(track: Track, queue: List<Track>, source: String = "") =
        connection.play(track, queue, source)

    fun togglePlayPause() = connection.togglePlayPause()
    fun next() = connection.next()
    fun previous() = connection.previous()
    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)
    fun seekBy(deltaMs: Long) = connection.seekBy(deltaMs)
    fun clearUpcoming() = connection.clearUpcoming()
    fun stop() = connection.stop()
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
     * Starts a shuffled queue. Falls back to the local library when the server is unreachable so
     * that discover-shuffle keeps working offline, using the same weight formula.
     */
    fun shuffle(mode: String, collectionId: String? = null) = viewModelScope.launch {
        val queue = runCatching { tracks.shuffle(mode, collectionId = collectionId) }
            .getOrElse { failure ->
                val local = runCatching {
                    if (collectionId != null) tracks.collectionTracksOnce(collectionId)
                    else tracks.libraryOnce()
                }.getOrNull()
                if (local.isNullOrEmpty()) {
                    _error.value = failure.toUserMessage()
                    return@launch
                }
                LocalShuffle.order(local, mode)
            }
        if (queue.isEmpty()) {
            _error.value = "Нечего перемешивать"
            return@launch
        }
        val label = if (mode == "discover") "Умное перемешивание" else "Перемешивание"
        connection.play(queue.first(), queue, label)
    }
}
