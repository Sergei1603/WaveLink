package ru.wavelink.app.publicbank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.core.net.toUserMessage
import ru.wavelink.app.library.LibrarySort
import ru.wavelink.app.library.TrackRepository
import javax.inject.Inject

data class PublicBankUiState(
    val query: String = "",
    val sort: LibrarySort = LibrarySort.Recent,
    val results: List<Track> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class PublicBankViewModel @Inject constructor(
    private val repo: TrackRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PublicBankUiState())
    val state: StateFlow<PublicBankUiState> = _state.asStateFlow()

    /**
     * `TrackResponse` carries no "already in my library" flag, so — like the web client — the
     * answer is computed client-side. Here it comes from the Room mirror rather than a second
     * request, which also keeps the badge right while offline.
     */
    val libraryIds: StateFlow<Set<String>> = repo.observeLibrary()
        .map { list -> list.mapTo(mutableSetOf()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val queries = MutableStateFlow("")

    init {
        viewModelScope.launch {
            // Debounced to match the web client, which waits 300 ms before hitting the API.
            queries.debounce(300).distinctUntilChanged().collect { load(it, _state.value.sort) }
        }
    }

    fun search(value: String) {
        _state.value = _state.value.copy(query = value)
        queries.value = value
    }

    /** Unlike the library, the bank is a server-side query, so the order is one too. */
    fun setSort(sort: LibrarySort) {
        if (_state.value.sort == sort) return
        _state.value = _state.value.copy(sort = sort)
        viewModelScope.launch { load(_state.value.query, sort) }
    }

    fun save(track: Track) = viewModelScope.launch {
        runCatching { repo.save(track.id) }
            .onSuccess {
                _state.value = _state.value.copy(message = "«${track.title}» в библиотеке")
                load(_state.value.query, _state.value.sort)
            }
            .onFailure { _state.value = _state.value.copy(message = it.toUserMessage()) }
    }

    fun dismissMessage() { _state.value = _state.value.copy(message = null) }

    private suspend fun load(query: String, sort: LibrarySort) {
        _state.value = _state.value.copy(loading = true, message = null)
        runCatching { repo.searchPublic(query, sort.apiValue) }
            .onSuccess { _state.value = _state.value.copy(loading = false, results = it) }
            .onFailure {
                _state.value = _state.value.copy(loading = false, message = it.toUserMessage())
            }
    }
}

/** `TracksController.ParseSort` on the server understands exactly these three. */
val LibrarySort.apiValue: String
    get() = when (this) {
        LibrarySort.Recent -> "recent"
        LibrarySort.Artist -> "artist"
        LibrarySort.Title -> "title"
    }
