package ru.wavelink.app.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wavelink.app.ui.components.WlDonut
import ru.wavelink.app.ui.components.WlScreenHeader
import ru.wavelink.app.ui.components.WlSegmented
import ru.wavelink.app.ui.components.WlSliceColors
import ru.wavelink.app.ui.components.fadingRule
import ru.wavelink.app.ui.formatListened
import ru.wavelink.app.ui.playsLabel
import ru.wavelink.app.ui.positionsLabel
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType
import ru.wavelink.app.ui.tracksLabel

/** One entry, whichever list is showing — the two differ only in what the subtitle says. */
private data class TopEntry(
    val title: String,
    val subtitle: String,
    val plays: Int
)

private enum class TopView(val label: String) { List("Список"), Chart("Диаграмма") }

/**
 * Screens 10 and 11. Same data, two readings: the ranked list answers "what did I play most",
 * the ring answers "how is my listening split" — which is why the design offers both.
 */
@Composable
fun TopChartScreen(
    kind: TopChartKind,
    onBack: () -> Unit,
    viewModel: TopChartViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The mock opens tracks as a list and artists as a ring; both switches still work.
    var view by rememberSaveable(kind) {
        mutableStateOf(if (kind == TopChartKind.Artists) TopView.Chart else TopView.List)
    }

    val entries = remember(state.stats, kind) {
        when (kind) {
            TopChartKind.Tracks -> state.stats?.topTracks.orEmpty().map {
                TopEntry(it.title, "${it.artist} · ${formatListened(it.listenedSeconds)}", it.plays)
            }
            TopChartKind.Artists -> state.stats?.topArtists.orEmpty().map {
                TopEntry(
                    it.artist,
                    "${tracksLabel(it.trackCount)} · ${formatListened(it.listenedSeconds)}",
                    it.plays
                )
            }
        }
    }
    val totalPlays = state.stats?.totalPlays ?: 0

    Column(modifier = Modifier.fillMaxSize().background(Wl.Bg).statusBarsPadding()) {
        WlScreenHeader(parent = "Статистика", onBack = onBack)

        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(kind.title, style = WlType.Title, color = Wl.Text)
                Text(
                    "${positionsLabel(entries.size)} · " + when (view) {
                        TopView.List -> "${playsLabel(totalPlays)} · ${state.period.label.lowercase()}"
                        TopView.Chart -> "доля прослушиваний"
                    },
                    style = WlType.Meta,
                    color = Wl.text(50),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            WlSegmented(
                options = TopView.entries.map { it.label },
                selected = TopView.entries.indexOf(view),
                onSelect = { view = TopView.entries[it] },
                minHeight = 38.dp,
                icons = listOf(
                    {
                        Icon(
                            Icons.Filled.FormatListBulleted,
                            contentDescription = null,
                            tint = LocalContentColor.current,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    {
                        Icon(
                            Icons.Filled.DonutLarge,
                            contentDescription = null,
                            tint = LocalContentColor.current,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )
            WlSegmented(
                options = StatsPeriod.entries.map { it.label },
                selected = StatsPeriod.entries.indexOf(state.period),
                onSelect = { viewModel.setPeriod(StatsPeriod.entries[it]) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        state.error?.let {
            Text(it, style = WlType.Meta, color = Wl.Accent300, modifier = Modifier.padding(horizontal = 20.dp))
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.loading) "Загрузка…" else "Пока нечего показать",
                    style = WlType.BodySm,
                    color = Wl.text(45)
                )
            }
            return@Column
        }

        when (view) {
            TopView.List -> RankedList(
                entries = entries,
                modifier = Modifier.weight(1f)
            )
            TopView.Chart -> ShareChart(
                entries = entries,
                totalPlays = totalPlays,
                modifier = Modifier.weight(1f)
            )
        }

        if (view == TopView.List) {
            Text(
                "1–${entries.size} из ${TopChartViewModel.TOP_LIMIT} · прокрутите список",
                style = WlType.Micro,
                color = Wl.text(38),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .fadingRule(top = true, inset = 0.dp)
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp)
            )
        }
    }
}

@Composable
private fun RankedList(entries: List<TopEntry>, modifier: Modifier = Modifier) {
    val peak = entries.maxOfOrNull { it.plays }?.coerceAtLeast(1) ?: 1
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(entries.size) { index ->
            val entry = entries[index]
            RankRow(
                rank = index + 1,
                title = entry.title,
                subtitle = entry.subtitle,
                fraction = entry.plays.toFloat() / peak,
                value = entry.plays,
                barWidth = 60.dp,
                rankWidth = 22.dp,
                modifier = Modifier.fadingRule(inset = 20.dp, color = Wl.text(7))
            )
        }
    }
}

/**
 * Six named slices and one "everything else", which is how the design keeps a hundred-entry
 * breakdown readable. The ring and the legend walk the same colour ramp in the same order.
 */
@Composable
private fun ShareChart(entries: List<TopEntry>, totalPlays: Int, modifier: Modifier = Modifier) {
    val named = entries.take(WlSliceColors.size)
    val namedPlays = named.sumOf { it.plays }
    val rest = (totalPlays - namedPlays).coerceAtLeast(0)
    val whole = (namedPlays + rest).coerceAtLeast(1)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                WlDonut(
                    slices = named.mapIndexed { i, e ->
                        e.plays.toFloat() to WlSliceColors[i]
                    } + (rest.toFloat() to Wl.Neutral800)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(totalPlays.toString(), style = WlType.Numeric, color = Wl.Text)
                        Text("прослушиваний", style = WlType.Micro, color = Wl.text(50))
                    }
                }
            }
        }
        items(named.size) { index ->
            LegendRow(
                color = WlSliceColors[index],
                label = named[index].title,
                value = "${named[index].plays} · ${percent(named[index].plays, whole)}"
            )
        }
        if (rest > 0) {
            item {
                LegendRow(
                    color = Wl.Neutral800,
                    label = "Ещё ${entries.size - named.size}",
                    value = "$rest · ${percent(rest, whole)}",
                    muted = true
                )
            }
        }
    }
}

@Composable
private fun LegendRow(color: androidx.compose.ui.graphics.Color, label: String, value: String, muted: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(
            label,
            style = WlType.BodySm,
            color = if (muted) Wl.text(65) else Wl.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = WlType.Meta, color = Wl.text(48))
    }
}

private fun percent(part: Int, whole: Int): String = "${(part * 100f / whole).toInt()}%"
