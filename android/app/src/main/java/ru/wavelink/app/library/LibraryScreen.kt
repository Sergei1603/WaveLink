package ru.wavelink.app.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.ui.TrackRow

@Composable
fun LibraryScreen(
    onPlay: (Track, List<Track>) -> Unit,
    onOpenDetail: (Track) -> Unit,
    onShuffle: (mode: String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()

    val visible = remember(tracks, state.query) {
        if (state.query.isBlank()) tracks
        else tracks.filter {
            it.title.contains(state.query, ignoreCase = true) ||
                it.artist.contains(state.query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Поиск…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { onShuffle("random") }, modifier = Modifier.weight(1f)) {
                Text("Перемешать")
            }
            Button(onClick = { onShuffle("discover") }, modifier = Modifier.weight(1f)) {
                Text("Открыть новое")
            }
        }

        if (state.refreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        if (state.offline) {
            Text(
                "Нет сети — показана сохранённая библиотека",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
        state.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        if (visible.isEmpty() && !state.refreshing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.query.isBlank()) "Библиотека пуста"
                    else "Ничего не найдено",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visible, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        onPlay = { onPlay(track, visible) },
                        onOpenDetail = { onOpenDetail(track) }
                    )
                }
            }
        }
    }
}
