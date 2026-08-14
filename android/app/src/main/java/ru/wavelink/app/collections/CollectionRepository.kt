package ru.wavelink.app.collections

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.wavelink.app.core.db.CollectionDao
import ru.wavelink.app.core.db.CollectionEntity
import ru.wavelink.app.core.db.CollectionTrackEntity
import ru.wavelink.app.core.db.TrackDao
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.core.model.parseInstant
import ru.wavelink.app.core.model.toEntity
import ru.wavelink.app.core.model.toModel
import ru.wavelink.app.core.net.NameBody
import ru.wavelink.app.core.net.TrackIdBody
import ru.wavelink.app.core.net.WaveLinkApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionRepository @Inject constructor(
    private val api: WaveLinkApi,
    private val collectionDao: CollectionDao,
    private val trackDao: TrackDao
) {
    fun observeAll(): Flow<List<CollectionEntity>> = collectionDao.observeAll()

    fun observeOne(id: String): Flow<CollectionEntity?> = collectionDao.observeOne(id)

    fun observeTracks(collectionId: String): Flow<List<Track>> =
        trackDao.observeCollectionTracks(collectionId).map { rows -> rows.map { it.toModel() } }

    suspend fun refreshAll() {
        val remote = api.collections()
        collectionDao.upsert(
            remote.map {
                CollectionEntity(
                    id = it.id,
                    name = it.name,
                    createdAtEpoch = parseInstant(it.createdAt)?.toEpochMilli() ?: 0L,
                    trackCount = it.trackCount
                )
            }
        )
        collectionDao.deleteNotIn(remote.map { it.id })
    }

    suspend fun refreshOne(collectionId: String) {
        val detail = api.collection(collectionId)
        val now = System.currentTimeMillis()
        // Collection members may include tracks that are not in the library list (saved public
        // tracks), so cache them with inLibrary = false rather than dropping them.
        trackDao.upsert(detail.tracks.map { it.toEntity(inLibrary = false, now = now) })
        collectionDao.replaceLinks(
            collectionId,
            detail.tracks.mapIndexed { index, track ->
                CollectionTrackEntity(collectionId, track.id, index)
            }
        )
    }

    /** Returns the new collection's id, so a "create and add" flow need not re-find it by name. */
    suspend fun create(name: String): String {
        val created = api.createCollection(NameBody(name.trim()))
        refreshAll()
        return created.id
    }

    suspend fun delete(id: String) {
        api.deleteCollection(id)
        collectionDao.deleteById(id)
        collectionDao.clearLinks(id)
    }

    suspend fun addTrack(collectionId: String, trackId: String) {
        api.addToCollection(collectionId, TrackIdBody(trackId))
        refreshOne(collectionId)
        refreshAll()
    }

    suspend fun removeTrack(collectionId: String, trackId: String) {
        api.removeFromCollection(collectionId, trackId)
        collectionDao.removeLink(collectionId, trackId)
        refreshAll()
    }
}
