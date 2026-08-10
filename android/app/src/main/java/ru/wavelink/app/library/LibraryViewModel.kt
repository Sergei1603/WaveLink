package ru.wavelink.app.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.core.net.isOffline
import ru.wavelink.app.core.net.toUserMessage
import javax.inject.Inject

data class LibraryUiState(
    val refreshing: Boolean = false,
    val message: String? = null,
    val offline: Boolean = false,
    val query: String = ""
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: TrackRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    /** Always sourced from Room, so the screen works with no network at all. */
    val tracks: StateFlow<List<Track>> = repo.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { refresh() }

    fun setQuery(value: String) { _state.value = _state.value.copy(query = value) }

    fun refresh() {
        if (_state.value.refreshing) return
        _state.value = _state.value.copy(refreshing = true, message = null)
        viewModelScope.launch {
            runCatching { repo.refreshLibrary() }
                .onSuccess { _state.value = _state.value.copy(refreshing = false, offline = false) }
                .onFailure {
                    _state.value = _state.value.copy(
                        refreshing = false,
                        offline = it.isOffline(),
                        // Offline is a normal state here — the cached list is still on screen.
                        message = if (it.isOffline()) null else it.toUserMessage()
                    )
                }
        }
    }

    fun delete(track: Track) = mutate { repo.delete(track.id) }
    fun unsave(track: Track) = mutate { repo.unsave(track.id) }

    fun dismissMessage() { _state.value = _state.value.copy(message = null) }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { _state.value = _state.value.copy(message = it.toUserMessage()) }
        }
    }
}
