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
        if (_state.value.period == period) return
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

/** Which list the Топ-100 screen is showing — the only difference between screens 10 and 11. */
enum class TopChartKind(val title: String) {
    Tracks("Топ треков"),
    Artists("Топ исполнителей")
}

data class TopChartUiState(
    val period: StatsPeriod = StatsPeriod.AllTime,
    val stats: MyStatsDto? = null,
    val loading: Boolean = false,
    val error: String? = null
)

/**
 * Screens 10 and 11 share this: same request, larger `limit`, and a Список/Диаграмма switch on
 * top. Kept apart from [StatsViewModel] so opening the full list does not disturb the summary.
 */
@HiltViewModel
class TopChartViewModel @Inject constructor(
    private val api: WaveLinkApi
) : ViewModel() {

    private val _state = MutableStateFlow(TopChartUiState())
    val state: StateFlow<TopChartUiState> = _state.asStateFlow()

    init { load() }

    fun setPeriod(period: StatsPeriod) {
        if (_state.value.period == period) return
        _state.value = _state.value.copy(period = period)
        load()
    }

    private fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val from = _state.value.period.days
                ?.let { Instant.now().minus(it, ChronoUnit.DAYS).toString() }
            runCatching { api.myStats(from = from, limit = TOP_LIMIT) }
                .onSuccess { _state.value = _state.value.copy(loading = false, stats = it) }
                .onFailure {
                    _state.value = _state.value.copy(loading = false, error = it.toUserMessage())
                }
        }
    }

    companion object {
        /** «100 позиций» in the design; the server clamps anything larger anyway. */
        const val TOP_LIMIT = 100
    }
}
