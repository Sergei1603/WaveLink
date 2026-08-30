package ru.wavelink.app.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wavelink.app.R
import ru.wavelink.app.collections.AddToCollectionDialog
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.ui.TrackRow
import ru.wavelink.app.ui.components.WlBackdrop
import ru.wavelink.app.ui.components.WlButton
import ru.wavelink.app.ui.components.WlInput
import ru.wavelink.app.ui.components.WlInputButton
import ru.wavelink.app.ui.components.WlSegmented
import ru.wavelink.app.ui.formatTotalDuration
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType
import ru.wavelink.app.ui.tracksLabel

/**
 * Screen 02. Header, the two shuffle modes, search, the Треки/Артисты switch, sort, then the
 * list — all over the photographic ground, which the scrim hands back to `--color-bg` before the
 * rows start.
 *
 * Holding a row selects instead of opening the track card; the card is one press further, behind
 * «Инфо», which the bar offers only while exactly one row is picked.
 */
@Composable
fun LibraryScreen(
    playingTrackId: String?,
    onPlay: (Track, List<Track>) -> Unit,
    onOpenDetail: (Track) -> Unit,
    onShuffle: (mode: String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onDownload: (List<String>) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val collections by viewModel.collectionCount.collectAsStateWithLifecycle()
    val telegramLinked by viewModel.telegramLinked.collectAsStateWithLifecycle()

    val selection = rememberSelection()
    var addingToCollection by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ArtistFolder?>(null) }

    // A refresh, a delete or a rename can retire whatever was picked; the bar must not keep
    // counting rows that are no longer on screen.
    val presentKeys = when (state.view) {
        LibraryView.Tracks -> tracks.map { it.id }
        LibraryView.Artists -> artists.map { it.key }
    }
    LaunchedEffect(presentKeys) { selection.retainAll(presentKeys) }

    /** What the bar acts on: the picked tracks, or every track inside the picked folders. */
    val selectedTracks: List<Track> = when (state.view) {
        LibraryView.Tracks -> tracks.filter { selection.contains(it.id) }
        LibraryView.Artists -> artists.filter { selection.contains(it.key) }.flatMap { it.tracks }
    }
    val selectedFolder: ArtistFolder? = artists
        .filter { selection.contains(it.key) }
        .singleOrNull()
        ?.takeIf { state.view == LibraryView.Artists }

    Box(modifier = Modifier.fillMaxSize().background(Wl.Bg)) {
        WlBackdrop(
            painter = painterResource(R.drawable.backdrop_library),
            alpha = 0.5f,
            brightness = 0.62f,
            verticalBias = -0.6f
        )

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Библиотека", style = WlType.Title, color = Wl.Text)
                        Text(
                            "${tracksLabel(tracks.size)} · " +
                                formatTotalDuration(tracks.sumOf { it.duration.toLong() }),
                            style = WlType.Meta,
                            color = Wl.text(50),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    // The mock shows no way into the levels below the library; these are it.
                    Column(horizontalAlignment = Alignment.End) {
                        LevelLink("Коллекции · $collections", onOpenCollections)
                        LevelLink("Загрузки", onOpenDownloads)
                    }
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

                WlInputButton(
                    text = "Поиск…",
                    onClick = onOpenSearch,
                    muted = true,
                    background = Wl.text(6),
                    leading = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = Wl.text(45),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                WlSegmented(
                    options = LibraryView.entries.map { it.label },
                    selected = LibraryView.entries.indexOf(state.view),
                    onSelect = {
                        // The two views key their selection differently — ids against folder
                        // names — so a switch has to drop it rather than carry it over.
                        selection.clear()
                        viewModel.setView(LibraryView.entries[it])
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.view == LibraryView.Tracks) {
                    WlSegmented(
                        options = LibrarySort.libraryOptions.map { it.label },
                        selected = LibrarySort.libraryOptions.indexOf(state.sort).coerceAtLeast(0),
                        onSelect = { viewModel.setSort(LibrarySort.libraryOptions[it]) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            state.message?.let { Notice(it, error = state.messageError) }
            if (state.offline) Notice("Нет сети — показана сохранённая библиотека")

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.view == LibraryView.Tracks && tracks.isEmpty() && !state.refreshing ->
                        Empty("Библиотека пуста")

                    state.view == LibraryView.Artists && artists.isEmpty() && !state.refreshing ->
                        Empty("Исполнителей пока нет")

                    state.view == LibraryView.Tracks -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
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

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
                    ) {
                        items(artists, key = { it.key }) { folder ->
                            ArtistRow(
                                folder = folder,
                                onOpen = { onOpenArtist(folder.name) },
                                onToggleSelect = { selection.toggle(folder.key) },
                                selectionMode = selection.active,
                                selected = selection.contains(folder.key)
                            )
                        }
                    }
                }
            }

            if (selection.active) {
                SelectionBar(
                    count = selection.count,
                    onClear = selection::clear,
                    actions = libraryActions(
                        selectedTracks = selectedTracks,
                        folder = selectedFolder,
                        telegramLinked = telegramLinked,
                        working = state.working,
                        onInfo = {
                            selectedTracks.singleOrNull()?.let(onOpenDetail)
                            selection.clear()
                        },
                        onDownload = {
                            onDownload(selectedTracks.map { it.id })
                            viewModel.note("В загрузки: ${selectedTracks.size}")
                            selection.clear()
                        },
                        onAddToCollection = { addingToCollection = true },
                        onSendToTelegram = {
                            viewModel.sendToTelegram(selectedTracks)
                            selection.clear()
                        },
                        onDelete = { confirmingDelete = true },
                        onRename = { renaming = selectedFolder }
                    )
                )
            }
        }
    }

    if (addingToCollection) {
        AddToCollectionDialog(
            trackIds = selectedTracks.map { it.id },
            onDismiss = { addingToCollection = false },
            // Cancelling must not throw the selection away; only a completed add does.
            onResult = { message -> viewModel.note(message); selection.clear() }
        )
    }

    if (confirmingDelete) {
        val owned = selectedTracks.count { it.isOwned }
        val saved = selectedTracks.size - owned
        ConfirmDialog(
            title = "Удалить ${tracksLabel(selectedTracks.size)}?",
            message = buildString {
                if (owned > 0) append("Своих будет удалено безвозвратно: $owned. ")
                if (saved > 0) append("Сохранённых уберём только из вашей библиотеки: $saved.")
            }.trim(),
            onConfirm = {
                viewModel.deleteTracks(selectedTracks)
                selection.clear()
            },
            onDismiss = { confirmingDelete = false }
        )
    }

    renaming?.let { folder ->
        RenameArtistDialog(
            folder = folder,
            onDismiss = { renaming = null },
            // Renaming from the list stays on the list — the folder simply reappears under its
            // new name once the library comes back. Only the artist screen has to follow it,
            // because its route is keyed by the name that just changed.
            onConfirm = { newName -> viewModel.renameArtist(folder.tracks, newName); selection.clear() }
        )
    }
}

/**
 * The bar the library offers over a selection. «Инфо» stands in for the hold that used to open
 * the track card, and «Переименовать» only means anything for one folder at a time.
 */
private fun libraryActions(
    selectedTracks: List<Track>,
    folder: ArtistFolder?,
    telegramLinked: Boolean,
    working: Boolean,
    onInfo: () -> Unit,
    onDownload: () -> Unit,
    onAddToCollection: () -> Unit,
    onSendToTelegram: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
): List<SelectionAction> = buildList {
    if (folder != null) {
        add(SelectionAction("Имя", Icons.Filled.Edit, enabled = !working, onClick = onRename))
    } else if (selectedTracks.size == 1) {
        add(SelectionAction("Инфо", Icons.Filled.Info, onClick = onInfo))
    }
    add(SelectionAction("Скачать", Icons.Filled.Download, onClick = onDownload))
    add(SelectionAction("Коллекция", Icons.Filled.PlaylistAdd, enabled = !working, onClick = onAddToCollection))
    if (telegramLinked) {
        add(SelectionAction("Telegram", Icons.Filled.Send, enabled = !working, onClick = onSendToTelegram))
    }
    add(SelectionAction("Удалить", Icons.Filled.Delete, enabled = !working, onClick = onDelete))
}

@Composable
internal fun RenameArtistDialog(
    folder: ArtistFolder,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(folder.key) { mutableStateOf(folder.name) }
    val skipped = folder.trackCount - folder.ownedCount

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Wl.Surface,
        title = { Text("Переименовать исполнителя", style = WlType.Heading, color = Wl.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WlInput(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Исполнитель",
                    modifier = Modifier.fillMaxWidth()
                )
                // Counts go after a colon on purpose: «Изменится у 1 трек» would need the
                // genitive, and tracksLabel only knows the nominative.
                Text(
                    "Затронет ваших треков: ${folder.ownedCount}." +
                        if (skipped > 0) " Сохранённых из банка не тронем: $skipped." else "",
                    style = WlType.Micro,
                    color = Wl.text(50)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && name.trim() != folder.name && folder.ownedCount > 0,
                onClick = { onConfirm(name); onDismiss() }
            ) { Text("Переименовать", color = Wl.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Wl.text(60))
            }
        }
    )
}

@Composable
private fun Empty(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = WlType.BodySm, color = Wl.text(45))
    }
}

@Composable
private fun LevelLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = WlType.Meta,
        color = Wl.Accent,
        modifier = Modifier
            .clip(Wl.RadiusSm)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
internal fun Notice(text: String, error: Boolean = false) {
    Text(
        text,
        style = WlType.Meta,
        color = if (error) Wl.Accent300 else Wl.text(50),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}
