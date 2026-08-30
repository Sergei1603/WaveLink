package ru.wavelink.app.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wavelink.app.ui.formatTotalDuration
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType
import ru.wavelink.app.ui.tracksLabel

/** One artist folder in the library's Артисты view: name over «N треков · время», then a chevron. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistRow(
    folder: ArtistFolder,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(Wl.RadiusMd)
            .background(if (selected) Wl.accent(16) else Color.Transparent)
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                onLongClick = onToggleSelect
            )
            .heightIn(min = 58.dp)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.CenterEnd) {
            Icon(
                if (selectionMode && selected) Icons.Filled.CheckCircle else Icons.Filled.Folder,
                contentDescription = null,
                tint = if (selected) Wl.Accent else Wl.text(45),
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                folder.name,
                style = WlType.RowTitle,
                color = Wl.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${tracksLabel(folder.trackCount)} · ${formatTotalDuration(folder.totalDuration)}",
                style = WlType.Meta,
                color = Wl.text(52),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text("›", style = WlType.Body, color = Wl.text(35))
    }
}
