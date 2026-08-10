package ru.wavelink.app.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.wavelink.app.core.db.CollectionEntity
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.core.net.toUserMessage
import javax.inject.Inject

data class CollectionsUiState(val message: String? = null)

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val repo: CollectionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CollectionsUiState())
    val state: StateFlow<CollectionsUiState> = _state.asStateFlow()

    val collections: StateFlow<List<CollectionEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { refresh() }

    fun refresh() = mutate { repo.refreshAll() }

    fun create(name: String) = mutate { repo.create(name) }

    fun delete(id: String) = mutate { repo.delete(id) }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { _state.value = CollectionsUiState(it.toUserMessage()) }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    private val repo: CollectionRepository
) : ViewModel() {

    private val collectionId = MutableStateFlow<String?>(null)

    private val _state = MutableStateFlow(CollectionsUiState())
    val state: StateFlow<CollectionsUiState> = _state.asStateFlow()

    val collection: StateFlow<CollectionEntity?> = collectionId
        .filterNotNull()
        .flatMapLatest { repo.observeOne(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tracks: StateFlow<List<Track>> = collectionId
        .filterNotNull()
        .flatMapLatest { repo.observeTracks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun bind(id: String) {
        if (collectionId.value == id) return
        collectionId.value = id
        viewModelScope.launch {
            runCatching { repo.refreshOne(id) }
                .onFailure { _state.value = CollectionsUiState(it.toUserMessage()) }
        }
    }

    fun removeTrack(trackId: String) {
        val id = collectionId.value ?: return
        viewModelScope.launch {
            runCatching { repo.removeTrack(id, trackId) }
                .onFailure { _state.value = CollectionsUiState(it.toUserMessage()) }
        }
    }
}
