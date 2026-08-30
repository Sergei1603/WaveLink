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
import ru.wavelink.app.telegram.TelegramRepository
import javax.inject.Inject

/**
 * The orders the design offers. «Исполнитель» is gone from the library — that job belongs to the
 * Артисты view now — but the enum keeps it, because the public bank still offers it as a
 * *server-side* order (`?sort=artist`) and enumerates this same enum.
 */
enum class LibrarySort(val label: String) {
    Recent("Недавние"),
    Artist("Исполнитель"),
    Title("Название");

    companion object {
        /** What the library sort strip shows. The bank keeps [entries]. */
        val libraryOptions = listOf(Recent, Title)
    }
}

/** A flat list of tracks, or folders of artists — two readings of one library. */
enum class LibraryView(val label: String) { Tracks("Треки"), Artists("Артисты") }

/**
 * One artist and their tracks. Grouping is case-insensitive, so «Кассета» and «кассета» are one
 * folder; [name] is the spelling the first track uses.
 */
data class ArtistFolder(val name: String, val tracks: List<Track>) {
    /** The route key and the selection key — the lowercased artist, not the display spelling. */
    val key: String get() = name.trim().lowercase()
    val trackCount: Int get() = tracks.size
    val totalDuration: Long get() = tracks.sumOf { it.duration.toLong() }
    val ownedCount: Int get() = tracks.count { it.isOwned }
}

data class LibraryUiState(
    val refreshing: Boolean = false,
    val message: String? = null,
    /** Bulk actions report success through the same slot, and «Удалено: 3» is not an error. */
    val messageError: Boolean = true,
    val offline: Boolean = false,
    val sort: LibrarySort = LibrarySort.Recent,
    val view: LibraryView = LibraryView.Tracks,
    val working: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: TrackRepository,
    private val telegram: TelegramRepository,
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

    /** The same library folded into folders. Derived here — the API has no artists endpoint. */
    val artists: StateFlow<List<ArtistFolder>> = repo.observeLibrary()
        .map(::foldArtists)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val telegramLinked: StateFlow<Boolean> = telegram.status
        .map { it?.botEnabled == true && it.linked }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val collectionCount: StateFlow<Int> = collections.observeAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        refresh()
        viewModelScope.launch { telegram.refresh() }
    }

    fun setSort(sort: LibrarySort) { _state.value = _state.value.copy(sort = sort) }

    fun setView(view: LibraryView) { _state.value = _state.value.copy(view = view) }

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

    /** Own tracks go from the server, saved ones only leave this library. */
    fun deleteTracks(tracks: List<Track>) = bulk("Удалено") { repo.deleteMany(tracks) }

    fun sendToTelegram(tracks: List<Track>) = bulk("Отправлено в Telegram") {
        telegram.sendMany(tracks.map { it.id })
    }

    /**
     * One name for a whole folder. Only the owner may patch a track — the server refuses a PATCH
     * on an upload belonging to somebody else — so saved rows are counted out rather than
     * silently dropped.
     */
    fun renameArtist(tracks: List<Track>, newName: String, onDone: (Boolean) -> Unit = {}) {
        val name = newName.trim()
        if (name.isBlank()) return
        val owned = tracks.filter { it.isOwned }
        val skipped = tracks.size - owned.size
        if (owned.isEmpty()) {
            _state.value = _state.value.copy(
                message = "Переименовать нечего: ни один из треков вам не принадлежит",
                messageError = true
            )
            onDone(false)
            return
        }
        _state.value = _state.value.copy(working = true, message = null)
        viewModelScope.launch {
            val result = runCatching { repo.setArtistMany(owned.map { it.id }, name) }
                .getOrElse { BulkResult(0, owned.size, it.toUserMessage()) }
            _state.value = _state.value.copy(
                working = false,
                messageError = result.failed > 0,
                message = buildString {
                    append("Переименовано: ").append(result.ok)
                    if (result.failed > 0) append(" · не вышло: ").append(result.failed)
                    if (skipped > 0) append(" · пропущено (не ваши): ").append(skipped)
                    result.firstError?.let { append(" — ").append(it) }
                }
            )
            onDone(result.ok > 0)
        }
    }

    fun note(message: String, error: Boolean = false) {
        _state.value = _state.value.copy(message = message, messageError = error)
    }

    fun dismissMessage() { _state.value = _state.value.copy(message = null) }

    private fun bulk(verb: String, block: suspend () -> BulkResult) {
        if (_state.value.working) return
        _state.value = _state.value.copy(working = true, message = null)
        viewModelScope.launch {
            val result = runCatching { block() }
                .getOrElse { BulkResult(0, 1, it.toUserMessage()) }
            _state.value = _state.value.copy(
                working = false,
                messageError = result.failed > 0,
                message = buildString {
                    append(verb).append(": ").append(result.ok)
                    if (result.failed > 0) append(" · не вышло: ").append(result.failed)
                    result.firstError?.let { append(" — ").append(it) }
                }
            )
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

/** Shared with the artist screen, so a folder means the same thing on both sides of the route. */
fun foldArtists(tracks: List<Track>): List<ArtistFolder> = tracks
    .groupBy { it.artist.trim().lowercase() }
    .map { (_, group) ->
        ArtistFolder(
            name = group.first().artist.trim(),
            tracks = group.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        )
    }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
