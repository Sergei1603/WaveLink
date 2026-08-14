package ru.wavelink.app.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.wavelink.app.core.db.DownloadDao
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.core.model.parseInstant
import ru.wavelink.app.core.net.TrackDetailDto
import ru.wavelink.app.core.net.isOffline
import ru.wavelink.app.core.net.toUserMessage
import ru.wavelink.app.telegram.TelegramRepository
import java.time.Instant
import javax.inject.Inject

/**
 * What the card renders. Built from `GET /api/tracks/{id}` when the server answers and from the
 * Room row when it does not, because a card that says only «Загрузка…» while offline is useless
 * in an app whose whole point is that it opens without a network.
 */
data class TrackCard(
    val title: String,
    val artist: String,
    val uploaderUsername: String,
    val duration: Int,
    val fileSize: Long,
    val mimeType: String,
    val isPublic: Boolean,
    val isOwned: Boolean,
    val uploadedAt: Instant?,
    val myPlays: Int,
    val myLastPlayedAt: Instant?,
    val myCompleted: Boolean,
    /** Null when only the cached row was available — cross-user counts live on the server. */
    val myListenedSeconds: Long?,
    val totalPlays: Int?,
    val distinctListeners: Int?
)

data class TrackDetailUiState(
    val card: TrackCard? = null,
    val downloaded: Boolean = false,
    val telegramLinked: Boolean = false,
    val stale: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class TrackDetailViewModel @Inject constructor(
    private val repo: TrackRepository,
    private val telegram: TelegramRepository,
    private val downloads: DownloadDao
) : ViewModel() {

    private val _state = MutableStateFlow(TrackDetailUiState())
    val state: StateFlow<TrackDetailUiState> = _state.asStateFlow()

    fun load(trackId: String) {
        _state.value = TrackDetailUiState()
        viewModelScope.launch {
            val downloaded = downloads.byTrack(trackId) != null
            val cached = repo.observeTrack(trackId).first()
            // Paint the cached row first so the sheet is never blank, then refine it.
            if (cached != null) {
                _state.value = _state.value.copy(card = cached.toCard(), downloaded = downloaded, stale = true)
            }

            telegram.refresh()
            val status = telegram.status.value

            runCatching { repo.detail(trackId) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        card = it.toCard(),
                        downloaded = downloaded,
                        stale = false,
                        telegramLinked = status?.botEnabled == true && status.linked
                    )
                }
                .onFailure { failure ->
                    _state.value = _state.value.copy(
                        downloaded = downloaded,
                        error = when {
                            cached != null && failure.isOffline() ->
                                "Нет связи — показаны сохранённые данные"
                            else -> failure.toUserMessage()
                        }
                    )
                }
        }
    }

    /**
     * Only the uploader may edit, which the server enforces too — the form is simply hidden for
     * tracks saved from the public bank.
     */
    fun save(trackId: String, title: String, artist: String, isPublic: Boolean) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true, error = null, message = null)
        viewModelScope.launch {
            runCatching { repo.update(trackId, title.trim(), artist.trim(), isPublic) }
                .onSuccess {
                    _state.value = _state.value.copy(saving = false, message = "Сохранено")
                    load(trackId)
                }
                .onFailure {
                    _state.value = _state.value.copy(saving = false, error = it.toUserMessage())
                }
        }
    }

    fun sendToTelegram(trackId: String) = viewModelScope.launch {
        runCatching { telegram.send(trackId) }
            .onSuccess { _state.value = _state.value.copy(message = "Отправлено в Telegram") }
            .onFailure { _state.value = _state.value.copy(error = it.toUserMessage()) }
    }

    fun note(message: String) { _state.value = _state.value.copy(message = message) }
}

private fun Track.toCard() = TrackCard(
    title = title,
    artist = artist,
    uploaderUsername = uploaderUsername,
    duration = duration,
    fileSize = fileSize,
    mimeType = mimeType,
    isPublic = isPublic,
    isOwned = isOwned,
    uploadedAt = uploadedAt,
    myPlays = myPlays,
    myLastPlayedAt = myLastPlayedAt,
    myCompleted = myCompleted,
    myListenedSeconds = null,
    totalPlays = null,
    distinctListeners = null
)

private fun TrackDetailDto.toCard() = TrackCard(
    title = track.title,
    artist = track.artist,
    uploaderUsername = track.uploaderUsername,
    duration = track.duration,
    fileSize = track.fileSize,
    mimeType = track.mimeType,
    isPublic = track.isPublic,
    isOwned = track.isOwned,
    uploadedAt = parseInstant(track.uploadedAt),
    myPlays = stats.myPlays,
    myLastPlayedAt = parseInstant(stats.myLastPlayedAt),
    myCompleted = stats.myCompleted,
    myListenedSeconds = stats.myListenedSeconds,
    totalPlays = stats.totalPlays,
    distinctListeners = stats.distinctListeners
)
