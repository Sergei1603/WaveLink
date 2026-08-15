package ru.wavelink.app.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import ru.wavelink.app.ui.theme.Wl
import kotlin.math.abs
import kotlin.math.sin

/**
 * The media notification, drawn in the app's own colours.
 *
 * WaveLink has no cover art, and a media notification without any is exactly what made the shade
 * and lock-screen widget look bleached: from Android 12 on, SystemUI tints the media panel from
 * the notification's large icon and falls back to the *device* theme when there is none — a white
 * panel on a phone in light mode. So the panel is given something dark to take its colours from:
 * the wave mark that stands in for cover art everywhere else in the app, on the Nocturne ground.
 * [NotificationCompat.Builder.setColorized] covers Android 11 and below, where the colour is used
 * directly rather than derived.
 *
 * Real artwork, if a track ever carries any, still wins — `DefaultMediaNotificationProvider` sets
 * the large icon from the metadata *after* this hook runs.
 */
@UnstableApi
class WaveLinkNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {

    private val artwork: Bitmap by lazy { renderArtwork() }

    override fun addNotificationActions(
        mediaSession: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory
    ): IntArray {
        builder
            .setLargeIcon(artwork)
            .setColor(Wl.Bg.toArgb())
            .setColorized(true)
        return super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
    }
}

private const val ARTWORK_SIZE = 512

/** The four-bar mark from `WlWaveMark`, at the sizes a 512 px square wants. */
private fun renderArtwork(): Bitmap {
    val bitmap = Bitmap.createBitmap(ARTWORK_SIZE, ARTWORK_SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val side = ARTWORK_SIZE.toFloat()

    val ground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, side, side,
            Wl.Bg.toArgb(), Wl.Surface.toArgb(),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, side, side, ground)

    val bars = 4
    val barWidth = 40f
    val gap = 26f
    val maxHeight = 200f
    val radius = barWidth / 2f
    val baseline = side / 2f + maxHeight / 2f
    var x = (side - (bars * barWidth + (bars - 1) * gap)) / 2f

    val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat(bars) { i ->
        val height = maxHeight * (0.32f + abs(sin((i + 1) * 0.9)).toFloat() * 0.68f)
        bar.color = if (i % 3 == 0 || i < 2) Wl.Accent.toArgb() else Wl.Accent700.toArgb()
        canvas.drawRoundRect(
            RectF(x, baseline - height, x + barWidth, baseline),
            radius,
            radius,
            bar
        )
        x += barWidth + gap
    }
    return bitmap
}
