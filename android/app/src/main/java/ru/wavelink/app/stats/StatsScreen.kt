package ru.wavelink.app.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wavelink.app.ui.formatListened

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatsPeriod.entries.forEach { period ->
                FilterChip(
                    selected = state.period == period,
                    onClick = { viewModel.setPeriod(period) },
                    label = { Text(period.label) }
                )
            }
        }

        if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        val stats = state.stats ?: return@Column

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("прослушиваний", stats.totalPlays.toString(), Modifier.weight(1f))
                    StatTile("треков", stats.distinctTracks.toString(), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("дослушано", stats.completedPlays.toString(), Modifier.weight(1f))
                    StatTile("времени", formatListened(stats.totalListenedSeconds), Modifier.weight(1f))
                }
            }

            item { SectionLabel("Топ треков") }
            items(stats.topTracks, key = { it.trackId }) { track ->
                ChartRow(
                    rank = stats.topTracks.indexOf(track) + 1,
                    title = track.title,
                    subtitle = track.artist,
                    count = track.plays
                )
            }

            item { SectionLabel("Топ исполнителей") }
            items(stats.topArtists, key = { it.artist }) { artist ->
                ChartRow(
                    rank = stats.topArtists.indexOf(artist) + 1,
                    title = artist.artist,
                    subtitle = "${artist.trackCount} трек(ов) · ${formatListened(artist.listenedSeconds)}",
                    count = artist.plays
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun ChartRow(rank: Int, title: String, subtitle: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            rank.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(count.toString(), color = MaterialTheme.colorScheme.primary)
    }
}
