package ru.wavelink.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var baseUrl by remember(state.baseUrl) { mutableStateOf(state.baseUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Аккаунт", style = MaterialTheme.typography.titleMedium)
        Text(
            state.username ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onSignOut) { Text("Выйти") }

        HorizontalDivider()

        Text("Сервер", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Адрес API") },
            supportingText = { Text("Изменение применится после перезапуска приложения") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { viewModel.setBaseUrl(baseUrl) }) { Text("Сохранить") }

        HorizontalDivider()

        Text("Кэш", style = MaterialTheme.typography.titleMedium)
        Text(
            "Лимит на потоковый кэш: ${state.cacheMb} МБ",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = state.cacheMb.toFloat(),
            onValueChange = { viewModel.setCacheMb(it.toLong()) },
            valueRange = 64f..4096f,
            steps = 0
        )
        Text(
            "Скачанные для офлайна треки закреплены и под этот лимит не попадают.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
