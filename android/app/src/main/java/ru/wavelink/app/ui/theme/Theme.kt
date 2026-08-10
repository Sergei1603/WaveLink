package ru.wavelink.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Mirrors the web client's accent palette so the two clients feel like one product. */
private val Accent = Color(0xFF9184D9)
private val AccentLight = Color(0xFFE7E5FE)
private val Surface = Color(0xFF24262E)
private val Background = Color(0xFF1D1E24)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF17181D),
    primaryContainer = Color(0xFF3B3566),
    onPrimaryContainer = AccentLight,
    secondary = AccentLight,
    background = Background,
    onBackground = Color(0xFFE9E9EE),
    surface = Surface,
    onSurface = Color(0xFFE9E9EE),
    surfaceVariant = Color(0xFF3F424D),
    onSurfaceVariant = Color(0xFFB9BAC4)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4CBF),
    onPrimary = Color.White,
    secondary = Accent,
    background = Color(0xFFF7F7FA),
    surface = Color.White
)

@Composable
fun WaveLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
