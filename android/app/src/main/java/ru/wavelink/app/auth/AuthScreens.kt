package ru.wavelink.app.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** `^[A-Za-z0-9._-]{3,32}$` — mirrors DTOs/UsernameRules on the server. */
private val UsernamePattern = Regex("^[A-Za-z0-9._-]{3,32}$")

@Composable
fun AuthScreen(
    registerMode: Boolean,
    onToggleMode: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val usernameValid = remember(username) { UsernamePattern.matches(username) }
    val passwordValid = password.length >= 8
    val canSubmit = usernameValid && passwordValid && !state.busy

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("WaveLink", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (registerMode) "Создайте аккаунт" else "Войдите в свою библиотеку",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it; viewModel.clearError() },
            label = { Text("Никнейм") },
            singleLine = true,
            isError = username.isNotEmpty() && !usernameValid,
            supportingText = {
                if (username.isNotEmpty() && !usernameValid) {
                    Text("3–32 символа: латиница, цифры, точка, дефис, подчёркивание")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; viewModel.clearError() },
            label = { Text("Пароль") },
            singleLine = true,
            isError = password.isNotEmpty() && !passwordValid,
            supportingText = {
                if (password.isNotEmpty() && !passwordValid) Text("Минимум 8 символов")
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                if (registerMode) viewModel.register(username, password)
                else viewModel.login(username, password)
            },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.busy) CircularProgressIndicator(modifier = Modifier.padding(2.dp))
            else Text(if (registerMode) "Зарегистрироваться" else "Войти")
        }

        TextButton(onClick = onToggleMode) {
            Text(if (registerMode) "Уже есть аккаунт? Войти" else "Нет аккаунта? Создать")
        }
    }
}
