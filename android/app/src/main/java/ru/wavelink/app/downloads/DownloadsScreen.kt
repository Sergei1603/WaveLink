package ru.wavelink.app.downloads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wavelink.app.ui.components.WlButton
import ru.wavelink.app.ui.components.WlMeter
import ru.wavelink.app.ui.components.WlScreenHeader
import ru.wavelink.app.ui.components.WlSplitMeter
import ru.wavelink.app.ui.components.fadingRule
import ru.wavelink.app.ui.formatMb
import ru.wavelink.app.ui.formatSize
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType
import ru.wavelink.app.ui.tracksLabel

/**
 * Screen 07. Two kinds of bytes live in one cache and the screen has to keep them apart:
 * downloads are pinned, the streaming cache is evictable, and only the second one has a limit.
 */
@Composable
fun DownloadsScreen(
    parentLabel: String,
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    var confirmingClear by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Wl.Bg).statusBarsPadding()) {
        WlScreenHeader(parent = parentLabel, onBack = onBack)

        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text("Загрузки", style = WlType.Title, color = Wl.Text)
                Text(
                    "${tracksLabel(items.size)} закреплено · ${formatMb(storage.pinnedBytes)}",
                    style = WlType.Meta,
                    color = Wl.text(50),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // The bar is drawn against whichever is larger: the limit, or what is actually
                // on disk — otherwise pinned downloads could overflow a bar they do not obey.
                val scale = maxOf(
                    storage.limitBytes,
                    storage.pinnedBytes + storage.streamCacheBytes,
                    1L
                ).toFloat()
                WlSplitMeter(
                    first = storage.pinnedBytes / scale,
                    second = storage.streamCacheBytes / scale,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${formatMb(storage.pinnedBytes)} закреплено · " +
                            "${formatMb(storage.streamCacheBytes)} кэш стриминга",
                        style = WlType.Micro,
                        color = Wl.text(45)
                    )
                    Text(
                        "Лимит ${formatMb(storage.limitBytes)}",
                        style = WlType.Micro,
                        color = Wl.Accent300
                    )
                }
                Text(
                    "Скачанное не вытесняется — лимит касается только кэша стриминга.",
                    style = WlType.Micro,
                    color = Wl.text(38)
                )
            }
        }

        if (items.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Ничего не скачано.\nОткройте карточку трека и нажмите «Скачать».",
                    style = WlType.BodySm,
                    color = Wl.text(45)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(items, key = { it.trackId }) { item ->
                    DownloadRow(item = item, onRemove = { viewModel.remove(item.trackId) })
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fadingRule(top = true, inset = 0.dp)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)
        ) {
            WlButton(
                text = "Очистить кэш стриминга",
                onClick = { confirmingClear = true },
                minHeight = 44.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            containerColor = Wl.Surface,
            title = { Text("Очистить кэш стриминга?", style = WlType.Heading, color = Wl.Text) },
            text = {
                Text(
                    "Скачанные треки останутся на месте. Освободится " +
                        "${formatMb(storage.streamCacheBytes)}; всё остальное придётся " +
                        "загружать заново при следующем прослушивании.",
                    style = WlType.BodySm,
                    color = Wl.text(60)
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearStreamCache(); confirmingClear = false }) {
                    Text("Очистить", color = Wl.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) { Text("Отмена", color = Wl.text(60)) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadRow(item: DownloadItem, onRemove: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(Wl.RadiusMd)
            .combinedClickable(onClick = {}, onLongClick = { confirming = true })
            .heightIn(min = 58.dp)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Wl.accent(14)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Download,
                contentDescription = null,
                tint = Wl.Accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = WlType.RowTitle,
                color = Wl.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(item.artist, item.stateLabel).filter { it.isNotBlank() }.joinToString(" · "),
                style = WlType.Meta,
                color = Wl.text(52),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.inProgress) {
                WlMeter(
                    fraction = item.percent / 100f,
                    height = 3.dp,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
        }
        Text(formatSize(item.bytesDownloaded), style = WlType.Meta, color = Wl.text(40))
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = Wl.Surface,
            title = { Text("Удалить загрузку?", style = WlType.Heading, color = Wl.Text) },
            text = {
                Text(
                    "«${item.title}» останется в библиотеке, но будет играть только по сети.",
                    style = WlType.BodySm,
                    color = Wl.text(60)
                )
            },
            confirmButton = {
                TextButton(onClick = { onRemove(); confirming = false }) {
                    Text("Удалить", color = Wl.Accent300)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Отмена", color = Wl.text(60)) }
            }
        )
    }
}
