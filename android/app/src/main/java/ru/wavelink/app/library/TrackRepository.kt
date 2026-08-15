package ru.wavelink.app.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.wavelink.app.core.db.TrackDao
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.core.model.toEntity
import ru.wavelink.app.core.model.toModel
import ru.wavelink.app.core.net.TrackDetailDto
import ru.wavelink.app.core.net.TrackDto
import ru.wavelink.app.core.net.UpdateTrackBody
import ru.wavelink.app.core.net.WaveLinkApi
import javax.inject.Inject
import javax.inject.Singleton

/** What `GET /api/tracks` accepts as the largest page. */
private const val LIBRARY_PAGE_LIMIT = 200

/** A stop so a server that misreports `total` cannot spin the walk forever. */
private const val MAX_LIBRARY_PAGES = 100

/** One tap on «Показать ещё» in the bank; the web client's `PAGE_SIZE` is the same number. */
private const val BANK_PAGE_LIMIT = 100

/** One page of a server-side track query, with how many rows match in all. */
data class TrackPage(val tracks: List<Track>, val total: Int)

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

    /**
     * The server caps `limit` at 200, so a library bigger than that arrives page by page — the
     * same walk the web client does as you scroll, only run to the end here because the whole
     * library has to land in Room for the offline screens and offline shuffle to see it.
     *
     * Room is swapped once, at the end: a failure halfway through leaves the cached library as it
     * was instead of replacing it with a truncated one.
     */
    suspend fun refreshLibrary() {
        val now = System.currentTimeMillis()
        // Uploads and deletions shift rows between pages, so pages can overlap — key by id.
        val all = LinkedHashMap<String, TrackDto>()
        var page = 1
        while (true) {
            val response = api.tracks(page = page, limit = LIBRARY_PAGE_LIMIT, sort = "recent")
            response.items.forEach { all[it.id] = it }
            val lastPage = response.items.size < LIBRARY_PAGE_LIMIT || all.size >= response.total
            if (lastPage || page >= MAX_LIBRARY_PAGES) break
            page++
        }
        trackDao.replaceLibrary(all.values.map { it.toEntity(inLibrary = true, now = now) })
    }

    /**
     * Public-bank search is not cached: it is a server-side query, not part of the library. It is
     * also not walked to the end the way [refreshLibrary] is — the bank belongs to everybody and
     * has no reason to fit in memory — so it arrives one page at a time behind «Показать ещё».
     */
    suspend fun searchPublic(query: String, sort: String = "recent", page: Int = 1): TrackPage {
        val response = api.publicTracks(
            page = page,
            limit = BANK_PAGE_LIMIT,
            search = query.ifBlank { null },
            sort = sort
        )
        return TrackPage(response.items.map { it.toModel() }, response.total)
    }

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
