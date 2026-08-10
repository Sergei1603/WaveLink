package ru.wavelink.app.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        TrackEntity::class,
        TrackCounterEntity::class,
        CollectionEntity::class,
        CollectionTrackEntity::class,
        PendingPlayEntity::class,
        DownloadEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WaveLinkDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun trackCounterDao(): TrackCounterDao
    abstract fun collectionDao(): CollectionDao
    abstract fun pendingPlayDao(): PendingPlayDao
    abstract fun downloadDao(): DownloadDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): WaveLinkDatabase =
        Room.databaseBuilder(context, WaveLinkDatabase::class.java, "wavelink.db")
            // The database is a cache of server state plus a small outbox; rebuilding it is cheap.
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun trackDao(db: WaveLinkDatabase): TrackDao = db.trackDao()
    @Provides fun trackCounterDao(db: WaveLinkDatabase): TrackCounterDao = db.trackCounterDao()
    @Provides fun collectionDao(db: WaveLinkDatabase): CollectionDao = db.collectionDao()
    @Provides fun pendingPlayDao(db: WaveLinkDatabase): PendingPlayDao = db.pendingPlayDao()
    @Provides fun downloadDao(db: WaveLinkDatabase): DownloadDao = db.downloadDao()
}
