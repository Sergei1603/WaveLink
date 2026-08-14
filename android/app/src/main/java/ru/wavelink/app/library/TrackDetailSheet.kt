package ru.wavelink.app.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wavelink.app.collections.AddToCollectionDialog
import ru.wavelink.app.ui.components.WlButton
import ru.wavelink.app.ui.components.WlButtonStyle
import ru.wavelink.app.ui.components.WlFieldLabel
import ru.wavelink.app.ui.components.WlInput
import ru.wavelink.app.ui.components.WlTag
import ru.wavelink.app.ui.components.WlTagStyle
import ru.wavelink.app.ui.components.WlToggle
import ru.wavelink.app.ui.formatDate
import ru.wavelink.app.ui.formatDuration
import ru.wavelink.app.ui.formatListened
import ru.wavelink.app.ui.formatMime
import ru.wavelink.app.ui.formatSize
import ru.wavelink.app.ui.formatWhen
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType

/**
 * Screen 12. Everything the app knows about one track, plus the edit form for tracks you own.
 *
 * The cross-user figures here are counts only — how many plays, how many distinct listeners.
 * Who those listeners were is deliberately not available from any endpoint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailSheet(
    trackId: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    viewModel: TrackDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var addingToCollection by remember { mutableStateOf(false) }

    LaunchedEffect(trackId) { viewModel.load(trackId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Wl.Surface,
        dragHandle = {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(Wl.RadiusSm)
                        .background(Wl.Neutral700)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            state.error?.let { Text(it, style = WlType.BodySm, color = Wl.Accent300) }
            state.message?.let { Text(it, style = WlType.BodySm, color = Wl.Accent300) }

            val card = state.card
            if (card == null) {
                Text("Загрузка…", style = WlType.BodySm, color = Wl.text(50))
                return@Column
            }

            Column {
                Text(card.title, style = WlType.TitleSm, color = Wl.Text)
                Text(
                    "${card.artist} · @${card.uploaderUsername}",
                    style = WlType.BodySm,
                    color = Wl.text(55),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (card.isPublic) WlTag("Public", style = WlTagStyle.Accent)
                    WlTag(
                        "${formatDuration(card.duration)} · ${formatSize(card.fileSize)} · " +
                            formatMime(card.mimeType),
                        style = WlTagStyle.Neutral
                    )
                }
            }

            // Editing writes straight to the server, so it is offered only once the server has
            // actually answered — otherwise a save would fail the moment it is tapped.
            if (card.isOwned && !state.stale) {
                EditBlock(
                    initialTitle = card.title,
                    initialArtist = card.artist,
                    initialPublic = card.isPublic,
                    saving = state.saving,
                    onSave = { t, a, p -> viewModel.save(trackId, t, a, p) }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(card.myPlays.toString(), "ваших прослушиваний", Modifier.weight(1f))
                    StatTile(
                        card.myListenedSeconds?.let(::formatListened) ?: "—",
                        "вы слушали",
                        Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        card.totalPlays?.toString() ?: "—",
                        "всего прослушиваний",
                        Modifier.weight(1f)
                    )
                    StatTile(
                        card.distinctListeners?.toString() ?: "—",
                        "разных слушателей",
                        Modifier.weight(1f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaRow("Последний раз прослушивали", formatWhen(card.myLastPlayedAt))
                MetaRow("Дослушан до конца", if (card.myCompleted) "да" else "нет")
                MetaRow("Загружен", formatDate(card.uploadedAt))
                Text(
                    "Прослушивание считается, если услышано не меньше 60% трека или 2 минут. " +
                        "Кто именно слушал — не показывается никому.",
                    style = WlType.Micro,
                    color = Wl.text(40)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WlButton(
                    text = if (state.downloaded) "Удалить загрузку" else "Скачать",
                    onClick = { if (state.downloaded) onRemoveDownload() else onDownload() },
                    minHeight = 44.dp,
                    fontSize = WlType.Caption,
                    modifier = Modifier.weight(1f)
                )
                WlButton(
                    text = "В коллекцию",
                    onClick = { addingToCollection = true },
                    minHeight = 44.dp,
                    fontSize = WlType.Caption,
                    modifier = Modifier.weight(1f)
                )
                if (state.telegramLinked) {
                    WlButton(
                        text = "В Telegram",
                        onClick = { viewModel.sendToTelegram(trackId) },
                        minHeight = 44.dp,
                        fontSize = WlType.Caption,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (addingToCollection) {
        AddToCollectionDialog(
            trackId = trackId,
            onDismiss = { addingToCollection = false },
            onResult = viewModel::note
        )
    }
}

/** Title, artist and the Public switch — the same three fields the web's edit modal offers. */
@Composable
private fun EditBlock(
    initialTitle: String,
    initialArtist: String,
    initialPublic: Boolean,
    saving: Boolean,
    onSave: (String, String, Boolean) -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var artist by remember(initialArtist) { mutableStateOf(initialArtist) }
    var isPublic by remember(initialPublic) { mutableStateOf(initialPublic) }
    val dirty = title.trim() != initialTitle || artist.trim() != initialArtist || isPublic != initialPublic

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Wl.RadiusMd)
            .background(Wl.text(4))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("РЕДАКТИРОВАТЬ ТРЕК", style = WlType.Kicker, color = Wl.text(45))
        Column {
            WlFieldLabel("Название")
            WlInput(
                value = title,
                onValueChange = { title = it },
                minHeight = 46.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Column {
            WlFieldLabel("Исполнитель")
            WlInput(
                value = artist,
                onValueChange = { artist = it },
                minHeight = 46.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Public", style = WlType.BodySm, color = Wl.Text)
                Text("Виден в общем банке", style = WlType.Micro, color = Wl.text(48))
            }
            WlToggle(checked = isPublic, onCheckedChange = { isPublic = it })
        }
        WlButton(
            text = if (saving) "Сохранение…" else "Сохранить",
            onClick = { onSave(title, artist, isPublic) },
            style = WlButtonStyle.Primary,
            enabled = dirty && !saving && title.isNotBlank() && artist.isNotBlank(),
            minHeight = 42.dp,
            fontSize = WlType.Caption,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(Wl.RadiusMd)
            .background(Wl.text(5))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(value, style = WlType.NumericSm, color = Wl.Text)
        Text(label, style = WlType.Micro, color = Wl.text(50))
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label, style = WlType.Caption, color = Wl.text(62), modifier = Modifier.weight(1f))
        Text(value, style = WlType.Caption, color = Wl.Text)
    }
}
