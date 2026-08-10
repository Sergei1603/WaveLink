package ru.wavelink.app.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local mirror of a track, so the library renders with no network at all.
 * [serverPlayCount] is overwritten from the API on every refresh; local deltas live in
 * [TrackCounterEntity] so the two never fight.
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val duration: Int,
    val fileSize: Long,
    val mimeType: String,
    val uploadedAtEpoch: Long,
    val isPublic: Boolean,
    val isOwned: Boolean,
    val uploaderUsername: String,
    val serverPlayCount: Int,
    val myLastPlayedAtEpoch: Long?,
    val myCompleted: Boolean,
    /** True for tracks in the caller's own library (as opposed to public-bank search results). */
    val inLibrary: Boolean,
    val syncedAtEpoch: Long
)

/**
 * Plays counted on the device but not yet absorbed by the server.
 * `effectiveMyPlays = tracks.serverPlayCount + track_counters.pendingPlayCount`.
 */
@Entity(tableName = "track_counters")
data class TrackCounterEntity(
    @PrimaryKey val trackId: String,
    val pendingPlayCount: Int,
    val localLastPlayedAtEpoch: Long?
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtEpoch: Long,
    val trackCount: Int
)

@Entity(
    tableName = "collection_tracks",
    primaryKeys = ["collectionId", "trackId"],
    indices = [Index("trackId")]
)
data class CollectionTrackEntity(
    val collectionId: String,
    val trackId: String,
    val position: Int
)

/** A finished listening session waiting to be flushed to `POST /api/plays`. */
@Entity(tableName = "pending_play_events")
data class PendingPlayEntity(
    @PrimaryKey val clientEventId: String,
    val trackId: String,
    val startedAtEpoch: Long,
    val listenedSeconds: Int,
    val trackDuration: Int?,
    /** Whether this event incremented a local counter, so the sync knows to decrement it. */
    val countedLocally: Boolean,
    val attempts: Int
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val trackId: String,
    /** Mirrors androidx.media3.exoplayer.offline.Download.STATE_*. */
    val state: Int,
    val bytesDownloaded: Long,
    val percent: Float,
    val requestedAtEpoch: Long
)
