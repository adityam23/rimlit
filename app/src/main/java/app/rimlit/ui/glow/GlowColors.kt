package app.rimlit.ui.glow

import android.graphics.Color
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.core.graphics.ColorUtils
import app.rimlit.model.GlowPalette
import app.rimlit.model.vividColor
import kotlin.math.floor

/** Color of [palette] at outline position [t] (any real; wraps every 1.0), so gradients loop seamlessly. */
internal fun GlowPalette.colorAt(t: Float): Int {
    val u = t - floor(t)
    return when (this) {
        GlowPalette.Rainbow -> vividColor(u * 360f)

        is GlowPalette.Colors -> {
            if (argb.size == 1) return argb[0]
            val x = u * argb.size
            val i = x.toInt() % argb.size
            ColorUtils.blendARGB(argb[i], argb[(i + 1) % argb.size], x - floor(x))
        }
    }
}

/**
 * [palette] spread evenly by **arc length** (not by angle) around [geometry]. A plain sweep gradient would
 * bunch colors on the short edges of a tall screen; mapping each arc-length sample to its angle fixes that.
 * The sample buffers are reused, so a frame allocates only the shader.
 */
internal class PaletteFlow(private val geometry: GlowGeometry, private val palette: GlowPalette) {

    private val colors = IntArray(GlowGeometry.SAMPLES)
    private val gradient = OutlineGradient(GlowGeometry.SAMPLES)

    /** The palette shifted [phase] loops along the outline. */
    fun shader(phase: Float): Shader {
        for (i in colors.indices) colors[i] = palette.colorAt(i.toFloat() / colors.size + phase)
        return gradient.shader(geometry, geometry.sampleAngles, colors)
    }
}

/**
 * The blade comet as shaders: [color] fading in quadratically along a tail up to the head, then fading out
 * over an edge length, transparent elsewhere; stroking the whole outline with one draws the comet in a
 * single pass. The head travels clockwise round the outline or against it, and the tail trails behind it
 * either way. The stop colors never change: [moveTo] places the tail once per frame, and each [shader]
 * call only adds its own head edge.
 */
internal class BladeFade(private val geometry: GlowGeometry, color: Int) {

    private val angles = FloatArray(STEPS + 2)

    /** The stops in outline order for a blade travelling clockwise: the tail's fade up to the head, then its edge. */
    private val clockwiseColors = IntArray(STEPS + 2).also {
        for (step in 0..STEPS) {
            val f = step.toFloat() / STEPS
            it[step] = ColorUtils.setAlphaComponent(color, (Color.alpha(color) * f * f).toInt())
        }
        // Transparent [color], not transparent black: gradients blend unpremultiplied, so black would grey the fade.
        it[EDGE] = ColorUtils.setAlphaComponent(color, 0)
    }

    /** The same stops for a blade travelling against the outline: in outline order, the head's edge comes first. */
    private val counterclockwiseColors = clockwiseColors.reversedArray()
    private val gradient = OutlineGradient(STEPS + 2)
    private var head = 0f
    private var tail = 0f
    private var clockwise = true
    private var from = Float.NEGATIVE_INFINITY
    private var to = Float.POSITIVE_INFINITY

    /**
     * Places the head at arc distance [head], travelling [clockwise] round the outline or against it, with a [tail]
     * (arc length) trailing behind it. Only the part between arc distances [from] and [to] (unwrapped, `from <= to`)
     * shows, so a blade can grow out of one point and drain into another.
     */
    fun moveTo(head: Float, tail: Float, clockwise: Boolean, from: Float, to: Float) {
        this.head = head
        this.tail = tail
        this.clockwise = clockwise
        this.from = from
        this.to = to
        for (step in 0..STEPS) angles[slot(step)] = angleAt(head - sign * tail * (1f - step.toFloat() / STEPS))
    }

    /** The comet with its head fading out over [edge] (a hairline for a crisp core; wider for a soft halo). */
    fun shader(edge: Float): Shader {
        // The longest head edge that still ends before the tail starts, keeping the stops in outline order.
        val maxEdge = (geometry.length - tail - 1f).coerceAtLeast(0f)
        angles[slot(EDGE)] = angleAt(head + sign * edge.coerceAtMost(maxEdge))
        return gradient.shader(geometry, angles, if (clockwise) clockwiseColors else counterclockwiseColors)
    }

    /** Toward greater arc distances (clockwise) or smaller ones. */
    private val sign: Float get() = if (clockwise) 1f else -1f

    /** Angle of arc [distance] clamped to the part that shows; clamped stops stay in outline order. */
    private fun angleAt(distance: Float) = geometry.angleAt(distance.coerceIn(from, to))

    /**
     * Where stop [index] (0 the tail's end, [STEPS] the head, [EDGE] the head's edge) goes in the gradient, whose
     * stops run in outline order: reversed for a blade travelling against it.
     */
    private fun slot(index: Int) = if (clockwise) index else EDGE - index

    private companion object {
        /** Gradient stops shaping the tail's fade. */
        const val STEPS = 32

        /** The stop past the head, where the head's edge fades out. */
        const val EDGE = STEPS + 1
    }
}

/**
 * Sweep gradient around a geometry's centre through colors at angles (turn fractions). The stops come
 * in outline order, so angles increase with at most one wrap past 0; they are rotated to start at the
 * wrap, and the seam at angle 0/1 gets the color interpolated across it. Reuses its stop buffers, sized
 * for [size] stops plus the two seam stops.
 */
private class OutlineGradient(private val size: Int) {

    private val positions = FloatArray(size + 2)
    private val stops = IntArray(size + 2)

    fun shader(geometry: GlowGeometry, angles: FloatArray, colors: IntArray): Shader {
        val n = size
        var wrap = 0
        for (k in 1 until n) if (angles[k] < angles[wrap]) wrap = k
        for (k in 0 until n) {
            positions[k + 1] = angles[(wrap + k) % n]
            stops[k + 1] = colors[(wrap + k) % n]
        }
        val first = positions[1]
        val last = positions[n]
        val seamFraction = (1f - last) / ((1f - last) + first).coerceAtLeast(1e-4f)
        val seam = ColorUtils.blendARGB(stops[n], stops[1], seamFraction)
        positions[0] = 0f
        stops[0] = seam
        positions[n + 1] = 1f
        stops[n + 1] = seam
        // The shader copies the stops, so the buffers can be refilled for the next frame.
        return SweepGradient(geometry.center.x, geometry.center.y, stops, positions)
    }
}
