package ru.wavelink.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.ui.components.WlTag
import ru.wavelink.app.ui.components.WlTagStyle
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType

/**
 * The library / search / collection row: ordinal, title over its subline, then whatever badges
 * apply and the duration. Tapping plays; holding opens the track card, which is the only place
 * the mock offers no dedicated control for.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    index: Int,
    onPlay: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    showPlays: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(Wl.RadiusMd)
            .background(if (playing) Wl.accent(10) else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onPlay, onLongClick = onOpenDetail)
            .heightIn(min = 58.dp)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "%02d".format(index + 1),
            style = WlType.Meta,
            color = if (playing) Wl.Accent else Wl.text(35),
            textAlign = TextAlign.End,
            modifier = Modifier.width(26.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = WlType.RowTitle,
                color = if (playing) Wl.Accent else Wl.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.subline(),
                style = WlType.Meta,
                color = Wl.text(52),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showPlays && track.myPlays > 0) {
                WlTag("▶ ${track.myPlays}", style = WlTagStyle.Neutral)
            } else {
                if (track.isPublic && track.isOwned) WlTag("Public", style = WlTagStyle.Accent)
                if (!track.isOwned) WlTag("Saved", style = WlTagStyle.Neutral)
            }
            if (track.downloadState != null) {
                Text("↓", style = WlType.Meta, color = Wl.Accent300)
            }
            Text(formatDuration(track.duration), style = WlType.Meta, color = Wl.text(42))
        }
    }
}

/** `Кассета · @anna_k · 5.4 МБ` — artist, who put it there, how big it is. */
fun Track.subline(): String = buildString {
    append(artist)
    append(" · @").append(uploaderUsername)
    if (fileSize > 0) append(" · ").append(formatSize(fileSize))
}
