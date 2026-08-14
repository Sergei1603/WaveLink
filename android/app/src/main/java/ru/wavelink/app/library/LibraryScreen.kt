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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wavelink.app.R
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.ui.TrackRow
import ru.wavelink.app.ui.components.WlBackdrop
import ru.wavelink.app.ui.components.WlButton
import ru.wavelink.app.ui.components.WlInputButton
import ru.wavelink.app.ui.components.WlSegmented
import ru.wavelink.app.ui.formatTotalDuration
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType
import ru.wavelink.app.ui.tracksLabel

/**
 * Screen 02. Header, the two shuffle modes, search, sort, then the list — all over the
 * photographic ground, which the scrim hands back to `--color-bg` before the rows start.
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
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val collections by viewModel.collectionCount.collectAsStateWithLifecycle()

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
                    options = LibrarySort.entries.map { it.label },
                    selected = LibrarySort.entries.indexOf(state.sort),
                    onSelect = { viewModel.setSort(LibrarySort.entries[it]) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            state.message?.let { Notice(it, error = true) }
            if (state.offline) Notice("Нет сети — показана сохранённая библиотека")

            if (tracks.isEmpty() && !state.refreshing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Библиотека пуста", style = WlType.BodySm, color = Wl.text(45))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
                ) {
                    itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                        TrackRow(
                            track = track,
                            index = index,
                            playing = track.id == playingTrackId,
                            onPlay = { onPlay(track, tracks) },
                            onOpenDetail = { onOpenDetail(track) }
                        )
                    }
                }
            }
        }
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
