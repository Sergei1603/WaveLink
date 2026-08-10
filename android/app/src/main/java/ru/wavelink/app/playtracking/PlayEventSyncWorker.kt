package ru.wavelink.app.playtracking

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.wavelink.app.core.db.PendingPlayDao
import ru.wavelink.app.core.db.TrackCounterDao
import ru.wavelink.app.core.net.ReportPlayItemDto
import ru.wavelink.app.core.net.ReportPlaysBody
import ru.wavelink.app.core.net.WaveLinkApi
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flushes the offline queue. Every replay is free — the server dedups on `clientEventId` — so a
 * failure simply leaves the rows in place for the next attempt.
 */
@HiltWorker
class PlayEventSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: WaveLinkApi,
    private val pendingPlays: PendingPlayDao,
    private val counters: TrackCounterDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val batch = pendingPlays.take(BATCH_SIZE)
        if (batch.isEmpty()) return Result.success()

        val body = ReportPlaysBody(
            batch.map {
                ReportPlayItemDto(
                    clientEventId = it.clientEventId,
                    trackId = it.trackId,
                    startedAt = Instant.ofEpochMilli(it.startedAtEpoch).toString(),
                    listenedSeconds = it.listenedSeconds.toDouble(),
                    trackDuration = it.trackDuration,
                    source = "android"
                )
            }
        )

        val response = runCatching { api.reportPlays(body) }.getOrElse {
            pendingPlays.markAttempt(batch.map { row -> row.clientEventId })
            return Result.retry()
        }

        // accepted, duplicate and rejected are all terminal — drop everything the server answered for.
        val settled = response.results.map { it.clientEventId }.toSet()
        val settledRows = batch.filter { it.clientEventId in settled }

        pendingPlays.deleteByIds(settledRows.map { it.clientEventId })
        // The server has absorbed these, so the local delta must go away exactly now.
        settledRows.filter { it.countedLocally }.forEach { counters.decrement(it.trackId) }
        counters.clearEmpty()

        return if (pendingPlays.count() > 0) Result.retry() else Result.success()
    }

    companion object {
        const val BATCH_SIZE = 100
        const val UNIQUE_WORK = "play-sync"
        const val PERIODIC_WORK = "play-sync-periodic"
    }
}

/** Thin wrapper so the tracker does not need to know about WorkManager request plumbing. */
@Singleton
class PlaySyncScheduler @Inject constructor(private val workManager: WorkManager) {

    private val networked = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleNow() {
        workManager.enqueueUniqueWork(
            PlayEventSyncWorker.UNIQUE_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<PlayEventSyncWorker>()
                .setConstraints(networked)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        )
    }

    /** Safety net in case every one-shot attempt was lost (process death, permanent failures). */
    fun schedulePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            PlayEventSyncWorker.PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PlayEventSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(networked)
                .build()
        )
    }
}
