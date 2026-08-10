package ru.wavelink.app.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.wavelink.app.core.db.TrackDao
import javax.inject.Inject

data class DownloadItem(
    val trackId: String,
    val title: String,
    val stateLabel: String,
    val bytesDownloaded: Long,
    val percent: Float
)

@UnstableApi
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repo: DownloadsRepository,
    trackDao: TrackDao
) : ViewModel() {

    val items: StateFlow<List<DownloadItem>> =
        combine(repo.observeAll(), trackDao.observeLibrary()) { downloads, tracks ->
            val titles = tracks.associate { it.id to it.title }
            downloads.map { download ->
                DownloadItem(
                    trackId = download.trackId,
                    title = titles[download.trackId] ?: download.trackId,
                    stateLabel = label(download.state),
                    bytesDownloaded = download.bytesDownloaded,
                    percent = download.percent
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun download(trackId: String) = repo.download(trackId)

    fun remove(trackId: String) = repo.remove(trackId)

    private fun label(state: Int): String = when (state) {
        Download.STATE_QUEUED -> "в очереди"
        Download.STATE_DOWNLOADING -> "скачивается"
        Download.STATE_COMPLETED -> "доступен офлайн"
        Download.STATE_FAILED -> "ошибка"
        Download.STATE_STOPPED -> "приостановлен"
        Download.STATE_REMOVING -> "удаляется"
        Download.STATE_RESTARTING -> "перезапуск"
        else -> "—"
    }
}
