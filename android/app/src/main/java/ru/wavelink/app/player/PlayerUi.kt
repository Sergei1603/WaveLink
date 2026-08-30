package ru.wavelink.app.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wavelink.app.R
import ru.wavelink.app.collections.AddToCollectionDialog
import ru.wavelink.app.ui.HairlineProgress
import ru.wavelink.app.ui.PlayPauseIcon
import ru.wavelink.app.ui.components.WlBackdrop
import ru.wavelink.app.ui.components.WlIconButton
import ru.wavelink.app.ui.components.WlRoundButton
import ru.wavelink.app.ui.components.WlStackedButton
import ru.wavelink.app.ui.components.WlTag
import ru.wavelink.app.ui.components.WlTagStyle
import ru.wavelink.app.ui.components.WlWaveMark
import ru.wavelink.app.ui.components.fadingRule
import ru.wavelink.app.ui.formatDuration
import ru.wavelink.app.ui.formatRemaining
import ru.wavelink.app.ui.formatSize
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType
import ru.wavelink.app.ui.tracksLabel

/** How far the ⟲/⟳ buttons jump — the figure the design prints inside the glyph. */
private const val SKIP_SECONDS = 15

/** How much of «Далее» is drawn at once, and how much one tap on «Показать ещё» adds. */
private const val QUEUE_VISIBLE_INITIAL = 12
private const val QUEUE_PAGE_STEP = 20

/**
 * The mini-player from screen 02: a seam of progress, the wave mark standing in for cover art,
 * what is playing, and one button. Tapping the row opens the full player.
 */
@Composable
fun NowPlayingBar(
    state: PlayerUiState,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.trackId == null) return

    Column(modifier = modifier.fillMaxWidth()) {
        HairlineProgress(state.progress())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(Wl.RadiusSm)
                    .background(Wl.accent(16)),
                contentAlignment = Alignment.Center
            ) {
                WlWaveMark(seed = 1, bars = 3, maxHeight = 16.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.title,
                    style = WlType.BodySm.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                    color = Wl.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${state.artist} · ${formatDuration((state.positionMs / 1000).toInt())} / " +
                        formatDuration((state.durationMs / 1000).toInt()),
                    style = WlType.Meta,
                    color = Wl.text(52),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            WlRoundButton(onClick = onToggle, size = 44.dp, modifier = Modifier.padding(end = 8.dp)) {
                PlayPauseIcon(state.isPlaying, Wl.Accent, 20.dp)
            }
        }
    }
}

