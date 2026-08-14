package ru.wavelink.app.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ru.wavelink.app.auth.AuthRepository
import ru.wavelink.app.core.db.TrackDao
import ru.wavelink.app.core.prefs.SettingsStore
import ru.wavelink.app.telegram.TelegramRepository
import javax.inject.Inject

data class ProfileUiState(
    val username: String? = null,
    val trackCount: Int = 0,
    val publishedCount: Int = 0,
    val baseUrl: String = "",
    val cacheBytes: Long = SettingsStore.DEFAULT_CACHE_BYTES,
    val wifiOnlyDownloads: Boolean = true,
    val backgroundPlayback: Boolean = true,
    /** Null until the server has answered — "unknown" must not be shown as "switched off". */
    val telegramBotEnabled: Boolean? = null,
    val telegramLinked: Boolean = false
)

/** Just a carrier so the four settings flows can be combined without a tuple of Any. */
private data class Prefs(
    val baseUrl: String,
    val cacheBytes: Long,
    val wifiOnly: Boolean,
    val background: Boolean
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val auth: AuthRepository,
    private val telegram: TelegramRepository,
    trackDao: TrackDao
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState(username = auth.username()))
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settings.baseUrl,
                settings.cacheBytes,
                settings.wifiOnlyDownloads,
                settings.backgroundPlayback
            ) { url, bytes, wifiOnly, background ->
                Prefs(url, bytes, wifiOnly, background)
            }.collect { prefs ->
                _state.value = _state.value.copy(
                    username = auth.username(),
                    baseUrl = prefs.baseUrl,
                    cacheBytes = prefs.cacheBytes,
                    wifiOnlyDownloads = prefs.wifiOnly,
                    backgroundPlayback = prefs.background
                )
            }
        }
        viewModelScope.launch {
            combine(trackDao.observeLibraryCount(), trackDao.observePublishedCount()) { all, published ->
                all to published
            }.collect { (all, published) ->
                _state.value = _state.value.copy(trackCount = all, publishedCount = published)
            }
        }
        viewModelScope.launch {
            telegram.refresh()
            telegram.status.collect { status ->
                _state.value = _state.value.copy(
                    telegramBotEnabled = status?.botEnabled,
                    telegramLinked = status?.linked == true
                )
            }
        }
    }

    fun setBaseUrl(value: String) = viewModelScope.launch { settings.setBaseUrl(value) }

    fun setCacheMb(value: Long) = viewModelScope.launch {
        settings.setCacheBytes(value * 1024 * 1024)
    }

    fun setWifiOnlyDownloads(value: Boolean) =
        viewModelScope.launch { settings.setWifiOnlyDownloads(value) }

    fun setBackgroundPlayback(value: Boolean) =
        viewModelScope.launch { settings.setBackgroundPlayback(value) }
}
