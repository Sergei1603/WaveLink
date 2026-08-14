package ru.wavelink.app.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.wavelink.app.collections.CollectionRepository
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.core.net.isOffline
import ru.wavelink.app.core.net.toUserMessage
import javax.inject.Inject

/** The three orders the design offers, in the order it lists them. */
enum class LibrarySort(val label: String) {
    Recent("Недавние"),
    Artist("Исполнитель"),
    Title("Название")
}

data class LibraryUiState(
    val refreshing: Boolean = false,
    val message: String? = null,
    val offline: Boolean = false,
    val sort: LibrarySort = LibrarySort.Recent
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: TrackRepository,
    collections: CollectionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    /**
     * Always sourced from Room, so the screen works with no network at all. Sorting happens here
     * rather than through `?sort=` for the same reason — an offline library must still reorder.
     */
    val tracks: StateFlow<List<Track>> =
        combine(repo.observeLibrary(), _state) { list, state -> list.sortedBy(state.sort) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val collectionCount: StateFlow<Int> = collections.observeAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init { refresh() }

    fun setSort(sort: LibrarySort) { _state.value = _state.value.copy(sort = sort) }

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

/** Room already hands the list back newest-first, so `Recent` is the identity ordering. */
fun List<Track>.sortedBy(sort: LibrarySort): List<Track> = when (sort) {
    LibrarySort.Recent -> this
    LibrarySort.Artist -> sortedWith(
        compareBy(String.CASE_INSENSITIVE_ORDER) { t: Track -> t.artist }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { t: Track -> t.title }
    )
    LibrarySort.Title -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { t: Track -> t.title })
}
