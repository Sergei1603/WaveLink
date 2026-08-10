package ru.wavelink.app.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wavelink.app.core.model.parseInstant
import ru.wavelink.app.ui.formatDate
import ru.wavelink.app.ui.formatDuration
import ru.wavelink.app.ui.formatListened
import ru.wavelink.app.ui.formatSize

/**
 * The track card from Планы.md: who added it, when, total plays across everyone, your own plays
 * and whether you ever heard it to the end.
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
    val sheetState = rememberModalBottomSheetState()

    androidx.compose.runtime.LaunchedEffect(trackId) { viewModel.load(trackId) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            val detail = state.detail
            if (detail == null) {
                Text("Загрузка…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(detail.track.title, style = MaterialTheme.typography.titleLarge)

                DetailRow("Исполнитель", detail.track.artist)
                DetailRow("Кто добавил", "@${detail.track.uploaderUsername}")
                DetailRow("Когда добавлен", formatDate(parseInstant(detail.track.uploadedAt)))
                DetailRow("Длительность", formatDuration(detail.track.duration))
                DetailRow("Размер", formatSize(detail.track.fileSize))

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Прослушивания", style = MaterialTheme.typography.titleMedium)

                DetailRow("Всего", detail.stats.totalPlays.toString())
                DetailRow("Разных слушателей", detail.stats.distinctListeners.toString())
                DetailRow("Твоих", detail.stats.myPlays.toString())
                DetailRow("Ты слушал", formatListened(detail.stats.myListenedSeconds))
                DetailRow("Последний раз", formatDate(parseInstant(detail.stats.myLastPlayedAt)))
                DetailRow("Дослушан до конца", if (detail.stats.myCompleted) "да" else "нет")

                Text(
                    "Прослушивание засчитывается с 60% трека или 2 минут — перемотка не считается.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.downloaded) {
                        OutlinedButton(onClick = onRemoveDownload, modifier = Modifier.weight(1f)) {
                            Text("Удалить загрузку")
                        }
                    } else {
                        Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                            Text("Слушать офлайн")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
