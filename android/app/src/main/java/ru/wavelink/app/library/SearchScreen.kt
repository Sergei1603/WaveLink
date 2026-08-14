package ru.wavelink.app.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.ui.TrackRow
import ru.wavelink.app.ui.components.WlIconButton
import ru.wavelink.app.ui.components.WlInput
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType
import ru.wavelink.app.ui.tracksLabel
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(repo: TrackRepository) : ViewModel() {
    /** Reads the same cached library the list screen does, so search works offline too. */
    val library: StateFlow<List<Track>> = repo.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * Screen 03. A local filter over the already-loaded library — no request goes out. Anything the
 * library does not hold is a job for the public bank, which the footer says out loud.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onOpenDetail: (Track) -> Unit,
    onOpenBank: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    val results = remember(library, query) {
        if (query.isBlank()) library
        else library.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Wl.Bg)
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WlIconButton(onClick = onBack, size = 40.dp) {
                    Text("←", style = WlType.Heading, color = Wl.Accent)
                }
                WlInput(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Название или исполнитель",
                    minHeight = 44.dp,
                    trailing = {
                        if (query.isNotEmpty()) {
                            Text(
                                "✕",
                                style = WlType.Meta,
                                color = Wl.text(45),
                                modifier = Modifier.clickable { query = "" }.padding(4.dp)
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).focusRequester(focus)
                )
            }
            Text(
                if (query.isBlank()) "Фильтр применяется к загруженному списку, офлайн тоже"
                else "${results.size} из ${tracksLabel(library.size)} · " +
                    "фильтр применяется к загруженному списку, офлайн тоже",
                style = WlType.Meta,
                color = Wl.text(45)
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(results, key = { _, t -> t.id }) { index, track ->
                TrackRow(
                    track = track,
                    index = index,
                    showPlays = true,
                    onPlay = { onPlay(track, results) },
                    onOpenDetail = { onOpenDetail(track) }
                )
            }
            item {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Wl.text(45))) { append("Нет нужного? Загляните в ") }
                        withStyle(SpanStyle(color = Wl.Accent300)) { append("Общий банк") }
                        withStyle(SpanStyle(color = Wl.text(45))) { append(" — там поиск идёт по серверу.") }
                    },
                    style = WlType.Caption,
                    modifier = Modifier
                        .clickable(onClick = onOpenBank)
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                )
            }
        }
    }
}
