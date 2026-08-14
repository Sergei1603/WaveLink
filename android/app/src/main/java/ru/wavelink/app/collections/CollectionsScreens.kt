package ru.wavelink.app.collections

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.ui.TrackRow
import ru.wavelink.app.ui.collectionsLabel
import ru.wavelink.app.ui.components.WlButton
import ru.wavelink.app.ui.components.WlInput
import ru.wavelink.app.ui.components.WlScreenHeader
import ru.wavelink.app.ui.components.WlWaveMark
import ru.wavelink.app.ui.components.fadingRule
import ru.wavelink.app.ui.formatTotalDuration
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType
import ru.wavelink.app.ui.tracksLabel

/**
 * Screen 06. A level inside Библиотека, not a tab of its own. Cards carry a generated wave mark
 * instead of artwork — WaveLink stores no cover images, and an empty grey square says nothing.
 */
@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onShuffle: (mode: String) -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel()
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(Wl.Bg).statusBarsPadding()) {
        WlScreenHeader(parent = "Библиотека", onBack = onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Коллекции", style = WlType.Title, color = Wl.Text)
                Text(
                    "${collectionsLabel(collections.size)} · " +
                        tracksLabel(collections.sumOf { it.trackCount }),
                    style = WlType.Meta,
                    color = Wl.text(50),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            WlButton(text = "Создать", onClick = { creating = true })
        }

        state.message?.let {
            Text(it, style = WlType.Meta, color = Wl.Accent300, modifier = Modifier.padding(horizontal = 20.dp))
        }

        if (collections.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Коллекций пока нет", style = WlType.BodySm, color = Wl.text(45))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(collections, key = { it.id }) { collection ->
                    CollectionCard(
                        name = collection.name,
                        trackCount = collection.trackCount,
                        seed = collection.id.hashCode(),
                        onOpen = { onOpen(collection.id) },
                        onDelete = { viewModel.delete(collection.id) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fadingRule(top = true, inset = 0.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WlButton(
                text = "Перемешать всё",
                onClick = { onShuffle("random") },
                minHeight = 42.dp,
                fontSize = WlType.Caption,
                modifier = Modifier.weight(1f)
            )
            WlButton(
                text = "Умное перемешивание",
                onClick = { onShuffle("discover") },
                minHeight = 42.dp,
                fontSize = WlType.Caption,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false; name = "" },
            containerColor = Wl.Surface,
            title = { Text("Новая коллекция", style = WlType.Heading, color = Wl.Text) },
            text = {
                WlInput(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Название",
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.create(name); creating = false; name = "" },
                    enabled = name.isNotBlank()
                ) { Text("Создать", color = Wl.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { creating = false; name = "" }) {
                    Text("Отмена", color = Wl.text(60))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionCard(
    name: String,
    trackCount: Int,
    seed: Int,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    var confirming by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .clip(Wl.RadiusLg)
            .background(Wl.Surface)
            .combinedClickable(onClick = onOpen, onLongClick = { confirming = true })
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .background(
                    Brush.verticalGradient(listOf(Wl.accent(20), androidx.compose.ui.graphics.Color.Transparent))
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            WlWaveMark(
                seed = seed,
                bars = 14,
                maxHeight = 58.dp,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            )
        }
        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)) {
            Text(name, style = WlType.RowTitle, color = Wl.Text, maxLines = 1)
            Text(
                tracksLabel(trackCount),
                style = WlType.Meta,
                color = Wl.text(50),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = Wl.Surface,
            title = { Text("Удалить «$name»?", style = WlType.Heading, color = Wl.Text) },
            text = {
                Text(
                    "Треки останутся в библиотеке — исчезнет только подборка.",
                    style = WlType.BodySm,
                    color = Wl.text(60)
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirming = false }) {
                    Text("Удалить", color = Wl.Accent300)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Отмена", color = Wl.text(60)) }
            }
        )
    }
}

/** One collection's tracks — the same list treatment as the library, one level deeper. */
@Composable
fun CollectionDetailScreen(
    collectionId: String,
    playingTrackId: String?,
    onBack: () -> Unit,
    onPlay: (Track, List<Track>, String) -> Unit,
    onOpenDetail: (Track) -> Unit,
    onShuffle: (String) -> Unit,
    viewModel: CollectionDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(collectionId) { viewModel.bind(collectionId) }

    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val collection by viewModel.collection.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title = collection?.name ?: "Коллекция"

    Column(modifier = Modifier.fillMaxSize().background(Wl.Bg).statusBarsPadding()) {
        WlScreenHeader(parent = "Коллекции", onBack = onBack)

        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(title, style = WlType.Title, color = Wl.Text)
                Text(
                    "${tracksLabel(tracks.size)} · " +
                        formatTotalDuration(tracks.sumOf { it.duration.toLong() }),
                    style = WlType.Meta,
                    color = Wl.text(50),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WlButton(
                    text = "Перемешивание",
                    onClick = { onShuffle("random") },
                    minHeight = 42.dp,
                    fontSize = WlType.Caption,
                    modifier = Modifier.weight(1f)
                )
                WlButton(
                    text = "Умное перемешивание",
                    onClick = { onShuffle("discover") },
                    minHeight = 42.dp,
                    fontSize = WlType.Caption,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        state.message?.let {
            Text(it, style = WlType.Meta, color = Wl.Accent300, modifier = Modifier.padding(horizontal = 20.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                TrackRow(
                    track = track,
                    index = index,
                    playing = track.id == playingTrackId,
                    onPlay = { onPlay(track, tracks, title) },
                    onOpenDetail = { onOpenDetail(track) }
                )
            }
        }
    }
}
