package ru.wavelink.app.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.wavelink.app.core.db.TrackDao
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.core.model.toEntity
import ru.wavelink.app.core.model.toModel
import ru.wavelink.app.core.net.TrackDetailDto
import ru.wavelink.app.core.net.UpdateTrackBody
import ru.wavelink.app.core.net.WaveLinkApi
import javax.inject.Inject
import javax.inject.Singleton

/** One page of a shuffled cycle, ready for the player's queue. */
data class ShufflePage(
    val tracks: List<Track>,
    val seed: Int,
    val nextCursor: Int,
    val hasMore: Boolean,
    val total: Int
)

/**
 * Offline-first: the UI always reads from Room, and [refreshLibrary] is what talks to the server.
 * A failed refresh leaves the cached list on screen rather than blanking it.
 */
@Singleton
class TrackRepository @Inject constructor(
    private val api: WaveLinkApi,
    private val trackDao: TrackDao
) {
    fun observeLibrary(): Flow<List<Track>> =
        trackDao.observeLibrary().map { rows -> rows.map { it.toModel() } }

    fun observeTrack(id: String): Flow<Track?> =
        trackDao.observeTrack(id).map { it?.toModel() }

    /** A one-shot read of the cached library — what offline shuffle draws from. */
    suspend fun libraryOnce(): List<Track> = trackDao.libraryOnce().map { it.toModel() }

    suspend fun collectionTracksOnce(collectionId: String): List<Track> =
        trackDao.collectionTracksOnce(collectionId).map { it.toModel() }

    suspend fun refreshLibrary() {
        val now = System.currentTimeMillis()
        val page = api.tracks(page = 1, limit = 200, sort = "recent")
        trackDao.replaceLibrary(page.items.map { it.toEntity(inLibrary = true, now = now) })
    }

    /** Public-bank search is not cached: it is a server-side query, not part of the library. */
    suspend fun searchPublic(query: String, sort: String = "recent"): List<Track> =
        api.publicTracks(search = query.ifBlank { null }, sort = sort).items.map { it.toModel() }

    suspend fun detail(id: String): TrackDetailDto = api.track(id)

    suspend fun shuffle(
        mode: String,
        limit: Int = 50,
        collectionId: String? = null,
        seed: Int? = null,
        cursor: Int = 0
    ): ShufflePage {
        val page = api.shuffle(
            mode = mode, limit = limit, collectionId = collectionId, seed = seed, cursor = cursor
        )
        return ShufflePage(
            tracks = page.items.map { it.toModel() },
            seed = page.seed,
            nextCursor = page.nextCursor,
            hasMore = page.hasMore,
            total = page.total
        )
    }

    suspend fun update(id: String, title: String?, artist: String?, isPublic: Boolean?) {
        api.updateTrack(id, UpdateTrackBody(title, artist, isPublic))
        refreshLibrary()
    }

    suspend fun save(id: String) {
        api.saveTrack(id)
        refreshLibrary()
    }

    suspend fun unsave(id: String) {
        api.unsaveTrack(id)
        trackDao.deleteById(id)
        refreshLibrary()
    }

    suspend fun delete(id: String) {
        api.deleteTrack(id)
        trackDao.deleteById(id)
        refreshLibrary()
    }
}
