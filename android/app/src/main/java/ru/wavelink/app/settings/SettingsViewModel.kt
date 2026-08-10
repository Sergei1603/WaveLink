package ru.wavelink.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ru.wavelink.app.auth.AuthRepository
import ru.wavelink.app.core.prefs.SettingsStore
import javax.inject.Inject

data class SettingsUiState(
    val username: String? = null,
    val baseUrl: String = "",
    val cacheMb: Long = 512
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val auth: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(settings.baseUrl, settings.cacheBytes) { url, bytes -> url to bytes }
                .collect { (url, bytes) ->
                    _state.value = SettingsUiState(
                        username = auth.username(),
                        baseUrl = url,
                        cacheMb = bytes / 1024 / 1024
                    )
                }
        }
    }

    fun setBaseUrl(value: String) = viewModelScope.launch { settings.setBaseUrl(value) }

    fun setCacheMb(value: Long) = viewModelScope.launch {
        settings.setCacheBytes(value * 1024 * 1024)
    }
}
