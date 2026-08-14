package ru.wavelink.app.core.model

import ru.wavelink.app.core.db.TrackEntity
import ru.wavelink.app.core.db.TrackWithCounters
import ru.wavelink.app.core.net.TrackDto
import java.time.Instant
import java.time.format.DateTimeParseException

/** What the UI works with. Server counters and local pending deltas are already folded together. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Int,
    val fileSize: Long,
    val mimeType: String,
    val uploadedAt: Instant?,
    val isPublic: Boolean,
    val isOwned: Boolean,
    val uploaderUsername: String,
    /** Server-confirmed plays plus anything reported on this device but not yet synced. */
    val myPlays: Int,
    val myLastPlayedAt: Instant?,
    val myCompleted: Boolean,
    val downloadState: Int?
)

fun parseInstant(value: String?): Instant? =
    value?.let { runCatching { Instant.parse(it) }.getOrElse { _: Throwable -> null } }

fun TrackDto.toEntity(inLibrary: Boolean, now: Long): TrackEntity = TrackEntity(
    id = id,
    title = title,
    artist = artist,
    duration = duration,
    fileSize = fileSize,
    mimeType = mimeType,
    uploadedAtEpoch = parseInstant(uploadedAt)?.toEpochMilli() ?: 0L,
    isPublic = isPublic,
    isOwned = isOwned,
    uploaderUsername = uploaderUsername,
    serverPlayCount = myPlays,
    myLastPlayedAtEpoch = parseInstant(myLastPlayedAt)?.toEpochMilli(),
    myCompleted = myCompleted,
    inLibrary = inLibrary,
    syncedAtEpoch = now
)

fun TrackWithCounters.toModel(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    duration = duration,
    fileSize = fileSize,
    mimeType = mimeType,
    uploadedAt = uploadedAtEpoch.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
    isPublic = isPublic,
    isOwned = isOwned,
    uploaderUsername = uploaderUsername,
    myPlays = serverPlayCount + pendingPlayCount,
    myLastPlayedAt = myLastPlayedAtEpoch?.let(Instant::ofEpochMilli),
    myCompleted = myCompleted,
    downloadState = downloadState
)

/** For search results and shuffle queues that never touched the local database. */
fun TrackDto.toModel(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    duration = duration,
    fileSize = fileSize,
    mimeType = mimeType,
    uploadedAt = parseInstant(uploadedAt),
    isPublic = isPublic,
    isOwned = isOwned,
    uploaderUsername = uploaderUsername,
    myPlays = myPlays,
    myLastPlayedAt = parseInstant(myLastPlayedAt),
    myCompleted = myCompleted,
    downloadState = null
)
