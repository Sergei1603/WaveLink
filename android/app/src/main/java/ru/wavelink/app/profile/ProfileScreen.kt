package ru.wavelink.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wavelink.app.core.prefs.SettingsStore
import ru.wavelink.app.ui.components.WlButton
import ru.wavelink.app.ui.components.WlButtonStyle
import ru.wavelink.app.ui.components.WlInput
import ru.wavelink.app.ui.components.WlListRow
import ru.wavelink.app.ui.components.WlToggle
import ru.wavelink.app.ui.formatMb
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType
import ru.wavelink.app.ui.tracksLabel

/**
 * Screen 09. The account, the two levels that hang off it, and the settings — including the one
 * thing the app deliberately cannot do: pair a Telegram chat. That stays on the web and in the bot.
 */
@Composable
fun ProfileScreen(
    onOpenStats: () -> Unit,
    onOpenDownloads: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingServer by remember { mutableStateOf(false) }
    var editingCache by remember { mutableStateOf(false) }
    var confirmingSignOut by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Wl.Bg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Wl.Accent800),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        state.username?.firstOrNull()?.uppercase() ?: "?",
                        style = WlType.Heading,
                        color = Wl.Accent200
                    )
                }
                Column {
                    Text("@${state.username ?: "…"}", style = WlType.Heading, color = Wl.Text)
                    Text(
                        "${tracksLabel(state.trackCount)} · ${state.publishedCount} в общем банке",
                        style = WlType.Caption,
                        color = Wl.text(52)
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WlListRow(
                    title = "Статистика",
                    hint = "Что вы слушали и сколько",
                    filled = true,
                    onClick = onOpenStats,
                    trailing = { Text("›", style = WlType.Body, color = Wl.text(40)) }
                )
                WlListRow(
                    title = "Загрузки",
                    hint = "Треки, доступные офлайн",
                    filled = true,
                    onClick = onOpenDownloads,
                    trailing = { Text("›", style = WlType.Body, color = Wl.text(40)) }
                )
            }
        }

        item { TelegramCard(botEnabled = state.telegramBotEnabled, linked = state.telegramLinked) }

        item {
            Column {
                WlListRow(
                    title = "Адрес сервера",
                    hint = "Применится после перезапуска",
                    ruled = true,
                    onClick = { editingServer = true },
                    trailing = {
                        Text(
                            state.baseUrl.removePrefix("http://").removePrefix("https://").trimEnd('/'),
                            style = WlType.Caption,
                            color = Wl.text(55),
                            maxLines = 1
                        )
                    }
                )
                WlListRow(
                    title = "Лимит кэша стриминга",
                    hint = "Скачанное не вытесняется",
                    ruled = true,
                    onClick = { editingCache = true },
                    trailing = {
                        Text(formatMb(state.cacheBytes), style = WlType.Caption, color = Wl.text(55))
                    }
                )
                WlListRow(
                    title = "Скачивать только по Wi-Fi",
                    hint = "Не тратить мобильный трафик",
                    ruled = true,
                    trailing = {
                        WlToggle(state.wifiOnlyDownloads, viewModel::setWifiOnlyDownloads)
                    }
                )
                WlListRow(
                    title = "Фоновое воспроизведение",
                    hint = "Уведомление с управлением",
                    ruled = true,
                    trailing = {
                        WlToggle(state.backgroundPlayback, viewModel::setBackgroundPlayback)
                    }
                )
                WlButton(
                    text = "Выйти",
                    onClick = { confirmingSignOut = true },
                    style = WlButtonStyle.Ghost,
                    color = Wl.Accent300,
                    minHeight = 48.dp,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }
    }

    if (editingServer) {
        TextDialog(
            title = "Адрес сервера",
            initial = state.baseUrl,
            hint = "Применится после перезапуска приложения.",
            keyboardType = KeyboardType.Uri,
            onDismiss = { editingServer = false },
            onSave = { viewModel.setBaseUrl(it); editingServer = false }
        )
    }

    if (editingCache) {
        CacheDialog(
            currentBytes = state.cacheBytes,
            onDismiss = { editingCache = false },
            onSave = { viewModel.setCacheMb(it); editingCache = false }
        )
    }

    if (confirmingSignOut) {
        AlertDialog(
            onDismissRequest = { confirmingSignOut = false },
            containerColor = Wl.Surface,
            title = { Text("Выйти из аккаунта?", style = WlType.Heading, color = Wl.Text) },
            text = {
                Text(
                    "Скачанные файлы останутся на устройстве, но библиотека станет " +
                        "недоступна до следующего входа.",
                    style = WlType.BodySm,
                    color = Wl.text(60)
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmingSignOut = false; onSignOut() }) {
                    Text("Выйти", color = Wl.Accent300)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSignOut = false }) { Text("Отмена", color = Wl.text(60)) }
            }
        )
    }
}

@Composable
private fun TelegramCard(botEnabled: Boolean?, linked: Boolean) {
    val title = when {
        botEnabled == null -> "Telegram"
        linked -> "Telegram привязан"
        else -> "Telegram не привязан"
    }
    val message = when {
        botEnabled == null -> "Состояние неизвестно — нет связи с сервером."
        !botEnabled -> "Бот выключен на сервере. Отправка треков в чат недоступна."
        linked -> "Привязка и загрузка треков — в веб-версии и боте. " +
            "Здесь можно отправлять треки в чат."
        else -> "Чат не привязан. Привяжите его командой /link в боте — " +
            "после этого появится отправка треков."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Wl.RadiusMd)
            .background(if (linked) Wl.accent(10) else Wl.text(5))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Filled.Send,
            contentDescription = null,
            tint = if (linked) Wl.Accent else Wl.text(40),
            modifier = Modifier.padding(top = 2.dp).size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = WlType.BodySm, color = Wl.Text)
            Text(message, style = WlType.Meta, color = Wl.text(52))
        }
    }
}

@Composable
private fun TextDialog(
    title: String,
    initial: String,
    hint: String,
    keyboardType: KeyboardType,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Wl.Surface,
        title = { Text(title, style = WlType.Heading, color = Wl.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WlInput(
                    value = value,
                    onValueChange = { value = it },
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(hint, style = WlType.Micro, color = Wl.text(50))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Сохранить", color = Wl.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Wl.text(60)) } }
    )
}

@Composable
private fun CacheDialog(currentBytes: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val minMb = SettingsStore.MIN_CACHE_BYTES / 1024 / 1024
    val maxMb = SettingsStore.MAX_CACHE_BYTES / 1024 / 1024
    var mb by remember(currentBytes) { mutableStateOf((currentBytes / 1024 / 1024).toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Wl.Surface,
        title = { Text("Лимит кэша стриминга", style = WlType.Heading, color = Wl.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${mb.toLong()} МБ", style = WlType.Numeric, color = Wl.Text)
                Slider(
                    value = mb,
                    onValueChange = { mb = it },
                    valueRange = minMb.toFloat()..maxMb.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Wl.Accent200,
                        activeTrackColor = Wl.Accent,
                        inactiveTrackColor = Wl.Neutral800
                    )
                )
                Text(
                    "Скачанные для офлайна треки закреплены и под этот лимит не попадают.",
                    style = WlType.Micro,
                    color = Wl.text(50)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(mb.toLong()) }) { Text("Сохранить", color = Wl.Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Wl.text(60)) } }
    )
}
