package app.rimlit.ui.glow

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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

/** Blade tail as a fraction of the outline length, and how many fading steps draw it. */
private const val BLADE_TAIL = 0.25f
private const val BLADE_STEPS = 32

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
            val painter = GlowPainter(spec, geometry, thicknessPx, glowPx)
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

/** Holds all paint/path objects for one spec + geometry, so drawing a frame allocates nothing but a shader. */
private class GlowPainter(
    private val spec: GlowSpec,
    private val geometry: GlowGeometry,
    thicknessPx: Float,
    glowPx: Float,
) {
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = thicknessPx
        // BUTT, not ROUND: blade segments abut exactly; round caps overlap and stack alpha into "beads".
        // The closed outline has no ends, so caps never show there.
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.ROUND
    }

    /** Soft halo under the core; `null` when glow width is 0 (a zero-radius blur is invalid). */
    private val bloom: Paint? = if (glowPx > 0f) {
        Paint(core).apply {
            strokeWidth = thicknessPx * 2f
            maskFilter = BlurMaskFilter(glowPx, BlurMaskFilter.Blur.NORMAL)
        }
    } else {
        null
    }

    private val segment = Path()
    private val solidColor = (spec.palette as? GlowPalette.Colors)?.argb?.singleOrNull()

    fun draw(canvas: Canvas, t: Float) {
        applyColors(t)
        when (spec.style) {
            GlowStyle.BASIC -> drawOutline(canvas, core, pulse(t))
            GlowStyle.GLOW -> {
                bloom?.let { drawOutline(canvas, it, 0.8f * pulse(t)) }
                drawOutline(canvas, core, pulse(t))
            }
            GlowStyle.BLADE -> {
                drawOutline(canvas, core, 0.18f)
                drawBlade(canvas, t)
            }
        }
    }

    /** Solid palettes set a plain color; multicolor palettes get an arc-length shader that flows over time. */
    private fun applyColors(t: Float) {
        if (solidColor != null) {
            core.color = solidColor
            bloom?.color = solidColor
        } else {
            val shader = arcLengthShader(geometry, spec.palette, phase = t * FLOW_LOOPS_PER_SECOND * spec.speed)
            core.shader = shader
            bloom?.shader = shader
        }
    }

    /** Smooth breathing in [0.6, 1]. */
    private fun pulse(t: Float): Float =
        0.6f + 0.4f * (0.5f - 0.5f * cos(2f * PI.toFloat() * PULSE_HZ * spec.speed * t))

    private fun drawOutline(canvas: Canvas, paint: Paint, alpha: Float) {
        paint.alpha = (255 * alpha * spec.brightness).toInt().coerceIn(0, 255)
        canvas.drawPath(geometry.path, paint)
    }

    /** A comet travelling at constant speed along the outline: [BLADE_STEPS] segments fading toward the tail. */
    private fun drawBlade(canvas: Canvas, t: Float) {
        val length = geometry.length
        val lap = t * BLADE_LAPS_PER_SECOND * spec.speed
        val head = (lap - floor(lap)) * length
        val tail = BLADE_TAIL * length
        for (step in 0 until BLADE_STEPS) {
            val from = head - tail * (1f - step.toFloat() / BLADE_STEPS)
            val to = head - tail * (1f - (step + 1f) / BLADE_STEPS)
            val fade = ((step + 1f) / BLADE_STEPS).let { it * it }
            bloom?.let { drawSegment(canvas, it, from, to, 0.8f * fade) }
            drawSegment(canvas, core, from, to, fade)
        }
    }

    /** Draws outline distance [from]..[to] (may cross the path start; wrapped into two pieces). */
    private fun drawSegment(canvas: Canvas, paint: Paint, from: Float, to: Float, alpha: Float) {
        val length = geometry.length
        val start = (from % length + length) % length
        val end = (to % length + length) % length
        paint.alpha = (255 * alpha * spec.brightness).toInt().coerceIn(0, 255)
        if (end >= start) {
            drawPiece(canvas, paint, start, end)
        } else {
            drawPiece(canvas, paint, start, length)
            drawPiece(canvas, paint, 0f, end)
        }
    }

    private fun drawPiece(canvas: Canvas, paint: Paint, start: Float, end: Float) {
        segment.reset()
        if (geometry.measure.getSegment(start, end, segment, true)) canvas.drawPath(segment, paint)
    }
}
