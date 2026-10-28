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
import app.rimlit.model.GlowPalette
import app.rimlit.model.GlowSpec
import app.rimlit.model.GlowStyle
import app.rimlit.ui.glow.particles.rememberParticleLayer
import kotlinx.coroutines.delay
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
    val layouts = remember { BuildCache(::GlowLayout) }
    val particles = rememberParticleLayer()
    Spacer(
        modifier.drawWithCache {
            val thicknessPx = spec.thicknessDp.dp.toPx()
            val glowPx = spec.glowWidthDp.dp.toPx()
            val display = if (preview) {
                DisplayShape.preview(size.width, size.height)
            } else {
                DisplayShape.of(view)
            }
            // A preview is a scaled-down screen, so custom corners and cutouts scale with it to keep their proportion.
            val scale = if (preview) size.width / view.rootView.width.coerceAtLeast(1) else 1f
            val pxPerDp = 1.dp.toPx() * scale
            val shape = display
                .let { d -> spec.cornerRadiusDp?.let { d.withCornerRadius(it * pxPerDp) } ?: d }
                .let { d -> spec.customCutout?.let { d.withCustomCutout(it, pxPerDp, size.width, size.height) } ?: d }
            val layout = layouts.get(
                GlowLayoutKey(
                    width = size.width,
                    height = size.height,
                    shape = shape,
                    region = spec.region,
                    thicknessPx = thicknessPx,
                    cutoutGapPx = 4.dp.toPx(),
                    bloomPx = if (spec.style == GlowStyle.BASIC) 0f else glowPx,
                ),
            )
            val painter = GlowPainter(spec, playback, layout, thicknessPx, glowPx)
            val particlePainter = particles.painter(spec, playback, size, pxPerDp = 1.dp.toPx() * scale)
            onDrawBehind {
                // Reading the clock here (draw phase) redraws every frame without recomposing.
                val t = seconds.value
                drawIntoCanvas {
                    // Particles go beneath the edge.
                    particlePainter?.draw(it.nativeCanvas, t)
                    painter.draw(it.nativeCanvas, t)
                }
            }
        },
    )
}

/**
 * Seconds since the first frame, while [running]; advanced at most [maxFps] times a second (`null`: every frame).
 * Read it only in draw to avoid recomposition. Under a cap the clock sleeps between frames, so the app isn't woken
 * on the vsyncs it skips.
 */
@Composable
private fun rememberFrameSeconds(running: Boolean, maxFps: Int?): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running, maxFps) {
        if (!running) return@LaunchedEffect
        // A little under the cap's interval, so vsync jitter doesn't push a frame a whole vsync late.
        val pauseMs = maxFps?.let { (MILLIS_PER_SECOND / it * (1 - FRAME_JITTER)).toLong() }
        // Carries on from where it was: a changed cap restarts this effect, not the animation.
        val start = withFrameNanos { it } - (seconds.floatValue * NANOS_PER_SECOND).toLong()
        while (true) {
            if (pauseMs != null) delay(pauseMs)
            withFrameNanos { now -> seconds.floatValue = (now - start) / NANOS_PER_SECOND }
        }
    }
    return seconds
}

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MILLIS_PER_SECOND = 1_000f

/** Share of a capped frame interval the clock wakes early by, so the frame after it isn't a vsync late. */
private const val FRAME_JITTER = 0.12f

/**
 * Holds the paints and gradient buffers for one spec + layout. A frame allocates only shaders: no blurs
 * (each halo is its outline's pre-blurred mask, tinted) and one stroke per layer and outline (the blade is a
 * shader, not segments).
 */
private class GlowPainter(
    private val spec: GlowSpec,
    private val playback: Playback,
    layout: GlowLayout,
    thicknessPx: Float,
    private val glowPx: Float,
) {
    /** The single color of a solid palette; `null` for multicolor palettes, which use a shader. */
    private val solidColor = (spec.palette as? GlowPalette.Colors)?.argb?.singleOrNull()

    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = thicknessPx
        strokeJoin = Paint.Join.ROUND
        solidColor?.let { color = it }
    }

    private val bloom = Paint(Paint.FILTER_BITMAP_FLAG).apply { solidColor?.let { color = it } }

    /** A still blade can't race, so it glows: the whole outline lit, with its halo. */
    private val style = if (playback.still && spec.style == GlowStyle.BLADE) GlowStyle.GLOW else spec.style

    private val outlines = layout.outlines.map(::OutlinePainter)

    fun draw(canvas: Canvas, t: Float) {
        for (outline in outlines) outline.draw(canvas, t)
    }

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

    /** Colors this paint with [shader] (`null` keeps the solid color set at construction), at [alpha] × brightness. */
    private fun Paint.tint(shader: Shader?, alpha: Float): Paint = apply {
        this.shader = shader
        this.alpha = (255 * alpha * playback.brightness).toInt().coerceIn(0, 255)
    }

    /**
     * One outline's gradient buffers. Every outline plays on the same clock: its palette flows around it on its
     * own, and its blade sits at the same fraction of a lap as the others', so blades race in step.
     */
    private inner class OutlinePainter(private val outline: GlowOutline) {
        private val geometry = outline.geometry

        /** Multicolor palettes flow around the outline over time; `null` for a solid color. */
        private val flow = if (solidColor == null) PaletteFlow(geometry, spec.palette) else null

        /** Built on the first blade frame: a GLOW, BASIC or still BLADE glow never needs its gradient buffers. */
        private val blade by lazy(LazyThreadSafetyMode.NONE) {
            BladeFade(geometry, tail = BLADE_TAIL * geometry.length, color = solidColor ?: Color.WHITE)
        }

        fun draw(canvas: Canvas, t: Float) {
            val palette = flow?.shader(phase = t * FLOW_LOOPS_PER_SECOND * spec.speed)
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

        private fun drawOutline(canvas: Canvas, shader: Shader?, alpha: Float) {
            canvas.drawPath(geometry.path, core.tint(shader, alpha))
        }

        private fun drawBloom(canvas: Canvas, shader: Shader?, alpha: Float) {
            outline.bloom?.draw(canvas, bloom.tint(shader, alpha))
        }
    }
}
