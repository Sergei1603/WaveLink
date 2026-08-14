package ru.wavelink.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.wavelink.app.ui.theme.Wl
import ru.wavelink.app.ui.theme.WlType

/**
 * The Nocturne component classes (`.btn`, `.tag`, `.seg`, `.input`, `.hr`, `.card`) as composables.
 * Everything here is a direct transcription of `_ds/nocturne/styles.css`; screens compose these
 * rather than restating paddings and colours.
 */

// ── buttons ────────────────────────────────────────────────────────────────────

enum class WlButtonStyle { Primary, Secondary, Ghost }

@Composable
fun WlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: WlButtonStyle = WlButtonStyle.Secondary,
    enabled: Boolean = true,
    minHeight: Dp = 40.dp,
    fontSize: TextStyle = WlType.BodySm,
    color: Color? = null,
    icon: (@Composable () -> Unit)? = null
) {
    val content = color ?: when (style) {
        WlButtonStyle.Primary, WlButtonStyle.Ghost -> Wl.Accent
        WlButtonStyle.Secondary -> Wl.Text
    }
    val border = when (style) {
        WlButtonStyle.Primary -> Wl.Accent
        WlButtonStyle.Secondary -> Wl.Divider
        WlButtonStyle.Ghost -> Color.Transparent
    }
    Row(
        modifier = modifier
            .clip(Wl.RadiusMd)
            .border(1.dp, if (enabled) border else border.copy(alpha = 0.45f), Wl.RadiusMd)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = minHeight)
            .padding(horizontal = if (style == WlButtonStyle.Ghost) 6.dp else 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            CompositionLocalProvider(LocalContentColor provides content) { icon() }
        }
        Text(
            text,
            style = fontSize.copy(fontWeight = FontWeight.Medium),
            color = if (enabled) content else content.copy(alpha = 0.45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/** A stacked icon-over-label button — the three actions under the player's transport row. */
@Composable
fun WlStackedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Wl.text(70),
    icon: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(Wl.RadiusMd)
            .clickable(onClick = onClick)
            .heightIn(min = 58.dp)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CompositionLocalProvider(LocalContentColor provides color) { icon() }
        Text(label, style = WlType.Micro, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun WlIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    enabled: Boolean = true,
    shape: RoundedCornerShape = Wl.RadiusMd,
    border: Color = Color.Transparent,
    background: Color = Color.Transparent,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background)
            .border(if (border == Color.Transparent) 0.dp else 1.dp, border, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** The player's round transport buttons — `.btn-primary.btn-icon` with `border-radius: 50%`. */
@Composable
fun WlRoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    content: @Composable () -> Unit
) = WlIconButton(
    onClick = onClick,
    modifier = modifier,
    size = size,
    shape = RoundedCornerShape(percent = 50),
    border = Wl.Accent,
    content = content
)

// ── tags ───────────────────────────────────────────────────────────────────────

enum class WlTagStyle { Accent, Neutral, Outline }

@Composable
fun WlTag(text: String, modifier: Modifier = Modifier, style: WlTagStyle = WlTagStyle.Neutral) {
    val shape = RoundedCornerShape(6.dp) // calc(--radius-md * 0.75)
    val background = when (style) {
        WlTagStyle.Accent -> Wl.Accent800
        WlTagStyle.Neutral -> Wl.Neutral800
        WlTagStyle.Outline -> Color.Transparent
    }
    val foreground = when (style) {
        WlTagStyle.Accent -> Wl.Accent100
        WlTagStyle.Neutral -> Wl.Neutral100
        WlTagStyle.Outline -> Wl.Accent
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .then(if (style == WlTagStyle.Outline) Modifier.border(1.dp, Wl.Accent, shape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text, style = WlType.Micro, color = foreground, maxLines = 1)
    }
}

// ── segmented control ──────────────────────────────────────────────────────────

/**
 * `.seg` — one rounded, clipped strip of equal-width options. The active one is marked by an
 * accent hairline and accent text, exactly as `.seg-opt:has(input:checked)` does.
 */
@Composable
fun WlSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 36.dp,
    icons: List<(@Composable () -> Unit)?> = emptyList()
) {
    Row(
        modifier = modifier
            .clip(Wl.RadiusMd)
            .border(1.dp, Wl.Divider, Wl.RadiusMd)
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = minHeight)
                    .then(if (index > 0) Modifier.leftDivider() else Modifier)
                    .then(if (active) Modifier.border(1.dp, Wl.Accent) else Modifier)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icons.getOrNull(index)?.let { icon ->
                    CompositionLocalProvider(
                        LocalContentColor provides if (active) Wl.Accent else Wl.Text
                    ) { icon() }
                }
                Text(
                    label,
                    style = WlType.Caption,
                    color = if (active) Wl.Accent else Wl.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun Modifier.leftDivider(): Modifier = drawBehind {
    drawRect(
        color = Wl.Divider,
        topLeft = Offset.Zero,
        size = Size(1f, size.height)
    )
}

// ── fields ─────────────────────────────────────────────────────────────────────

@Composable
fun WlFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = WlType.Meta, color = Wl.text(70), modifier = modifier.padding(bottom = 5.dp))
}

/**
 * `.input` — surface fill, divider hairline, accent border while focused. Written on
 * BasicTextField rather than Material's OutlinedTextField, whose floating label and 56dp
 * minimum do not exist in this design.
 */
@Composable
fun WlInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    minHeight: Dp = 48.dp,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .clip(Wl.RadiusMd)
            .background(Wl.Surface)
            .border(1.dp, if (focused) Wl.Accent else Wl.Divider, Wl.RadiusMd)
            .heightIn(min = minHeight)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        leading?.invoke()
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && placeholder != null) {
                Text(placeholder, style = WlType.BodySm, color = Wl.text(45), maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                interactionSource = interaction,
                textStyle = WlType.BodySm.copy(color = Wl.Text),
                cursorBrush = SolidColor(Wl.Accent),
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                modifier = Modifier.fillMaxWidth()
            )
        }
        trailing?.invoke()
    }
}

