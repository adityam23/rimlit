package app.rimlit.ui.glow.effects

import android.graphics.Canvas
import android.graphics.Path
import app.rimlit.ui.glow.OutlinePart
import app.rimlit.ui.glow.OutlineShape
import kotlin.math.PI
import kotlin.math.cos
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

/**
 * Points the wave bends per wave: one every [SAMPLE_SPACING_PX] of arc, but between the fewest that keep a wave
 * smooth and the most worth drawing; and at most [MAX_SAMPLES] round the whole outline.
 */
private const val SAMPLE_SPACING_PX = 4f
private const val MIN_SAMPLES_PER_WAVE = 16
private const val MAX_SAMPLES_PER_WAVE = 32
private const val MAX_SAMPLES = 4096

/**
 * A sine ripple travelling along the outline over its halo: the line swings toward the open side and back, a whole
 * number of waves per lap so it joins up across the path's start. Still, it is the steady outline.
 */
internal class WaveEffect(private val brush: OutlineBrush) : GlowEffectPainter {
    /** Only a moving wave bends, so a still one never builds its track. */
    private val track by lazy(LazyThreadSafetyMode.NONE) { brush.outline.part(Track) }
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
        track.bend(phase) { i, x, y -> if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
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

/**
 * The line a wave of [amplitude] bends along [shape]: points evenly spaced around it, each with its unit normal toward
 * the open side. The points follow [shape] smoothed for 2 × [amplitude], the farthest the wave swings (see
 * [OutlineShape.smoothedFor]: the screen edge's corners rounded to at least that), so the normals turn smoothly and
 * the wave never swings past a corner's centre into a loop, however sharp the outline's own corners.
 */
internal class WaveTrack(shape: OutlineShape, val amplitude: Float) {
    private val rounded = shape.smoothedFor(2f * amplitude)

    /** Length of the track, which the waves divide. */
    val length = rounded.perimeter
    val waves = waveCount(length, wavelength = amplitude * WAVELENGTH_AMPLITUDES)
    val count = (length / waves / SAMPLE_SPACING_PX).roundToInt()
        .coerceIn(MIN_SAMPLES_PER_WAVE, MAX_SAMPLES_PER_WAVE)
        .let { (it * waves).coerceAtMost(MAX_SAMPLES) }
    val x = FloatArray(count)
    val y = FloatArray(count)
    val normalX = FloatArray(count)
    val normalY = FloatArray(count)

    /** Sine and cosine of each point's place in its wave, so a frame only rotates them by the phase: see [bend]. */
    val sinAt = FloatArray(count)
    val cosAt = FloatArray(count)

    init {
        val point = FloatArray(4)
        for (i in 0 until count) {
            rounded.pointAt(length * i / count, point)
            x[i] = point[0]
            y[i] = point[1]
            normalX[i] = point[2]
            normalY[i] = point[3]
            val angle = 2f * PI.toFloat() * waves * i / count
            sinAt[i] = sin(angle)
            cosAt[i] = cos(angle)
        }
    }

    /**
     * Hands each point of the line bent into the wave [phase] waves along to [to], in order round the track: the
     * [waveOffset] at each point, from one sine and cosine per frame (sin(a − p) = sin a cos p − cos a sin p).
     */
    inline fun bend(phase: Float, to: (index: Int, x: Float, y: Float) -> Unit) {
        val shift = 2f * PI.toFloat() * phase
        val sinShift = sin(shift)
        val cosShift = cos(shift)
        for (i in 0 until count) {
            val offset = amplitude * (1f + sinAt[i] * cosShift - cosAt[i] * sinShift)
            to(i, x[i] + normalX[i] * offset, y[i] + normalY[i] * offset)
        }
    }
}

private val Track = OutlinePart {
    val amplitude = it.geometry.shape.fit(it.key.thicknessPx * AMPLITUDE_THICKNESSES, MAX_AMPLITUDE_SHARE)
    WaveTrack(it.geometry.shape, amplitude)
}
