package ru.wavelink.app.publicbank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.core.net.toUserMessage
import ru.wavelink.app.library.TrackRepository
import javax.inject.Inject

data class PublicBankUiState(
    val query: String = "",
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

    private val queries = MutableStateFlow("")

    init {
        viewModelScope.launch {
            // Debounced to match the web client, which waits 300 ms before hitting the API.
            queries.debounce(300).distinctUntilChanged().collect { load(it) }
        }
    }

    fun search(value: String) {
        _state.value = _state.value.copy(query = value)
        queries.value = value
    }

    fun save(track: Track) = viewModelScope.launch {
        runCatching { repo.save(track.id) }
            .onSuccess { _state.value = _state.value.copy(message = "«${track.title}» добавлен в библиотеку") }
            .onFailure { _state.value = _state.value.copy(message = it.toUserMessage()) }
    }

    private suspend fun load(query: String) {
        _state.value = _state.value.copy(loading = true, message = null)
        runCatching { repo.searchPublic(query) }
            .onSuccess { _state.value = _state.value.copy(loading = false, results = it) }
            .onFailure {
                _state.value = _state.value.copy(loading = false, message = it.toUserMessage())
            }
    }
}
