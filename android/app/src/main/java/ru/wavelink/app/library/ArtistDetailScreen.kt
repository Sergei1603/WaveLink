package ru.wavelink.app.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wavelink.app.collections.AddToCollectionDialog
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.ui.TrackRow
import ru.wavelink.app.ui.components.WlButton
import ru.wavelink.app.ui.components.WlScreenHeader
import ru.wavelink.app.ui.formatTotalDuration
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType
import ru.wavelink.app.ui.tracksLabel

/**
 * One artist folder opened: the tracks inside it, the same hold-to-select contract as the
 * library, and «Переименовать», which rewrites the artist on every track you own in the folder.
 *
 * It reuses [LibraryViewModel] rather than growing a second one — the folder is a filtered view
 * of the same Room-backed library, so it stays readable offline like everything else here.
 */
@Composable
fun ArtistDetailScreen(
    artist: String,
    playingTrackId: String?,
    onBack: () -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onOpenDetail: (Track) -> Unit,
    onOpenArtist: (String) -> Unit,
    onDownload: (List<String>) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val telegramLinked by viewModel.telegramLinked.collectAsStateWithLifecycle()

    val key = artist.trim().lowercase()
    val folder = artists.firstOrNull { it.key == key }
    val tracks = folder?.tracks.orEmpty()

    val selection = rememberSelection()
    var addingToCollection by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    LaunchedEffect(tracks.map { it.id }) { selection.retainAll(tracks.map { it.id }) }

    val selected = tracks.filter { selection.contains(it.id) }

    Column(modifier = Modifier.fillMaxSize().background(Wl.Bg).statusBarsPadding()) {
        WlScreenHeader(parent = "Библиотека", onBack = onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(folder?.name ?: artist, style = WlType.Title, color = Wl.Text)
                Text(
                    "${tracksLabel(tracks.size)} · " +
                        formatTotalDuration(tracks.sumOf { it.duration.toLong() }),
                    style = WlType.Meta,
                    color = Wl.text(50),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (folder != null && folder.ownedCount > 0) {
                WlButton(text = "Переименовать", onClick = { renaming = true })
            }
        }

        state.message?.let { Notice(it, error = state.messageError) }

        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Здесь пусто", style = WlType.BodySm, color = Wl.text(45))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        index = index,
                        playing = track.id == playingTrackId,
                        onPlay = { onPlay(track, tracks) },
                        onOpenDetail = { onOpenDetail(track) },
                        selectionMode = selection.active,
                        selected = selection.contains(track.id),
                        onToggleSelect = { selection.toggle(track.id) }
                    )
                }
            }
        }

        if (selection.active) {
            SelectionBar(
                count = selection.count,
                onClear = selection::clear,
                actions = buildList {
                    if (selected.size == 1) {
                        add(
                            SelectionAction("Инфо", Icons.Filled.Info) {
                                selected.single().let(onOpenDetail)
                                selection.clear()
                            }
                        )
                    }
                    add(
                        SelectionAction("Скачать", Icons.Filled.Download) {
                            onDownload(selected.map { it.id })
                            viewModel.note("В загрузки: ${selected.size}")
                            selection.clear()
                        }
                    )
                    add(
                        SelectionAction("Коллекция", Icons.Filled.PlaylistAdd, enabled = !state.working) {
                            addingToCollection = true
                        }
                    )
                    if (telegramLinked) {
                        add(
                            SelectionAction("Telegram", Icons.Filled.Send, enabled = !state.working) {
                                viewModel.sendToTelegram(selected)
                                selection.clear()
                            }
                        )
                    }
                    add(
                        SelectionAction("Удалить", Icons.Filled.Delete, enabled = !state.working) {
                            confirmingDelete = true
                        }
                    )
                }
            )
        }
    }

    if (addingToCollection) {
        AddToCollectionDialog(
            trackIds = selected.map { it.id },
            onDismiss = { addingToCollection = false },
            // Cancelling must not throw the selection away; only a completed add does.
            onResult = { message -> viewModel.note(message); selection.clear() }
        )
    }

    if (confirmingDelete) {
        val owned = selected.count { it.isOwned }
        val saved = selected.size - owned
        ConfirmDialog(
            title = "Удалить ${tracksLabel(selected.size)}?",
            message = buildString {
                if (owned > 0) append("Своих будет удалено безвозвратно: $owned. ")
                if (saved > 0) append("Сохранённых уберём только из вашей библиотеки: $saved.")
            }.trim(),
            onConfirm = {
                viewModel.deleteTracks(selected)
                selection.clear()
            },
            onDismiss = { confirmingDelete = false }
        )
    }

    if (renaming && folder != null) {
        RenameArtistDialog(
            folder = folder,
            onDismiss = { renaming = false },
            onConfirm = { newName ->
                // The route is keyed by the artist, so a successful rename must move the screen
                // onto the new folder — otherwise it sits on a name that no longer exists.
                viewModel.renameArtist(folder.tracks, newName) { changed ->
                    if (changed) onOpenArtist(newName.trim())
                }
                selection.clear()
            }
        )
    }
}
