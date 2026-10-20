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
 * The blade comet as a shader: [color] fading in quadratically over [tail] arc length up to [head], then
 * fading out over [edge] (a hairline for a crisp core; wider for the soft halo), transparent elsewhere.
 * Stroking the whole outline with it draws the comet in one pass.
 */
internal fun bladeShader(geometry: GlowGeometry, head: Float, tail: Float, edge: Float, color: Int, steps: Int): Shader {
    val angles = FloatArray(steps + 2)
    val colors = IntArray(steps + 2)
    for (step in 0..steps) {
        val f = step.toFloat() / steps
        angles[step] = geometry.angleAt(head - tail * (1f - f))
        colors[step] = ColorUtils.setAlphaComponent(color, (Color.alpha(color) * f * f).toInt())
    }
    angles[steps + 1] = geometry.angleAt(head + edge)
    colors[steps + 1] = Color.TRANSPARENT
    return outlineGradient(geometry, angles, colors)
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
