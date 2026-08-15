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
    /** How many rows the query matches on the server — [results] is only what has been fetched. */
    val total: Int = 0,
    val page: Int = 1,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val message: String? = null
) {
    val hasMore: Boolean get() = results.size < total
}

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

    /**
     * Every new query or sort starts a generation; an answer from an older one is dropped, or a
     * slow first page could land on top of a newer search — or be appended to by «Показать ещё».
     */
    private var generation = 0

    /**
     * The query [PublicBankUiState.results] actually came from. Not the same as the state's
     * `query`, which follows the keyboard 300 ms ahead of the request — paging has to ask for the
     * next page of the list on screen, not of whatever is half-typed in the field.
     */
    private var loadedQuery = ""
    private var loadedSort = LibrarySort.Recent

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

    /**
     * The next page of the current query. Saving a track does *not* reload the list — the badge
     * comes from the Room mirror — so pages the user has already asked for stay where they are.
     */
    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasMore) return
        val gen = generation
        val next = current.page + 1
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true, message = null)
            runCatching { repo.searchPublic(loadedQuery, loadedSort.apiValue, page = next) }
                .onSuccess { page ->
                    if (gen != generation) return@onSuccess
                    val shown = _state.value.results
                    // Uploads and saves shift rows between pages, so a page can overlap the one
                    // before it — the same reason the web client dedupes on append.
                    val seen = shown.mapTo(mutableSetOf()) { it.id }
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        page = next,
                        total = page.total,
                        results = shown + page.tracks.filterNot { it.id in seen }
                    )
                }
                .onFailure {
                    if (gen != generation) return@onFailure
                    _state.value = _state.value.copy(loadingMore = false, message = it.toUserMessage())
                }
        }
    }

    fun save(track: Track) = viewModelScope.launch {
        runCatching { repo.save(track.id) }
            .onSuccess { _state.value = _state.value.copy(message = "«${track.title}» в библиотеке") }
            .onFailure { _state.value = _state.value.copy(message = it.toUserMessage()) }
    }

    fun dismissMessage() { _state.value = _state.value.copy(message = null) }

    private suspend fun load(query: String, sort: LibrarySort) {
        val gen = ++generation
        _state.value = _state.value.copy(loading = true, loadingMore = false, message = null)
        runCatching { repo.searchPublic(query, sort.apiValue, page = 1) }
            .onSuccess { page ->
                if (gen != generation) return@onSuccess
                loadedQuery = query
                loadedSort = sort
                _state.value = _state.value.copy(
                    loading = false,
                    page = 1,
                    results = page.tracks,
                    total = page.total
                )
            }
            .onFailure {
                if (gen != generation) return@onFailure
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
