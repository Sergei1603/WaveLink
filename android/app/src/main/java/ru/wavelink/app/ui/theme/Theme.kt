package ru.wavelink.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Nocturne tokens from the design system, transcribed one-for-one. The mock is drawn at
 * 412 × 892, which is exactly Compose's dp grid on a 412dp-wide phone, so every px in the design
 * is a dp here and the numbers can be read straight off the spec.
 *
 * The app is dark-only on purpose: Nocturne defines a single ground (`--color-bg`), and the
 * photographic backdrops on the auth, library, bank and player screens are lit for it.
 */
object Wl {
    val Bg = Color(0xFF161826)
    val Surface = Color(0xFF232532)
    val Text = Color(0xFFE9E9ED)
    val Accent = Color(0xFF9184D9)

    /** `--color-divider`: the text colour at 16%, not a separate hue. */
    val Divider = Text.copy(alpha = 0.16f)

    val Neutral100 = Color(0xFFF3F5FE)
    val Neutral200 = Color(0xFFE4E7F5)
    val Neutral300 = Color(0xFFCFD3E5)
    val Neutral400 = Color(0xFFB2B6CA)
    val Neutral500 = Color(0xFF9397AB)
    val Neutral600 = Color(0xFF75798C)
    val Neutral700 = Color(0xFF595D6C)
    val Neutral800 = Color(0xFF3F424D)
    val Neutral900 = Color(0xFF292B31)

    val Accent100 = Color(0xFFF5F4FF)
    val Accent200 = Color(0xFFE7E5FE)
    val Accent300 = Color(0xFFD2CEFD)
    val Accent400 = Color(0xFFB5ABFC)
    val Accent500 = Color(0xFF968AE0)
    val Accent600 = Color(0xFF796CBF)
    val Accent700 = Color(0xFF5D5294)
    val Accent800 = Color(0xFF423A6A)
    val Accent900 = Color(0xFF2B2741)

    /** The chrome behind the mini-player and the tab bar — `#1a1c2c` at 92%. */
    val BarBackground = Color(0xFF1A1C2C).copy(alpha = 0.92f)

    val RadiusSm = RoundedCornerShape(4.dp)
    val RadiusMd = RoundedCornerShape(8.dp)
    val RadiusLg = RoundedCornerShape(14.dp)

    /** `color-mix(in srgb, var(--color-text) N%, transparent)`, the design's one dimming idiom. */
    fun text(percent: Int): Color = Text.copy(alpha = percent / 100f)

    /** The same mix against the accent, used for tinted fills. */
    fun accent(percent: Int): Color = Accent.copy(alpha = percent / 100f)
}

/**
 * Nocturne sets headings in the same family as body text at weight 500 and body at 400, so the
 * distinction the mock draws is weight and size, not typeface. Sizes are the design's px values.
 */
object WlType {
    val Display = TextStyle(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp)
    val Title = TextStyle(fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.45).sp)
    val TitleSm = TextStyle(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.35).sp)
    val Numeric = TextStyle(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
    val NumericSm = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
    val Heading = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
    /** Track and list titles: heading family at 15px. */
    val RowTitle = TextStyle(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
    val Body = TextStyle(fontSize = 15.sp, lineHeight = 23.sp)
    val BodySm = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    val Caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val Meta = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val Micro = TextStyle(fontSize = 11.sp, lineHeight = 15.sp)
    /** The uppercase 11px kicker above every section list. */
    val Kicker = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 1.1.sp)
}

private val NocturneColors = darkColorScheme(
    primary = Wl.Accent,
    onPrimary = Wl.Bg,
    primaryContainer = Wl.Accent800,
    onPrimaryContainer = Wl.Accent100,
    secondary = Wl.Accent300,
    onSecondary = Wl.Bg,
    background = Wl.Bg,
    onBackground = Wl.Text,
    surface = Wl.Surface,
    onSurface = Wl.Text,
    surfaceVariant = Wl.Neutral800,
    onSurfaceVariant = Wl.Neutral400,
    outline = Wl.Neutral700,
    outlineVariant = Wl.Neutral800,
    error = Color(0xFFE7908A)
)

/**
 * Material components still show up in a few places (bottom sheet, dialogs, the system slider),
 * so the scheme is mapped even though the app's own widgets draw from [Wl] directly.
 */
@Composable
fun WaveLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NocturneColors,
        typography = Typography(
            headlineSmall = WlType.Title,
            titleLarge = WlType.Heading,
            titleMedium = WlType.RowTitle,
            bodyLarge = WlType.Body,
            bodyMedium = WlType.BodySm,
            bodySmall = WlType.Meta,
            labelSmall = WlType.Micro
        ),
        content = content
    )
}
