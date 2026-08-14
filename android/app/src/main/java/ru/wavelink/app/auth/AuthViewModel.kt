package ru.wavelink.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.wavelink.app.core.net.toUserMessage
import ru.wavelink.app.core.prefs.SettingsStore
import javax.inject.Inject

data class AuthUiState(
    val busy: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    val isSignedIn: StateFlow<Boolean?> = repo.isSignedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Reachable before sign-in on purpose: on a physical device the default host is the
     * emulator's loopback alias, so without this the first login can never succeed.
     */
    val baseUrl: StateFlow<String> = settings.baseUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setBaseUrl(value: String) = viewModelScope.launch { settings.setBaseUrl(value) }

    fun login(username: String, password: String) = submit { repo.login(username, password) }

    fun register(username: String, password: String) = submit { repo.register(username, password) }

    fun logout() = viewModelScope.launch { repo.logout() }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    private fun submit(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.value = AuthUiState(busy = true)
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _state.value = AuthUiState() }
                .onFailure { _state.value = AuthUiState(error = it.toUserMessage()) }
        }
    }
}
