package ru.wavelink.app.publicbank

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wavelink.app.R
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.library.LibrarySort
import ru.wavelink.app.ui.components.WlBackdrop
import ru.wavelink.app.ui.components.WlButton
import ru.wavelink.app.ui.components.WlInput
import ru.wavelink.app.ui.components.WlSegmented
import ru.wavelink.app.ui.formatDuration
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType

/**
 * Screen 05. Tracks other people published. Saving one puts it in your library without copying
 * the file, which is why the row's only action is «Сохранить» and never a download.
 */
@Composable
fun PublicBankScreen(
    playingTrackId: String?,
    onPlay: (Track, List<Track>) -> Unit,
    onOpenDetail: (Track) -> Unit,
    viewModel: PublicBankViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val libraryIds by viewModel.libraryIds.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(Wl.Bg)) {
        // The mock's own figures: opacity 0.6, saturate(0.7) brightness(0.75),
        // object-position 50% 18% — which is a vertical bias of 2 × 0.18 − 1.
        WlBackdrop(
            painter = painterResource(R.drawable.backdrop_bank),
            alpha = 0.6f,
            brightness = 0.75f,
            verticalBias = -0.64f
        )

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Общий банк", style = WlType.Title, color = Wl.Text)
                Text(
                    "Треки, опубликованные другими пользователями. " +
                        "Сохранённое попадает в вашу библиотеку.",
                    style = WlType.Caption,
                    color = Wl.text(52)
                )
                WlInput(
                    value = state.query,
                    onValueChange = viewModel::search,
                    placeholder = "Название или исполнитель",
                    minHeight = 44.dp,
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

            state.message?.let {
                Text(
                    it,
                    style = WlType.Meta,
                    color = Wl.Accent300,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(Wl.RadiusSm)
                )
            }

            if (state.results.isEmpty() && !state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.query.isBlank()) "В банке пока пусто" else "Ничего не найдено",
                        style = WlType.BodySm,
                        color = Wl.text(45)
                    )
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(state.results, key = { it.id }) { track ->
                    PublicRow(
                        track = track,
                        playing = track.id == playingTrackId,
                        saved = track.isOwned || track.id in libraryIds,
                        onPlay = { onPlay(track, state.results) },
                        onOpenDetail = { onOpenDetail(track) },
                        onSave = { viewModel.save(track) }
                    )
                }

                if (state.hasMore) {
                    item(key = "load-more") {
                        WlButton(
                            text = if (state.loadingMore) {
                                "Загрузка…"
                            } else {
                                "Показать ещё (${state.total - state.results.size})"
                            },
                            onClick = viewModel::loadMore,
                            enabled = !state.loadingMore,
                            minHeight = 44.dp,
                            fontSize = WlType.Caption,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PublicRow(
    track: Track,
    playing: Boolean,
    saved: Boolean,
    onPlay: () -> Unit,
    onOpenDetail: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(Wl.RadiusMd)
            .background(if (playing) Wl.accent(10) else Color.Transparent)
            .combinedClickable(onClick = onPlay, onLongClick = onOpenDetail)
            .heightIn(min = 62.dp)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = WlType.RowTitle,
                color = if (playing) Wl.Accent else Wl.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${track.artist} · @${track.uploaderUsername}",
                style = WlType.Meta,
                color = Wl.text(52),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(formatDuration(track.duration), style = WlType.Meta, color = Wl.text(42))
        if (saved) {
            Text("В библиотеке", style = WlType.Meta, color = Wl.text(40))
        } else {
            WlButton(
                text = "Сохранить",
                onClick = onSave,
                minHeight = 36.dp,
                fontSize = WlType.Caption
            )
        }
    }
}
