package ru.wavelink.app.collections

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.ui.TrackRow

@Composable
fun CollectionsScreen(
    onOpen: (String) -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel()
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { creating = true }) { Text("Новая коллекция") }
        }

        state.message?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        if (collections.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Коллекций пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(collections, key = { it.id }) { collection ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(collection.id) }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(collection.name)
                            Text(
                                "${collection.trackCount} треков",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.delete(collection.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Удалить коллекцию")
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false; name = "" },
            title = { Text("Новая коллекция") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.create(name); creating = false; name = "" },
                    enabled = name.isNotBlank()
                ) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { creating = false; name = "" }) { Text("Отмена") }
            }
        )
    }
}

@Composable
fun CollectionDetailScreen(
    collectionId: String,
    onPlay: (Track, List<Track>) -> Unit,
    onOpenDetail: (Track) -> Unit,
    onShuffle: (String) -> Unit,
    viewModel: CollectionDetailViewModel = hiltViewModel()
) {
    androidx.compose.runtime.LaunchedEffect(collectionId) { viewModel.bind(collectionId) }

    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val collection by viewModel.collection.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            collection?.name ?: "…",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )

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

        state.message?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    onPlay = { onPlay(track, tracks) },
                    onOpenDetail = { onOpenDetail(track) }
                )
            }
        }
    }
}