/**
 * Screen 04. No waveform here — the design replaced it with a flat slider, because on a phone
 * a per-pixel waveform is unreadable and costs a whole extra fetch of the audio.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    onCollapse: () -> Unit,
    onOpenDetail: (String) -> Unit,
    viewModel: PlayerViewModel,
    onDownload: (String) -> Unit = {},
    onRemoveDownload: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val track by viewModel.currentTrack.collectAsStateWithLifecycle()
    val telegramLinked by viewModel.telegramLinked.collectAsStateWithLifecycle()
    // Collapsed on open: the point of arriving here is the track that is playing, not the ten
    // behind it, and an expanded list pushes the transport row off the fold on a short phone.
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    var queueVisible by remember { mutableIntStateOf(QUEUE_VISIBLE_INITIAL) }
    var addingToCollection by remember { mutableStateOf(false) }

    if (state.trackId == null) {
        Box(modifier = Modifier.fillMaxSize().background(Wl.Bg), contentAlignment = Alignment.Center) {
            Text("Ничего не играет", style = WlType.BodySm, color = Wl.text(45))
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Wl.Bg)) {
        // The mock's own figures: opacity 0.6, saturate(0.7) brightness(0.75),
        // object-position 50% 45% — which is a vertical bias of 2 × 0.45 − 1.
        WlBackdrop(
            painter = painterResource(R.drawable.backdrop_player),
            alpha = 0.6f,
            brightness = 0.75f,
            verticalBias = -0.1f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                WlIconButton(onClick = onCollapse, size = 44.dp) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Свернуть",
                        tint = Wl.Accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    listOfNotNull("Сейчас играет", state.source.ifBlank { null }).joinToString(" · ")
                        .uppercase(),
                    style = WlType.Kicker,
                    color = Wl.text(45),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp)
                )
                WlIconButton(
                    onClick = { state.trackId?.let(onOpenDetail) },
                    size = 44.dp
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "Подробнее о треке",
                        tint = Wl.Accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 18.dp),
                // Anchored to the top, not the bottom: with «Далее» collapsed a bottom-anchored
                // column would leave the transport row hugging the navigation bar.
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.padding(top = 32.dp)) {
                    Text(state.title, style = WlType.Title, color = Wl.Text)
                    Text(
                        buildString {
                            append(state.artist)
                            track?.let { append(" · @").append(it.uploaderUsername) }
                        },
                        style = WlType.Body,
                        color = Wl.text(55),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        track?.let { t ->
                            if (t.isPublic) WlTag("Public", style = WlTagStyle.Accent)
                            if (t.myPlays > 0) WlTag("▶ ${t.myPlays}", style = WlTagStyle.Neutral)
                            if (t.downloadState != null) {
                                WlTag("Загружен · ${formatSize(t.fileSize)}", style = WlTagStyle.Neutral)
                            }
                        }
                    }
                }

                Column {
                    WlSlider(
                        fraction = state.progress(),
                        onSeek = { f -> viewModel.seekTo((f * state.durationMs).toLong()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val position = (state.positionMs / 1000).toInt()
                        val total = (state.durationMs / 1000).toInt()
                        Text(formatDuration(position), style = WlType.Meta, color = Wl.Accent300)
                        Text(formatRemaining(total - position), style = WlType.Meta, color = Wl.text(50))
                        Text(formatDuration(total), style = WlType.Meta, color = Wl.text(50))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SkipButton(back = true) { viewModel.seekBy(-SKIP_SECONDS * 1000L) }
                    WlIconButton(
                        onClick = viewModel::previous,
                        size = 64.dp,
                        enabled = state.hasPrevious
                    ) {
                        Icon(
                            Icons.Filled.SkipPrevious,
                            contentDescription = "Предыдущий",
                            tint = if (state.hasPrevious) Wl.Text else Wl.text(30),
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    WlRoundButton(onClick = viewModel::togglePlayPause, size = 88.dp) {
                        PlayPauseIcon(state.isPlaying, Wl.Accent, 40.dp)
                    }
                    WlIconButton(onClick = viewModel::next, size = 64.dp, enabled = state.hasNext) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "Следующий",
                            tint = if (state.hasNext) Wl.Text else Wl.text(30),
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    SkipButton(back = false) { viewModel.seekBy(SKIP_SECONDS * 1000L) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().fadingRule(top = true).padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val downloaded = track?.downloadState != null
                    WlStackedButton(
                        label = if (downloaded) "Скачано" else "Скачать",
                        color = if (downloaded) Wl.Accent else Wl.text(70),
                        onClick = {
                            val id = state.trackId ?: return@WlStackedButton
                            if (downloaded) onRemoveDownload(id) else onDownload(id)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (downloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                            contentDescription = null,
                            tint = LocalContentColor.current,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    WlStackedButton(
                        label = "В коллекцию",
                        onClick = { addingToCollection = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = LocalContentColor.current,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    WlStackedButton(
                        label = "В Telegram",
                        color = if (telegramLinked) Wl.text(70) else Wl.text(30),
                        onClick = { if (telegramLinked) viewModel.sendCurrentToTelegram() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = null,
                            tint = LocalContentColor.current,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().fadingRule(top = true).padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.clickable { queueOpen = !queueOpen },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "ДАЛЕЕ · ${tracksLabel(state.upcomingTotal)}".uppercase(),
                                style = WlType.Kicker,
                                color = Wl.text(45)
                            )
                            Text(
                                if (queueOpen) "⌄" else "›",
                                style = WlType.Caption,
                                color = Wl.text(45)
                            )
                        }
                        if (state.upcoming.isNotEmpty()) {
                            Text(
                                "Очистить",
                                style = WlType.Meta,
                                color = Wl.Accent,
                                modifier = Modifier
                                    .clickable(onClick = viewModel::clearUpcoming)
                                    .padding(6.dp)
                            )
                        }
                    }
                    if (queueOpen) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.upcoming.take(queueVisible).forEachIndexed { position, entry ->
                                // The same contract as every other row in the app: tap plays it,
                                // holding opens its card.
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(Wl.RadiusMd)
                                        .combinedClickable(
                                            onClick = { viewModel.playQueueItem(entry) },
                                            onLongClick = { onOpenDetail(entry.id) }
                                        )
                                        .heightIn(min = 44.dp)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        "%02d".format(position + 1),
                                        style = WlType.Meta,
                                        color = Wl.text(35),
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            entry.title,
                                            style = WlType.BodySm,
                                            color = Wl.Text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            entry.artist,
                                            style = WlType.Micro,
                                            color = Wl.text(48),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        formatDuration(entry.durationSeconds),
                                        style = WlType.Meta,
                                        color = Wl.text(40)
                                    )
                                }
                            }

                            // This screen is one big verticalScroll, so a LazyColumn cannot go
                            // here — the list grows on demand instead. Fetching the next page is
                            // driven by playback, not by this button.
                            if (state.upcoming.size > queueVisible) {
                                Text(
                                    "Показать ещё",
                                    style = WlType.Meta,
                                    color = Wl.Accent,
                                    modifier = Modifier
                                        .clickable { queueVisible += QUEUE_PAGE_STEP }
                                        .padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (addingToCollection) {
        val id = state.trackId
        if (id == null) addingToCollection = false
        else AddToCollectionDialog(
            trackIds = listOf(id),
            onDismiss = { addingToCollection = false },
            onResult = { }
        )
    }
}

/** ⟲ / ⟳ with the jump printed inside, the way the mock draws them. */
@Composable
private fun SkipButton(back: Boolean, onClick: () -> Unit) {
    WlIconButton(onClick = onClick, size = 56.dp) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Replay,
                contentDescription = if (back) "Назад на $SKIP_SECONDS секунд" else "Вперёд на $SKIP_SECONDS секунд",
                tint = Wl.text(60),
                modifier = Modifier
                    .size(28.dp)
                    .scale(scaleX = if (back) 1f else -1f, scaleY = 1f)
            )
            Text(
                SKIP_SECONDS.toString(),
                style = WlType.Micro.copy(fontSize = 9.sp),
                color = Wl.text(60),
                modifier = Modifier.offset(y = 1.dp)
            )
        }
    }
}

/**
 * The flat scrubber: neutral track, accent fill, an accent-200 handle. Written by hand because
 * Material's Slider brings a tick rail, a ripple and a 48dp thumb this design does not use.
 */
@Composable
private fun WlSlider(
    fraction: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    barHeight: Dp = 4.dp,
    thumbSize: Dp = 14.dp
) {
    var widthPx by remember { mutableStateOf(1) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = (dragging ?: fraction).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(36.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { offset -> onSeek((offset.x / widthPx).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> dragging = (offset.x / widthPx).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, delta ->
                        dragging = ((dragging ?: shown) + delta / widthPx).coerceIn(0f, 1f)
                        change.consume()
                    },
                    onDragEnd = { dragging?.let(onSeek); dragging = null },
                    onDragCancel = { dragging = null }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(barHeight / 2))
                .background(Wl.Neutral800)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(shown)
                    .clip(RoundedCornerShape(barHeight / 2))
                    .background(Wl.Accent)
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(shown)
                    .height(thumbSize),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(thumbSize)
                        .offset(x = thumbSize / 2)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Wl.Accent200)
                )
            }
        }
    }
}

private fun PlayerUiState.progress(): Float =
    if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