/** A field-shaped surface that is not editable in place — tapping it opens something else. */
@Composable
fun WlInputButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    minHeight: Dp = 44.dp,
    background: Color = Wl.Surface,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .clip(Wl.RadiusMd)
            .background(background)
            .border(1.dp, Wl.Divider, Wl.RadiusMd)
            .clickable(onClick = onClick)
            .heightIn(min = minHeight)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        leading?.invoke()
        Text(
            text,
            style = WlType.BodySm,
            color = if (muted) Wl.text(45) else Wl.Text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailing?.invoke()
    }
}

/** The pill toggle from the settings rows and the track card's Public switch. */
@Composable
fun WlToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (checked) Wl.Accent else Wl.Neutral800)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (checked) Wl.Neutral100 else Wl.Neutral500)
        )
    }
}

// ── rules and meters ───────────────────────────────────────────────────────────

/**
 * Nocturne's signature rule: a hairline that fades to nothing at both ends over the last 40dp,
 * rather than stopping square. Drawn rather than composed so it can sit on any edge.
 */
fun Modifier.fadingRule(
    top: Boolean = false,
    color: Color = Wl.Divider,
    inset: Dp = 40.dp
): Modifier = drawBehind {
    if (size.width <= 0f) return@drawBehind
    val fade = inset.toPx().coerceAtMost(size.width / 2f)
    val stops = arrayOf(
        0f to Color.Transparent,
        (fade / size.width) to color,
        1f - (fade / size.width) to color,
        1f to Color.Transparent
    )
    val thickness = 1.dp.toPx()
    drawRect(
        brush = Brush.horizontalGradient(colorStops = stops),
        topLeft = Offset(0f, if (top) 0f else size.height - thickness),
        size = Size(size.width, thickness)
    )
}

/** A flat progress track: `--color-neutral-800` behind, accent in front. */
@Composable
fun WlMeter(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 5.dp,
    color: Color = Wl.Accent,
    track: Color = Wl.Neutral800
) {
    val shape = RoundedCornerShape(height / 2)
    Box(modifier = modifier.height(height).clip(shape).background(track)) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(shape)
                .background(color)
        )
    }
}

/** The downloads screen's two-part bar: pinned bytes, then the evictable stream cache. */
@Composable
fun WlSplitMeter(
    first: Float,
    second: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp
) {
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Wl.Neutral800)
    ) {
        if (first > 0f) Box(Modifier.weight(first).fillMaxSize().background(Wl.Accent))
        if (second > 0f) Box(Modifier.weight(second).fillMaxSize().background(Wl.Accent700))
        val rest = (1f - first - second).coerceAtLeast(0f)
        if (rest > 0f) Box(Modifier.weight(rest).fillMaxSize())
    }
}

// ── backdrops ──────────────────────────────────────────────────────────────────

/**
 * The photographic grounds behind the auth, library, bank and player screens: the image itself,
 * dimmed and desaturated the way the mock's `filter` does, under a gradient that hands the bottom
 * of the screen back to `--color-bg` so text stays legible.
 */
