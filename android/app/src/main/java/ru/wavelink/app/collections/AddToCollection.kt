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
import ru.wavelink.app.library.BulkResult
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

    fun add(collectionId: String, trackIds: List<String>, onDone: (BulkResult) -> Unit) =
        viewModelScope.launch {
            val result = runCatching { repo.addTracks(collectionId, trackIds) }
                .getOrElse { BulkResult(0, trackIds.size, it.toUserMessage()) }
            onDone(result)
        }

    fun createAndAdd(name: String, trackIds: List<String>, onDone: (BulkResult) -> Unit) =
        viewModelScope.launch {
            val result = runCatching { repo.addTracks(repo.create(name), trackIds) }
                .getOrElse { BulkResult(0, trackIds.size, it.toUserMessage()) }
            onDone(result)
        }
}

/**
 * «В коллекцию» from the player, the track card and the library selection bar. All three reach
 * the same picker rather than each growing their own list of collections — which is also why it
 * takes a list of ids: a selection of forty is the same gesture as a selection of one.
 */
@Composable
fun AddToCollectionDialog(
    trackIds: List<String>,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit,
    viewModel: AddToCollectionViewModel = hiltViewModel()
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    fun report(collectionName: String, result: BulkResult) {
        onResult(
            buildString {
                append("Добавлено ").append(result.ok).append(" в «").append(collectionName).append("»")
                if (result.failed > 0) append(" · не вышло: ").append(result.failed)
                result.firstError?.let { append(" — ").append(it) }
            }
        )
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Wl.Surface,
        title = {
            Text(
                if (trackIds.size > 1) "В коллекцию · ${tracksLabel(trackIds.size)}" else "В коллекцию",
                style = WlType.Heading,
                color = Wl.Text
            )
        },
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
                                viewModel.add(collection.id, trackIds) { report(collection.name, it) }
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
                        viewModel.createAndAdd(name, trackIds) { report(name.trim(), it) }
                    }
                ) { Text("Создать и добавить", color = Wl.Accent) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Wl.text(60)) } }
    )
}
