package app.rimlit.ui.glow

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

/** Blade tail as a fraction of the outline length. */
private const val BLADE_TAIL = 0.25f

/** Arc length over which the blade's core cuts off at its head: a crisp front edge. */
private const val CRISP_EDGE_PX = 0.5f

/**
 * Pure edge-glow renderer shared by the overlay, the lock-screen activity and in-app previews.
 * No side effects and no settings access: everything comes from [spec], played as [playback] says.
 *
 * With [preview] the outline is a scaled stand-in for the display (proportional corners, simulated
 * camera hole) instead of the real window's corners and cutout.
 */
@Composable
fun EdgeGlow(spec: GlowSpec, playback: Playback, modifier: Modifier = Modifier, preview: Boolean = false) {
    val view = LocalView.current
    // A still glow needs no clock at all: it draws once.
    val seconds = rememberFrameSeconds(running = !playback.still, maxFps = playback.maxFps)
    val layouts = remember { GlowLayoutCache() }
    Spacer(
        modifier.drawWithCache {
            val thicknessPx = spec.thicknessDp.dp.toPx()
            val glowPx = spec.glowWidthDp.dp.toPx()
            val shape = if (preview) {
                DisplayShape.preview(size.width, size.height)
            } else {
                DisplayShape.of(view, fallbackCornerPx = 24.dp.toPx())
            }
            val layout = layouts.get(
                GlowLayoutKey(
                    width = size.width,
                    height = size.height,
                    shape = shape,
                    ringCutout = spec.region == EdgeRegion.CUTOUT,
                    thicknessPx = thicknessPx,
                    cutoutGapPx = 4.dp.toPx(),
                    bloomPx = if (spec.style == GlowStyle.BASIC) 0f else glowPx,
                ),
            )
            val painter = GlowPainter(spec, playback, layout, thicknessPx, glowPx)
            onDrawBehind {
                // Reading the clock here (draw phase) redraws every frame without recomposing.
                val t = seconds.value
                drawIntoCanvas { painter.draw(it.nativeCanvas, t) }
            }
        },
    )
}

/**
 * Seconds since the first frame, while [running]; advanced at most [maxFps] times a second (`null`: every frame).
 * Read it only in draw to avoid recomposition: a frame that doesn't advance it isn't redrawn.
 */
@Composable
private fun rememberFrameSeconds(running: Boolean, maxFps: Int?): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running, maxFps) {
        if (!running) return@LaunchedEffect
        // A little under the cap's interval, so vsync jitter doesn't skip a frame that is due.
        val interval = maxFps?.let { (NANOS_PER_SECOND / it * (1 - FRAME_JITTER)).toLong() } ?: 0L
        val start = withFrameNanos { it }
        var shown = start
        while (true) {
            withFrameNanos { now ->
                if (now - shown >= interval) {
                    shown = now
                    seconds.floatValue = (now - start) / NANOS_PER_SECOND
                }
            }
        }
    }
    return seconds
}

private const val NANOS_PER_SECOND = 1_000_000_000f

/** Share of a capped frame interval a frame may arrive early by and still be drawn. */
private const val FRAME_JITTER = 0.12f

/**
 * Holds the paints and gradient buffers for one spec + layout. A frame allocates only shaders: no blurs
 * (the halo is the layout's pre-blurred mask, tinted) and one stroke per layer (the blade is a shader, not
 * segments).
 */
private class GlowPainter(
    private val spec: GlowSpec,
    private val playback: Playback,
    private val layout: GlowLayout,
    thicknessPx: Float,
    private val glowPx: Float,
) {
    private val geometry = layout.geometry

    /** The single color of a solid palette; `null` for multicolor palettes, which use a shader. */
    private val solidColor = (spec.palette as? GlowPalette.Colors)?.argb?.singleOrNull()

    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = thicknessPx
        strokeJoin = Paint.Join.ROUND
        solidColor?.let { color = it }
    }

    private val bloom = Paint(Paint.FILTER_BITMAP_FLAG).apply { solidColor?.let { color = it } }

    /** Multicolor palettes flow around the outline over time; `null` for a solid color. */
    private val flow = if (solidColor == null) PaletteFlow(geometry, spec.palette) else null

    private val blade = BladeFade(geometry, tail = BLADE_TAIL * geometry.length, color = solidColor ?: Color.WHITE)

    /** A still blade can't race, so it glows: the whole outline lit, with its halo. */
    private val style = if (playback.still && spec.style == GlowStyle.BLADE) GlowStyle.GLOW else spec.style

    fun draw(canvas: Canvas, t: Float) {
        val palette = paletteShader(t)
        when (style) {
            GlowStyle.BASIC -> drawOutline(canvas, palette, pulse(t))

            GlowStyle.GLOW -> {
                drawBloom(canvas, palette, 0.8f * pulse(t))
                drawOutline(canvas, palette, pulse(t))
            }

            GlowStyle.BLADE -> {
                drawOutline(canvas, palette, 0.18f)
                val lap = t * BLADE_LAPS_PER_SECOND * spec.speed
                blade.moveTo(head = (lap - floor(lap)) * geometry.length)
                drawBloom(canvas, comet(blade.shader(edge = glowPx), palette), 0.8f)
                drawOutline(canvas, comet(blade.shader(edge = CRISP_EDGE_PX), palette), 1f)
            }
        }
    }

    private fun paletteShader(t: Float): Shader? = flow?.shader(phase = t * FLOW_LOOPS_PER_SECOND * spec.speed)

    /** Smooth breathing in [0.6, 1]; full and steady when still. */
    private fun pulse(t: Float): Float = if (playback.still) {
        1f
    } else {
        0.6f + 0.4f * (0.5f - 0.5f * cos(2f * PI.toFloat() * PULSE_HZ * spec.speed * t))
    }

    /** The blade's [fade] colored by [palette]; a solid color is already baked into the fade. */
    private fun comet(fade: Shader, palette: Shader?): Shader =
        // DST_IN keeps the palette's colors where the fade is opaque.
        if (palette == null) fade else ComposeShader(palette, fade, PorterDuff.Mode.DST_IN)

    private fun drawOutline(canvas: Canvas, shader: Shader?, alpha: Float) {
        canvas.drawPath(geometry.path, core.tint(shader, alpha))
    }

    private fun drawBloom(canvas: Canvas, shader: Shader?, alpha: Float) {
        layout.bloom?.draw(canvas, bloom.tint(shader, alpha))
    }

    /** Colors this paint with [shader] (`null` keeps the solid color set at construction), at [alpha] × brightness. */
    private fun Paint.tint(shader: Shader?, alpha: Float): Paint = apply {
        this.shader = shader
        this.alpha = (255 * alpha * playback.brightness).toInt().coerceIn(0, 255)
    }
}
