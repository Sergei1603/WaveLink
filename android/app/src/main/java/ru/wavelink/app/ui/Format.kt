package ru.wavelink.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale("ru")).withZone(ZoneId.systemDefault())

fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "—"
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

fun formatListened(seconds: Long): String = when {
    seconds <= 0 -> "—"
    seconds < 60 -> "$seconds сек"
    seconds < 3600 -> "${seconds / 60} мин"
    else -> "${seconds / 3600} ч ${(seconds % 3600) / 60} мин"
}

fun formatSize(bytes: Long): String =
    if (bytes < 1024 * 1024) "%.0f КБ".format(bytes / 1024.0)
    else "%.1f МБ".format(bytes / 1024.0 / 1024.0)

fun formatDate(instant: Instant?): String = instant?.let(DateFormat::format) ?: "—"
