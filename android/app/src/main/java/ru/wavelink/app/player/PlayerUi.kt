package ru.wavelink.app.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wavelink.app.ui.formatDuration

@Composable
fun NowPlayingBar(
    state: PlayerUiState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.trackId == null) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp
    ) {
        Column {
            LinearProgressIndicator(
                progress = { if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        state.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Пауза" else "Играть"
                    )
                }
                IconButton(onClick = onNext, enabled = state.hasNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Следующий")
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.trackId == null) {
            Text("Ничего не играет", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        Text(state.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
        Text(
            state.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Slider(
            value = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
            onValueChange = { fraction ->
                if (state.durationMs > 0) onSeek((fraction * state.durationMs).toLong())
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatDuration((state.positionMs / 1000).toInt()),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                formatDuration((state.durationMs / 1000).toInt()),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious, enabled = state.hasPrevious) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Предыдущий", modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Пауза" else "Играть",
                    modifier = Modifier.size(48.dp)
                )
            }
            IconButton(onClick = onNext, enabled = state.hasNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Следующий", modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Filled.Close, contentDescription = "Остановить")
            }
        }
    }
}
