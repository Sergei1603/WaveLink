package ru.wavelink.app.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wavelink.app.ui.components.WlCard
import ru.wavelink.app.ui.components.WlMeter
import ru.wavelink.app.ui.components.WlScreenHeader
import ru.wavelink.app.ui.components.WlSectionHeader
import ru.wavelink.app.ui.components.WlSegmented
import ru.wavelink.app.ui.formatListened
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType
import ru.wavelink.app.ui.tracksLabel

/**
 * Screen 08. A summary, not the whole picture: five tracks and four artists, each with a link
 * into the full Топ-100. Everything here is the caller's own listening — no one else's.
 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onOpenTop: (TopChartKind) -> Unit,
    onOpenTrack: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats = state.stats

    Column(modifier = Modifier.fillMaxSize().background(Wl.Bg).statusBarsPadding()) {
        WlScreenHeader(parent = "Профиль", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                    Text("Статистика", style = WlType.Title, color = Wl.Text)
                    Text(
                        "Что вы слушали и сколько",
                        style = WlType.Meta,
                        color = Wl.text(50),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            item {
                WlSegmented(
                    options = StatsPeriod.entries.map { it.label },
                    selected = StatsPeriod.entries.indexOf(state.period),
                    onSelect = { viewModel.setPeriod(StatsPeriod.entries[it]) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            state.error?.let { message ->
                item { Text(message, style = WlType.Meta, color = Wl.Accent300) }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            (stats?.totalPlays ?: 0).toString(),
                            "прослушиваний",
                            Modifier.weight(1f)
                        )
                        StatTile(
                            (stats?.distinctTracks ?: 0).toString(),
                            "разных треков",
                            Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            (stats?.completedPlays ?: 0).toString(),
                            "дослушано до конца",
                            Modifier.weight(1f)
                        )
                        StatTile(
                            formatListened(stats?.totalListenedSeconds ?: 0),
                            "времени прослушано",
                            Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                WlSectionHeader(
                    title = "Топ треков",
                    action = "Подробнее →",
                    onAction = { onOpenTop(TopChartKind.Tracks) },
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            val topTracks = stats?.topTracks.orEmpty().take(5)
            val trackPeak = topTracks.maxOfOrNull { it.plays }?.coerceAtLeast(1) ?: 1
            items(topTracks.size) { index ->
                val track = topTracks[index]
                RankRow(
                    rank = index + 1,
                    title = track.title,
                    subtitle = track.artist,
                    fraction = track.plays.toFloat() / trackPeak,
                    value = track.plays,
                    onClick = { onOpenTrack(track.trackId) }
                )
            }

            item {
                WlSectionHeader(
                    title = "Топ исполнителей",
                    action = "Подробнее →",
                    onAction = { onOpenTop(TopChartKind.Artists) },
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            val topArtists = stats?.topArtists.orEmpty().take(4)
            val artistPeak = topArtists.maxOfOrNull { it.plays }?.coerceAtLeast(1) ?: 1
            items(topArtists.size) { index ->
                val artist = topArtists[index]
                RankRow(
                    rank = index + 1,
                    title = artist.artist,
                    subtitle = "${tracksLabel(artist.trackCount)} · " +
                        formatListened(artist.listenedSeconds),
                    fraction = artist.plays.toFloat() / artistPeak,
                    value = artist.plays,
                    onClick = { onOpenArtist(artist.artist) }
                )
            }

            if (stats != null && stats.topTracks.isEmpty()) {
                item {
                    Text(
                        "Пока нечего показать — послушайте что-нибудь.",
                        style = WlType.BodySm,
                        color = Wl.text(45),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    WlCard(modifier = modifier) {
        Column {
            Text(value, style = WlType.Numeric, color = Wl.Text)
            Text(label, style = WlType.Micro, color = Wl.text(50))
        }
    }
}

/**
 * rank · title over subtitle · a bar scaled to the leader · the count itself.
 *
 * [onClick] is what makes the charts navigable: a track row opens its card, an artist row opens
 * that artist's folder in the library. Rows without one stay inert.
 */
@Composable
internal fun RankRow(
    rank: Int,
    title: String,
    subtitle: String,
    fraction: Float,
    value: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    barWidth: androidx.compose.ui.unit.Dp = 84.dp,
    rankWidth: androidx.compose.ui.unit.Dp = 16.dp
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Wl.RadiusMd)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            rank.toString(),
            style = WlType.Meta,
            color = Wl.text(35),
            textAlign = TextAlign.End,
            modifier = Modifier.width(rankWidth)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = WlType.BodySm, color = Wl.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = WlType.Micro,
                color = Wl.text(48),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        WlMeter(fraction = fraction, modifier = Modifier.width(barWidth))
        Text(
            value.toString(),
            style = WlType.Meta,
            color = Wl.Accent300,
            textAlign = TextAlign.End,
            modifier = Modifier.width(26.dp)
        )
    }
}
