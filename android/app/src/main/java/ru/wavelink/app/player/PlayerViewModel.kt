package ru.wavelink.app.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.core.net.toUserMessage
import ru.wavelink.app.library.TrackRepository
import ru.wavelink.app.playtracking.PlaySyncScheduler
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val connection: PlayerConnection,
    private val tracks: TrackRepository,
    private val syncScheduler: PlaySyncScheduler
) : ViewModel() {

    val state: StateFlow<PlayerUiState> = connection.state

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        connection.connect()
        syncScheduler.schedulePeriodic()
    }

    fun play(track: Track, queue: List<Track>) = connection.play(track, queue)
    fun togglePlayPause() = connection.togglePlayPause()
    fun next() = connection.next()
    fun previous() = connection.previous()
    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)
    fun stop() = connection.stop()
    fun dismissError() { _error.value = null }

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
        connection.play(queue.first(), queue)
    }
}