@Composable
fun WlBackdrop(
    painter: Painter,
    modifier: Modifier = Modifier,
    alpha: Float = 0.5f,
    saturation: Float = 0.7f,
    brightness: Float = 0.62f,
    verticalBias: Float = -0.6f,
    scrim: List<Pair<Float, Color>> = DefaultScrim
) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = androidx.compose.ui.BiasAlignment(0f, verticalBias),
            alpha = alpha,
            colorFilter = ColorFilter.colorMatrix(
                ColorMatrix().apply {
                    setToSaturation(saturation)
                    timesAssign(
                        ColorMatrix(
                            floatArrayOf(
                                brightness, 0f, 0f, 0f, 0f,
                                0f, brightness, 0f, 0f, 0f,
                                0f, 0f, brightness, 0f, 0f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                }
            ),
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colorStops = scrim.toTypedArray()))
        )
    }
}

/** The list screens' scrim: barely there at the top, solid ground by 97%. */
val DefaultScrim = listOf(
    0f to Wl.Bg.copy(alpha = 0.08f),
    0.70f to Wl.Bg.copy(alpha = 0.20f),
    0.88f to Wl.Bg.copy(alpha = 0.80f),
    0.97f to Wl.Bg
)

/** The auth screen hands over to the ground much earlier, because the form sits low. */
val AuthScrim = listOf(
    0.06f to Color.Transparent,
    0.44f to Wl.Bg.copy(alpha = 0.70f),
    0.78f to Wl.Bg,
    1f to Wl.Bg
)

// ── misc ───────────────────────────────────────────────────────────────────────

/** `.card` — a plain surface tile; the stats screen's four counters. */
@Composable
fun WlCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(Wl.RadiusMd)
            .background(Wl.Surface)
            .padding(14.dp)
    ) { content() }
}

/** The section kicker + trailing link that heads every list in the stats screens. */
@Composable
fun WlSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title.uppercase(), style = WlType.Kicker, color = Wl.text(45))
        if (action != null && onAction != null) {
            Text(
                action,
                style = WlType.Caption,
                color = Wl.Accent,
                modifier = Modifier.clickable(onClick = onAction).padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
            )
        }
    }
}

/** Every sub-level opens with a back chevron labelled by the level it returns to. */
@Composable
fun WlScreenHeader(parent: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        WlIconButton(onClick = onBack, size = 40.dp) {
            Text("←", style = WlType.Heading, color = Wl.Accent)
        }
        Text(parent, style = WlType.Body, color = Wl.text(70), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A tappable settings/navigation row: title over hint, with something on the right. */
@Composable
fun WlListRow(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    onClick: (() -> Unit)? = null,
    filled: Boolean = false,
    ruled: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (filled) Modifier.clip(Wl.RadiusMd).background(Wl.text(5)) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (ruled) Modifier.fadingRule(inset = 24.dp, color = Wl.text(8)) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = if (filled) 14.dp else 2.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = WlType.Body, color = Wl.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hint != null) {
                Text(hint, style = WlType.Meta, color = Wl.text(50), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

/**
 * The share-of-listening ring on the Топ-100 chart view. Drawn as arcs rather than a conic
 * gradient because Compose has no conic brush; the visual result is the same.
 */
@Composable
fun WlDonut(
    slices: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    diameter: Dp = 216.dp,
    thickness: Dp = 62.dp,
    center: @Composable () -> Unit
) {
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            var start = -90f
            val total = slices.sumOf { it.first.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
            slices.forEach { (value, color) ->
                val sweep = value / total * 360f
                drawArc(color = color, startAngle = start, sweepAngle = sweep, useCenter = true)
                start += sweep
            }
        }
        Box(
            modifier = Modifier
                .size(diameter - thickness * 2)
                .clip(RoundedCornerShape(percent = 50))
                .background(Wl.Bg),
            contentAlignment = Alignment.Center
        ) { center() }
    }
}

/** Ordered accent ramp the donut and its legend both walk, so swatches line up with arcs. */
val WlSliceColors = listOf(
    Wl.Accent300, Wl.Accent, Wl.Accent500, Wl.Accent600, Wl.Accent700, Wl.Accent800
)

/** The four-bar mark that stands in for cover art throughout the app. */
@Composable
fun WlWaveMark(
    seed: Int,
    modifier: Modifier = Modifier,
    bars: Int = 4,
    maxHeight: Dp = 20.dp,
    barWidth: Dp = 3.dp,
    accented: Int = 2
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(bars) { i ->
            val h = maxHeight * (0.32f + kotlin.math.abs(kotlin.math.sin((i + seed) * 0.9)).toFloat() * 0.68f)
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i % 3 == 0 || i < accented) Wl.Accent else Wl.Accent700)
            )
        }
    }
}
