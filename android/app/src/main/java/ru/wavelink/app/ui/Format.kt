package ru.wavelink.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ru = Locale("ru")
private val DateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Ru).withZone(ZoneId.systemDefault())
private val TimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Ru).withZone(ZoneId.systemDefault())

fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "—"
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

/** The player's middle timing, e.g. `−2:36`. */
fun formatRemaining(seconds: Int): String =
    if (seconds <= 0) "−0:00" else "−" + formatDuration(seconds)

fun formatListened(seconds: Long): String = when {
    seconds <= 0 -> "—"
    seconds < 60 -> "$seconds сек"
    seconds < 3600 -> "${seconds / 60} мин"
    else -> "${seconds / 3600} ч ${(seconds % 3600) / 60} мин"
}

/** Library and collection totals: `6 ч 42 мин`, or just minutes below the hour. */
fun formatTotalDuration(seconds: Long): String = when {
    seconds <= 0 -> "0 мин"
    seconds < 3600 -> "${seconds / 60} мин"
    else -> "${seconds / 3600} ч ${(seconds % 3600) / 60} мин"
}

fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes < 1024 * 1024 -> "%.0f КБ".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f МБ".format(bytes / 1024.0 / 1024.0)
    else -> "%.1f ГБ".format(bytes / 1024.0 / 1024.0 / 1024.0)
}

/** Whole megabytes — the storage figures on the downloads screen and the cache-limit row. */
fun formatMb(bytes: Long): String = "${bytes / 1024 / 1024} МБ"

fun formatDate(instant: Instant?): String = instant?.let(DateFormat::format) ?: "—"

/** `сегодня, 21:04` while it is recent enough to mean something, an absolute date after that. */
fun formatWhen(instant: Instant?): String {
    if (instant == null) return "—"
    val zone = ZoneId.systemDefault()
    val day = instant.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (day) {
        today -> "сегодня, " + TimeFormat.format(instant)
        today.minusDays(1) -> "вчера, " + TimeFormat.format(instant)
        else -> DateFormat.format(instant)
    }
}

/**
 * Russian needs three forms, and the counts in this UI are user-visible everywhere
 * (`1 трек` / `2 трека` / `5 треков`), so picking the right one is not optional.
 */
fun plural(count: Int, one: String, few: String, many: String): String {
    val n = kotlin.math.abs(count) % 100
    val n1 = n % 10
    return when {
        n in 11..19 -> many
        n1 == 1 -> one
        n1 in 2..4 -> few
        else -> many
    }
}

fun tracksLabel(count: Int): String = "$count " + plural(count, "трек", "трека", "треков")
fun collectionsLabel(count: Int): String = "$count " + plural(count, "подборка", "подборки", "подборок")
fun playsLabel(count: Int): String = "$count " + plural(count, "прослушивание", "прослушивания", "прослушиваний")
fun positionsLabel(count: Int): String = "$count " + plural(count, "позиция", "позиции", "позиций")
fun listenersLabel(count: Int): String = "$count " + plural(count, "слушатель", "слушателя", "слушателей")
fun artistsLabel(count: Int): String = "$count " + plural(count, "исполнитель", "исполнителя", "исполнителей")

/** `audio/mpeg` → `mp3`, for the track card's format tag. */
fun formatMime(mimeType: String): String = when (mimeType.lowercase()) {
    "audio/mpeg" -> "mp3"
    "audio/flac", "audio/x-flac" -> "flac"
    "audio/wav", "audio/x-wav" -> "wav"
    "audio/ogg", "audio/vorbis" -> "ogg"
    else -> mimeType.substringAfter('/').ifBlank { mimeType }
}
