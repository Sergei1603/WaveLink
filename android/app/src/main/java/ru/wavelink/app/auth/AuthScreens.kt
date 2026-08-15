package ru.wavelink.app.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.wavelink.app.R
import ru.wavelink.app.core.prefs.SettingsStore
import ru.wavelink.app.ui.components.AuthScrim
import ru.wavelink.app.ui.components.WlBackdrop
import ru.wavelink.app.ui.components.WlButton
import ru.wavelink.app.ui.components.WlButtonStyle
import ru.wavelink.app.ui.components.WlFieldLabel
import ru.wavelink.app.ui.components.WlInput
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType

/** `^[A-Za-z0-9._-]{3,32}$` — mirrors DTOs/UsernameRules on the server. */
private val UsernamePattern = Regex("^[A-Za-z0-9._-]{3,32}$")

/**
 * Screen 01. The form sits at the bottom of a photographic ground that hands over to
 * `--color-bg` before it reaches the fields, so nothing has to be boxed to stay readable.
 */
@Composable
fun AuthScreen(
    registerMode: Boolean,
    onToggleMode: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var revealPassword by rememberSaveable { mutableStateOf(false) }

    // Null until the user touches the field: the stored address arrives asynchronously, and
    // seeding local state with the empty initial value would show a blank field for good.
    var serverEdit by rememberSaveable { mutableStateOf<String?>(null) }
    val server = serverEdit ?: baseUrl

    val usernameValid = remember(username) { UsernamePattern.matches(username) }
    val passwordValid = password.length >= 8
    val serverValid = remember(server) { SettingsStore.isValidBaseUrl(server) }
    val canSubmit = usernameValid && passwordValid && serverValid && !state.busy

    Box(modifier = Modifier.fillMaxSize().background(Wl.Bg)) {
        WlBackdrop(
            painter = painterResource(R.drawable.backdrop_auth),
            alpha = 1f,
            saturation = 1f,
            brightness = 1f,
            verticalBias = -0.4f,
            scrim = AuthScrim
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp, Alignment.Bottom)
        ) {
            Brand(modifier = Modifier.padding(top = 120.dp))

            Column {
                Text(
                    if (registerMode) "Создайте аккаунт" else "С возвращением",
                    style = WlType.Display,
                    color = Wl.Text
                )
                Text(
                    if (registerMode) "Своя библиотека, свои коллекции, свой сервер."
                    else "Ваша музыка ждёт там же, где вы её оставили.",
                    style = WlType.BodySm,
                    color = Wl.text(55),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    WlFieldLabel("Адрес сервера")
                    WlInput(
                        value = server,
                        onValueChange = { serverEdit = it; viewModel.clearError() },
                        placeholder = "193.222.99.254",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    when {
                        server.isNotEmpty() && !serverValid ->
                            Hint("Например: 193.222.99.254, 192.168.0.10:5000 или https://wavelink.ru")
                        // Show what the bare host actually expands to, so the http:// default
                        // never comes as a surprise.
                        serverValid && !server.startsWith("http", ignoreCase = true) ->
                            Hint(SettingsStore.normalizeBaseUrl(server))
                    }
                }

                Column {
                    WlFieldLabel("Никнейм")
                    WlInput(
                        value = username,
                        onValueChange = { username = it; viewModel.clearError() },
                        placeholder = "anna_k",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (username.isNotEmpty() && !usernameValid) {
                        Hint("3–32 символа: латиница, цифры, точка, дефис, подчёркивание")
                    }
                }

                Column {
                    WlFieldLabel("Пароль")
                    WlInput(
                        value = password,
                        onValueChange = { password = it; viewModel.clearError() },
                        placeholder = "••••••••",
                        visualTransformation =
                            if (revealPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        trailing = {
                            Text(
                                if (revealPassword) "Скрыть" else "Показать",
                                style = WlType.Meta,
                                color = Wl.Accent,
                                modifier = Modifier
                                    .clickable { revealPassword = !revealPassword }
                                    .padding(4.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (password.isNotEmpty() && !passwordValid) Hint("Минимум 8 символов")
                }

                state.error?.let {
                    Text(it, style = WlType.Meta, color = Wl.Accent300)
                }

                WlButton(
                    text = when {
                        state.busy -> "…"
                        registerMode -> "Зарегистрироваться"
                        else -> "Войти"
                    },
                    onClick = {
                        if (registerMode) viewModel.register(server, username, password)
                        else viewModel.login(server, username, password)
                    },
                    style = WlButtonStyle.Primary,
                    enabled = canSubmit,
                    minHeight = 48.dp,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (registerMode) "Уже есть аккаунт? " else "Нет аккаунта? ",
                        style = WlType.Caption,
                        color = Wl.text(50)
                    )
                    Text(
                        if (registerMode) "Войти" else "Создать",
                        style = WlType.Caption,
                        color = Wl.Accent,
                        modifier = Modifier.clickable(onClick = onToggleMode)
                    )
                }
            }
        }
    }
}

/** The wordmark: four bars of an equaliser, then the name. */
@Composable
private fun Brand(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.height(20.dp)
        ) {
            Bar(9.dp, Wl.Accent)
            Bar(18.dp, Wl.Accent)
            Bar(12.dp, Wl.Accent700)
            Bar(20.dp, Wl.Accent700)
        }
        Text("WaveLink", style = WlType.Heading.copy(fontSize = WlType.Heading.fontSize), color = Wl.Text)
    }
}

@Composable
private fun Bar(height: androidx.compose.ui.unit.Dp, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .width(3.dp)
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = WlType.Micro,
        color = Wl.text(45),
        modifier = Modifier.padding(top = 5.dp)
    )
}
