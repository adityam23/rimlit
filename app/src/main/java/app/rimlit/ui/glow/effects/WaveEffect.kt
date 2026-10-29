package app.rimlit.ui.glow.effects

import android.graphics.Canvas
import android.graphics.Path
import app.rimlit.ui.glow.GlowOutline
import app.rimlit.ui.glow.OutlinePart
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Waves passing a point per second at speed 1. */
private const val WAVES_PER_SECOND = 1f

/** Wave amplitude, in line thicknesses. */
private const val AMPLITUDE_THICKNESSES = 2f

/** Largest amplitude, as a share of the outline's shorter side, so a small cutout ring or preview isn't swamped. */
private const val MAX_AMPLITUDE_SHARE = 0.04f

/** Wavelength, in amplitudes. */
private const val WAVELENGTH_AMPLITUDES = 16f

/** Fewest whole waves around an outline, so even a small ring ripples. */
private const val MIN_WAVES = 4

/** Halo opacity under the wave. */
private const val HALO_ALPHA = 0.6f

/** Arc length between the points the wave bends; fine enough to stay smooth, coarse enough to stay cheap. */
private const val SAMPLE_SPACING_PX = 4f
private const val MIN_SAMPLES = 32
private const val MAX_SAMPLES = 4096

/**
 * A sine ripple travelling along the outline over its halo: the line swings toward the open side and back, a whole
 * number of waves per lap so it joins up across the path's start. Still, it is the steady outline.
 */
internal class WaveEffect(private val brush: OutlineBrush) : GlowEffectPainter {
    private val samples = brush.outline.part(Samples)
    private val length = brush.geometry.length
    private val amplitude = brush.geometry.shape.fit(brush.thicknessPx * AMPLITUDE_THICKNESSES, MAX_AMPLITUDE_SHARE)
    private val waves = waveCount(length, wavelength = amplitude * WAVELENGTH_AMPLITUDES)
    private val path = Path()

    override fun draw(canvas: Canvas, t: Float) {
        val palette = brush.palette(t)
        brush.drawBloom(canvas, palette, HALO_ALPHA)
        if (brush.still) {
            brush.drawOutline(canvas, palette, 1f)
        } else {
            bend(phase = t * WAVES_PER_SECOND * brush.spec.speed)
            brush.drawPath(canvas, path, palette, 1f)
        }
    }

    /** Rebuilds [path] as the outline bent into the wave [phase] waves along; reuses the path, allocating nothing. */
    private fun bend(phase: Float) {
        path.rewind()
        for (i in 0 until samples.count) {
            val offset = waveOffset(length * i / samples.count, length, waves, phase, amplitude)
            val x = samples.x[i] + samples.normalX[i] * offset
            val y = samples.y[i] + samples.normalY[i] * offset
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
}

/** Whole waves around an outline [length] long, each about [wavelength]: a whole number, so the ripple joins up. */
internal fun waveCount(length: Float, wavelength: Float): Int =
    (length / wavelength.coerceAtLeast(1f)).roundToInt().coerceAtLeast(MIN_WAVES)

/**
 * How far the wave sits off the outline toward the open side at arc [distance], with [waves] whole waves around an
 * outline [length] long, travelled [phase] waves along: in [0, 2 × [amplitude]], and the same at 0 and [length].
 */
internal fun waveOffset(distance: Float, length: Float, waves: Int, phase: Float, amplitude: Float): Float =
    amplitude * (1f + sin(2f * PI.toFloat() * (waves * distance / length - phase)))

/** Points evenly spaced along an outline, each with its unit normal toward the open side. */
private class WaveSamples(outline: GlowOutline) {
    val count = (outline.geometry.length / SAMPLE_SPACING_PX).toInt().coerceIn(MIN_SAMPLES, MAX_SAMPLES)
    val x = FloatArray(count)
    val y = FloatArray(count)
    val normalX = FloatArray(count)
    val normalY = FloatArray(count)

    init {
        val position = FloatArray(2)
        val tangent = FloatArray(2)
        // The tangent runs clockwise on screen, so (-ty, tx) points inside the outline.
        val side = if (outline.opensInward) 1f else -1f
        for (i in 0 until count) {
            outline.geometry.pointAt(outline.geometry.length * i / count, position, tangent)
            x[i] = position[0]
            y[i] = position[1]
            normalX[i] = -tangent[1] * side
            normalY[i] = tangent[0] * side
        }
    }
}

private val Samples = OutlinePart(::WaveSamples)
