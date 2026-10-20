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
 * No side effects and no settings access: everything comes from [spec].
 *
 * With [preview] the outline is a scaled stand-in for the display (proportional corners, simulated
 * camera hole) instead of the real window's corners and cutout.
 */
@Composable
fun EdgeGlow(spec: GlowSpec, modifier: Modifier = Modifier, preview: Boolean = false) {
    val view = LocalView.current
    val seconds = rememberFrameSeconds()
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
            val painter = GlowPainter(spec, layout, thicknessPx, glowPx)
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
 * Holds the paints for one spec + layout. A frame allocates only shaders: no blurs (the halo is the
 * layout's pre-blurred mask, tinted) and one stroke per layer (the blade is a shader, not segments).
 */
private class GlowPainter(
    private val spec: GlowSpec,
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

    private val blade = BladeFade(geometry, tail = BLADE_TAIL * geometry.length, color = solidColor ?: Color.WHITE)

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
                val lap = t * BLADE_LAPS_PER_SECOND * spec.speed
                blade.moveTo(head = (lap - floor(lap)) * geometry.length)
                drawBloom(canvas, comet(blade.shader(edge = glowPx), palette), 0.8f)
                drawOutline(canvas, comet(blade.shader(edge = CRISP_EDGE_PX), palette), 1f)
            }
        }
    }

    /** Multicolor palettes flow around the outline over time; `null` for a solid color. */
    private fun paletteShader(t: Float): Shader? =
        if (solidColor != null) null else arcLengthShader(geometry, spec.palette, phase = t * FLOW_LOOPS_PER_SECOND * spec.speed)

    /** Smooth breathing in [0.6, 1]. */
    private fun pulse(t: Float): Float =
        0.6f + 0.4f * (0.5f - 0.5f * cos(2f * PI.toFloat() * PULSE_HZ * spec.speed * t))

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
        this.alpha = (255 * alpha * spec.brightness).toInt().coerceIn(0, 255)
    }
}
