package ru.wavelink.app.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.wavelink.app.core.net.MyStatsDto
import ru.wavelink.app.core.net.WaveLinkApi
import ru.wavelink.app.core.net.toUserMessage
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

enum class StatsPeriod(val label: String, val days: Long?) {
    AllTime("Всё время", null),
    Month("30 дней", 30),
    Week("7 дней", 7)
}

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.AllTime,
    val stats: MyStatsDto? = null,
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val api: WaveLinkApi
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init { load() }

    fun setPeriod(period: StatsPeriod) {
        _state.value = _state.value.copy(period = period)
        load()
    }

    private fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val from = _state.value.period.days
                ?.let { Instant.now().minus(it, ChronoUnit.DAYS).toString() }
            runCatching { api.myStats(from = from, limit = 20) }
                .onSuccess { _state.value = _state.value.copy(loading = false, stats = it) }
                .onFailure {
                    _state.value = _state.value.copy(loading = false, error = it.toUserMessage())
                }
        }
    }
}
