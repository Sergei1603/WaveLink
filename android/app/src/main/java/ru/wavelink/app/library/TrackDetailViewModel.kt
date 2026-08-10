package ru.wavelink.app.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.wavelink.app.core.db.DownloadDao
import ru.wavelink.app.core.net.TrackDetailDto
import ru.wavelink.app.core.net.toUserMessage
import javax.inject.Inject

data class TrackDetailUiState(
    val detail: TrackDetailDto? = null,
    val downloaded: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TrackDetailViewModel @Inject constructor(
    private val repo: TrackRepository,
    private val downloads: DownloadDao
) : ViewModel() {

    private val _state = MutableStateFlow(TrackDetailUiState())
    val state: StateFlow<TrackDetailUiState> = _state.asStateFlow()

    fun load(trackId: String) {
        _state.value = TrackDetailUiState()
        viewModelScope.launch {
            val downloaded = downloads.byTrack(trackId) != null
            runCatching { repo.detail(trackId) }
                .onSuccess { _state.value = TrackDetailUiState(detail = it, downloaded = downloaded) }
                .onFailure {
                    _state.value = TrackDetailUiState(downloaded = downloaded, error = it.toUserMessage())
                }
        }
    }
}
