package ru.wavelink.app.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.wavelink.app.core.db.CollectionEntity
import ru.wavelink.app.core.net.toUserMessage
import ru.wavelink.app.ui.components.WlInput
import ru.wavelink.app.ui.components.WlListRow
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType
import ru.wavelink.app.ui.tracksLabel
import javax.inject.Inject

@HiltViewModel
class AddToCollectionViewModel @Inject constructor(
    private val repo: CollectionRepository
) : ViewModel() {

    val collections: StateFlow<List<CollectionEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { viewModelScope.launch { runCatching { repo.refreshAll() } } }

    fun add(collectionId: String, trackId: String, onDone: (String?) -> Unit) =
        viewModelScope.launch {
            runCatching { repo.addTrack(collectionId, trackId) }
                .onSuccess { onDone(null) }
                .onFailure { onDone(it.toUserMessage()) }
        }

    fun createAndAdd(name: String, trackId: String, onDone: (String?) -> Unit) =
        viewModelScope.launch {
            runCatching { repo.addTrack(repo.create(name), trackId) }
                .onSuccess { onDone(null) }
                .onFailure { onDone(it.toUserMessage()) }
        }
}

/**
 * «В коллекцию» from the player and the track card. Both reach the same picker rather than each
 * growing their own list of collections.
 */
@Composable
fun AddToCollectionDialog(
    trackId: String,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit,
    viewModel: AddToCollectionViewModel = hiltViewModel()
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Wl.Surface,
        title = { Text("В коллекцию", style = WlType.Heading, color = Wl.Text) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (creating) {
                    WlInput(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Название",
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    if (collections.isEmpty()) {
                        Text("Коллекций пока нет", style = WlType.BodySm, color = Wl.text(50))
                    }
                    collections.forEach { collection ->
                        WlListRow(
                            title = collection.name,
                            hint = tracksLabel(collection.trackCount),
                            ruled = true,
                            onClick = {
                                viewModel.add(collection.id, trackId) { error ->
                                    onResult(error ?: "Добавлено в «${collection.name}»")
                                    onDismiss()
                                }
                            }
                        )
                    }
                    Text(
                        "Создать новую",
                        style = WlType.Caption,
                        color = Wl.Accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { creating = true }
                            .padding(vertical = 14.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.createAndAdd(name, trackId) { error ->
                            onResult(error ?: "Добавлено в «${name.trim()}»")
                            onDismiss()
                        }
                    }
                ) { Text("Создать и добавить", color = Wl.Accent) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Wl.text(60)) } }
    )
}
