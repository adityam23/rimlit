package app.rimlit.ui.glow

import android.graphics.Color
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.core.graphics.ColorUtils
import app.rimlit.model.GlowPalette
import kotlin.math.floor

/** Color of [palette] at outline position [t] (any real; wraps every 1.0), so gradients loop seamlessly. */
internal fun GlowPalette.colorAt(t: Float): Int {
    val u = t - floor(t)
    return when (this) {
        GlowPalette.Rainbow -> ColorUtils.HSLToColor(floatArrayOf(u * 360f, 1f, 0.55f))
        is GlowPalette.Colors -> {
            if (argb.size == 1) return argb[0]
            val x = u * argb.size
            val i = x.toInt() % argb.size
            ColorUtils.blendARGB(argb[i], argb[(i + 1) % argb.size], x - floor(x))
        }
    }
}

/** True when the palette changes along the outline (so it needs a shader and can flow). */
internal val GlowPalette.isMulticolor: Boolean
    get() = this is GlowPalette.Rainbow || (this is GlowPalette.Colors && argb.size > 1)

/**
 * A sweep shader that spreads [palette] evenly by **arc length** (not by angle) around [geometry],
 * shifted by [phase]. A plain sweep gradient would bunch colors on the short edges of a tall screen;
 * mapping each arc-length sample to its angle fixes that.
 */
internal fun arcLengthShader(geometry: GlowGeometry, palette: GlowPalette, phase: Float): Shader {
    val n = GlowGeometry.SAMPLES
    val colors = IntArray(n) { palette.colorAt(it.toFloat() / n + phase) }
    return outlineGradient(geometry, geometry.sampleAngles, colors)
}

/**
 * The blade comet as shaders: [color] fading in quadratically along the tail up to the head, then fading
 * out over an edge length, transparent elsewhere; stroking the whole outline with one draws the comet in a
 * single pass. The stop colors never change: [moveTo] places the tail once per frame, and each [shader]
 * call only adds its own head edge.
 */
internal class BladeFade(private val geometry: GlowGeometry, color: Int) {

    private val angles = FloatArray(STEPS + 2)
    private val colors = IntArray(STEPS + 2).also {
        for (step in 0..STEPS) {
            val f = step.toFloat() / STEPS
            it[step] = ColorUtils.setAlphaComponent(color, (Color.alpha(color) * f * f).toInt())
        }
        // Transparent [color], not transparent black: gradients blend unpremultiplied, so black would grey the fade.
        it[STEPS + 1] = ColorUtils.setAlphaComponent(color, 0)
    }
    private var head = 0f

    /** Places the head at arc distance [head], with the tail trailing it by [tail]. */
    fun moveTo(head: Float, tail: Float) {
        this.head = head
        for (step in 0..STEPS) angles[step] = geometry.angleAt(head - tail * (1f - step.toFloat() / STEPS))
    }

    /** The comet with its head fading out over [edge] (a hairline for a crisp core; wider for a soft halo). */
    fun shader(edge: Float): Shader {
        angles[STEPS + 1] = geometry.angleAt(head + edge)
        return outlineGradient(geometry, angles, colors)
    }

    private companion object {
        /** Gradient stops shaping the tail's fade. */
        const val STEPS = 32
    }
}

/**
 * Sweep gradient around [geometry]'s centre through [colors] at [angles] (turn fractions). The stops come
 * in outline order, so angles increase with at most one wrap past 0; they are rotated to start at the
 * wrap, and the seam at angle 0/1 gets the color interpolated across it.
 */
private fun outlineGradient(geometry: GlowGeometry, angles: FloatArray, colors: IntArray): Shader {
    val n = angles.size
    val wrap = angles.indices.minBy { angles[it] }
    val positions = FloatArray(n + 2)
    val stops = IntArray(n + 2)
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
    return SweepGradient(geometry.center.x, geometry.center.y, stops, positions)
}
