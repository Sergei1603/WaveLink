package ru.wavelink.app.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.wavelink.app.core.db.TrackDao
import ru.wavelink.app.core.prefs.SettingsStore
import javax.inject.Inject

data class DownloadItem(
    val trackId: String,
    val title: String,
    val artist: String,
    val stateLabel: String,
    val bytesDownloaded: Long,
    val percent: Float,
    val inProgress: Boolean
)

/** The three figures the storage meter needs: what is pinned, what is cached, what is allowed. */
data class StorageUiState(
    val pinnedBytes: Long = 0,
    val streamCacheBytes: Long = 0,
    val limitBytes: Long = SettingsStore.DEFAULT_CACHE_BYTES
)

@UnstableApi
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repo: DownloadsRepository,
    private val settings: SettingsStore,
    trackDao: TrackDao
) : ViewModel() {

    val items: StateFlow<List<DownloadItem>> =
        combine(repo.observeAll(), trackDao.observeLibrary()) { downloads, tracks ->
            val byId = tracks.associateBy { it.id }
            downloads.map { download ->
                val track = byId[download.trackId]
                DownloadItem(
                    trackId = download.trackId,
                    title = track?.title ?: download.trackId,
                    artist = track?.artist.orEmpty(),
                    stateLabel = label(download.state, download.percent),
                    bytesDownloaded = download.bytesDownloaded,
                    percent = download.percent,
                    inProgress = download.state == Download.STATE_DOWNLOADING ||
                        download.state == Download.STATE_QUEUED
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _storage = MutableStateFlow(StorageUiState())
    val storage: StateFlow<StorageUiState> = _storage.asStateFlow()

    /** Bumped by anything that changes disk usage without changing a download or the limit. */
    private val storageTicks = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            combine(repo.observeAll(), settings.cacheBytes, storageTicks) { _, limit, _ -> limit }
                .collect { limit ->
                    // Both figures are read from the caches themselves rather than summed from
                    // the download rows: the rows say what was fetched, the caches say what is
                    // actually on the disk right now.
                    val (pinned, streamed) = withContext(Dispatchers.IO) {
                        repo.pinnedBytes() to repo.streamCacheBytes()
                    }
                    _storage.value = StorageUiState(
                        pinnedBytes = pinned,
                        streamCacheBytes = streamed,
                        limitBytes = limit
                    )
                }
        }
    }

    fun download(trackId: String) = repo.download(trackId)

    fun remove(trackId: String) = repo.remove(trackId)

    fun clearStreamCache() {
        viewModelScope.launch {
            repo.clearStreamCache()
            storageTicks.value++
        }
    }

    private fun label(state: Int, percent: Float): String = when (state) {
        Download.STATE_QUEUED -> "в очереди"
        Download.STATE_DOWNLOADING -> "загружается ${percent.toInt()}%"
        Download.STATE_COMPLETED -> "закреплено"
        Download.STATE_FAILED -> "ошибка"
        Download.STATE_STOPPED -> "приостановлен"
        Download.STATE_REMOVING -> "удаляется"
        Download.STATE_RESTARTING -> "перезапуск"
        else -> "—"
    }
}
