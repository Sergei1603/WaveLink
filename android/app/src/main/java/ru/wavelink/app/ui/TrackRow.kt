package ru.wavelink.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wavelink.app.core.model.Track

@Composable
fun TrackRow(
    track: Track,
    onPlay: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Воспроизвести",
            tint = MaterialTheme.colorScheme.primary
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(track.artist)
                    append(" · @").append(track.uploaderUsername)
                    if (track.myPlays > 0) append(" · ▶ ").append(track.myPlays)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (track.downloadState != null) {
            Icon(
                Icons.Filled.DownloadDone,
                contentDescription = "Доступен офлайн",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            formatDuration(track.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        IconButton(onClick = onOpenDetail) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Подробнее о треке")
        }
    }
}
