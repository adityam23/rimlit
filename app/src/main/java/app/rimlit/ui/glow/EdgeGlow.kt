package app.rimlit.ui.glow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import app.rimlit.model.EdgeRegion
import app.rimlit.model.GlowPalette
import app.rimlit.model.GlowSpec
import app.rimlit.model.GlowStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor

/** Breathing rate of BASIC/GLOW at speed 1, in cycles per second. */
private const val PULSE_HZ = 0.7f

/** Blade laps per second at speed 1. */
private const val BLADE_LAPS_PER_SECOND = 0.45f

/** Gradient/rainbow flow around the outline at speed 1, in loops per second. */
private const val FLOW_LOOPS_PER_SECOND = 0.12f

/** Blade tail as a fraction of the outline length, and how many gradient stops shape its fade. */
private const val BLADE_TAIL = 0.25f
private const val BLADE_STEPS = 32

/** Arc length over which the blade's core cuts off at its head: a crisp front edge. */
private const val CRISP_EDGE_PX = 0.5f

/**
 * Pure edge-glow renderer shared by the overlay, the lock-screen activity and in-app previews.
 * No side effects and no settings access: everything comes from [spec].
 *
 * With [preview] the outline is a scaled stand-in for the display (proportional corners, simulated
 * camera hole) instead of the real window's corners and cutout.
 */
@Composable
fun EdgeGlow(spec: GlowSpec, modifier: Modifier = Modifier, preview: Boolean = false) {
    val view = LocalView.current
    val seconds = rememberFrameSeconds()
    Spacer(
        modifier.drawWithCache {
            val thicknessPx = spec.thicknessDp.dp.toPx()
            val glowPx = spec.glowWidthDp.dp.toPx()
            val shape = if (preview) {
                DisplayShape.preview(size.width, size.height)
            } else {
                DisplayShape.of(view, fallbackCornerPx = 24.dp.toPx())
            }
            val cutout = shape.cutout
            val geometry = if (spec.region == EdgeRegion.CUTOUT && cutout != null) {
                cutoutGeometry(cutout, gapPx = 4.dp.toPx(), strokePx = thicknessPx)
            } else {
                screenEdgeGeometry(size.width, size.height, shape, thicknessPx)
            }
            // Blurred once per size/spec here, never per frame (a zero-radius blur is invalid).
            val bloomMask = if (glowPx > 0f && size.width >= 1f && size.height >= 1f) {
                bloomMask(geometry, size.width.toInt(), size.height.toInt(), strokePx = thicknessPx * 2f, blurPx = glowPx)
            } else {
                null
            }
            val painter = GlowPainter(spec, geometry, thicknessPx, glowPx, bloomMask)
            onDrawBehind {
                // Reading the clock here (draw phase) redraws every frame without recomposing.
                val t = seconds.value
                drawIntoCanvas { painter.draw(it.nativeCanvas, t) }
            }
        },
    )
}

/** Seconds since first frame, advanced every frame. Read it only in draw to avoid recomposition. */
@Composable
private fun rememberFrameSeconds(): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { seconds.floatValue = (it - start) / 1_000_000_000f }
    }
    return seconds
}

/**
 * Holds the paints for one spec + geometry. A frame allocates only shaders: no blurs (the halo is the
 * pre-blurred [bloomMask], tinted) and one stroke per layer (the blade is a shader, not segments).
 */
private class GlowPainter(
    private val spec: GlowSpec,
    private val geometry: GlowGeometry,
    thicknessPx: Float,
    private val glowPx: Float,
    /** Soft halo under the core; `null` when glow width is 0. */
    private val bloomMask: Bitmap?,
) {
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = thicknessPx
        strokeJoin = Paint.Join.ROUND
    }

    private val bloom = Paint(Paint.FILTER_BITMAP_FLAG)

    /** The single color of a solid palette; `null` for multicolor palettes, which use a shader. */
    private val solidColor = (spec.palette as? GlowPalette.Colors)?.argb?.singleOrNull()

    fun draw(canvas: Canvas, t: Float) {
        val palette = paletteShader(t)
        when (spec.style) {
            GlowStyle.BASIC -> drawOutline(canvas, palette, pulse(t))
            GlowStyle.GLOW -> {
                drawBloom(canvas, palette, 0.8f * pulse(t))
                drawOutline(canvas, palette, pulse(t))
            }
            GlowStyle.BLADE -> {
                drawOutline(canvas, palette, 0.18f)
                drawBloom(canvas, comet(t, palette, edge = glowPx), 0.8f)
                drawOutline(canvas, comet(t, palette, edge = CRISP_EDGE_PX), 1f)
            }
        }
    }

    /** Multicolor palettes flow around the outline over time; `null` for a solid color. */
    private fun paletteShader(t: Float): Shader? =
        if (solidColor != null) null else arcLengthShader(geometry, spec.palette, phase = t * FLOW_LOOPS_PER_SECOND * spec.speed)

    /** Smooth breathing in [0.6, 1]. */
    private fun pulse(t: Float): Float =
        0.6f + 0.4f * (0.5f - 0.5f * cos(2f * PI.toFloat() * PULSE_HZ * spec.speed * t))

    /** A comet travelling at constant speed along the outline, colored by [palette] (or the solid color). */
    private fun comet(t: Float, palette: Shader?, edge: Float): Shader {
        val lap = t * BLADE_LAPS_PER_SECOND * spec.speed
        val head = (lap - floor(lap)) * geometry.length
        val tail = BLADE_TAIL * geometry.length
        val fade = bladeShader(geometry, head, tail, edge, solidColor ?: Color.WHITE, BLADE_STEPS)
        // DST_IN keeps the palette's colors where the fade is opaque.
        return if (palette == null) fade else ComposeShader(palette, fade, PorterDuff.Mode.DST_IN)
    }

    private fun drawOutline(canvas: Canvas, shader: Shader?, alpha: Float) {
        canvas.drawPath(geometry.path, core.tint(shader, alpha))
    }

    private fun drawBloom(canvas: Canvas, shader: Shader?, alpha: Float) {
        bloomMask?.let { canvas.drawBitmap(it, 0f, 0f, bloom.tint(shader, alpha)) }
    }

    /** Colors this paint with [shader], or the solid color when `null`, at [alpha] × brightness. */
    private fun Paint.tint(shader: Shader?, alpha: Float): Paint = apply {
        this.shader = shader
        if (shader == null && solidColor != null) color = solidColor
        this.alpha = (255 * alpha * spec.brightness).toInt().coerceIn(0, 255)
    }
}
