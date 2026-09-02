package ru.wavelink.app.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A track plus the locally pending play delta — the shape every list screen renders. */
data class TrackWithCounters(
    val id: String,
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
    val pendingPlayCount: Int,
    val myLastPlayedAtEpoch: Long?,
    val myCompleted: Boolean,
    val downloadState: Int?
)

private const val TRACK_SELECT = """
    SELECT t.id, t.title, t.artist, t.duration, t.fileSize, t.mimeType, t.uploadedAtEpoch,
           t.isPublic, t.isOwned, t.uploaderUsername, t.serverPlayCount,
           COALESCE(c.pendingPlayCount, 0) AS pendingPlayCount,
           t.myLastPlayedAtEpoch, t.myCompleted, d.state AS downloadState
    FROM tracks t
    LEFT JOIN track_counters c ON c.trackId = t.id
    LEFT JOIN downloads d ON d.trackId = t.id
"""

@Dao
interface TrackDao {

    @Query("$TRACK_SELECT WHERE t.inLibrary = 1 ORDER BY t.uploadedAtEpoch DESC, t.id")
    fun observeLibrary(): Flow<List<TrackWithCounters>>

    @Query("$TRACK_SELECT WHERE t.id = :id")
    fun observeTrack(id: String): Flow<TrackWithCounters?>

    @Query("$TRACK_SELECT WHERE t.inLibrary = 1")
    suspend fun libraryOnce(): List<TrackWithCounters>

    @Query("SELECT COUNT(*) FROM tracks WHERE inLibrary = 1")
    fun observeLibraryCount(): Flow<Int>

    /** «N в общем банке» on the profile: the caller's own tracks that are published. */
    @Query("SELECT COUNT(*) FROM tracks WHERE inLibrary = 1 AND isOwned = 1 AND isPublic = 1")
    fun observePublishedCount(): Flow<Int>

    @Query(
        """$TRACK_SELECT
           WHERE t.id IN (SELECT trackId FROM collection_tracks WHERE collectionId = :collectionId)
           ORDER BY (SELECT position FROM collection_tracks
                     WHERE collectionId = :collectionId AND trackId = t.id)"""
    )
    fun observeCollectionTracks(collectionId: String): Flow<List<TrackWithCounters>>

    @Query(
        """$TRACK_SELECT
           WHERE t.id IN (SELECT trackId FROM collection_tracks WHERE collectionId = :collectionId)"""
    )
    suspend fun collectionTracksOnce(collectionId: String): List<TrackWithCounters>

    @Upsert
    suspend fun upsert(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE inLibrary = 1 AND id NOT IN (:keep)")
    suspend fun deleteLibraryTracksNotIn(keep: List<String>)

    /** Nothing left to reference it: not in the library, in no collection, not downloaded. */
    @Query(
        """DELETE FROM tracks
           WHERE inLibrary = 0
             AND id NOT IN (SELECT trackId FROM collection_tracks)
             AND id NOT IN (SELECT trackId FROM downloads)"""
    )
    suspend fun pruneOrphans()

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Transaction
    suspend fun replaceLibrary(tracks: List<TrackEntity>) {
        upsert(tracks)
        deleteLibraryTracksNotIn(tracks.map { it.id })
        pruneOrphans()
    }
}

@Dao
interface TrackCounterDao {

    @Query(
        """INSERT INTO track_counters (trackId, pendingPlayCount, localLastPlayedAtEpoch)
           VALUES (:trackId, 1, :atEpoch)
           ON CONFLICT(trackId) DO UPDATE SET
             pendingPlayCount = pendingPlayCount + 1,
             localLastPlayedAtEpoch = :atEpoch"""
    )
    suspend fun increment(trackId: String, atEpoch: Long)

    @Query(
        """UPDATE track_counters SET pendingPlayCount = MAX(pendingPlayCount - 1, 0)
           WHERE trackId = :trackId"""
    )
    suspend fun decrement(trackId: String)

    @Query("DELETE FROM track_counters WHERE pendingPlayCount <= 0")
    suspend fun clearEmpty()
}

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collections ORDER BY createdAtEpoch")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    fun observeOne(id: String): Flow<CollectionEntity?>

    @Upsert
    suspend fun upsert(collections: List<CollectionEntity>)

    @Query("DELETE FROM collections WHERE id NOT IN (:keep)")
    suspend fun deleteNotIn(keep: List<String>)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setLinks(links: List<CollectionTrackEntity>)

    @Query("DELETE FROM collection_tracks WHERE collectionId = :collectionId")
    suspend fun clearLinks(collectionId: String)

    @Query("DELETE FROM collection_tracks WHERE collectionId = :collectionId AND trackId = :trackId")
    suspend fun removeLink(collectionId: String, trackId: String)

    @Transaction
    suspend fun replaceLinks(collectionId: String, links: List<CollectionTrackEntity>) {
        clearLinks(collectionId)
        setLinks(links)
    }
}

@Dao
interface PendingPlayDao {

    @Upsert
    suspend fun add(event: PendingPlayEntity)

    @Query("SELECT * FROM pending_play_events ORDER BY startedAtEpoch LIMIT :limit")
    suspend fun take(limit: Int): List<PendingPlayEntity>

    @Query("SELECT COUNT(*) FROM pending_play_events")
    suspend fun count(): Int

    @Query("DELETE FROM pending_play_events WHERE clientEventId IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE pending_play_events SET attempts = attempts + 1 WHERE clientEventId IN (:ids)")
    suspend fun markAttempt(ids: List<String>)
}

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY requestedAtEpoch DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE trackId = :trackId")
    suspend fun byTrack(trackId: String): DownloadEntity?

    @Upsert
    suspend fun upsert(download: DownloadEntity)

    @Upsert
    suspend fun upsertAll(downloads: List<DownloadEntity>)

    @Query("DELETE FROM downloads WHERE trackId = :trackId")
    suspend fun deleteByTrack(trackId: String)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    /**
     * Media3's download index is the truth; this table is a mirror kept by a listener that only
     * lives as long as the process. Rewritten wholesale at startup so rows for downloads that
     * disappeared while the app was dead cannot linger.
     */
    @Transaction
    suspend fun replaceAll(downloads: List<DownloadEntity>) {
        deleteAll()
        upsertAll(downloads)
    }
}
