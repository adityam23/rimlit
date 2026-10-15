package app.rimlit.ui.glow

import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.RectF
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
    val positions = FloatArray(n + 2)
    val colors = IntArray(n + 2)
    geometry.angleOrder.forEachIndexed { k, i ->
        positions[k + 1] = geometry.sampleAngles[i]
        colors[k + 1] = palette.colorAt(i.toFloat() / n + phase)
    }
    // Close the seam at angle 0/1 with the color interpolated between the last and first samples.
    val first = positions[1]
    val last = positions[n]
    val seamFraction = (1f - last) / ((1f - last) + first).coerceAtLeast(1e-4f)
    val seam = ColorUtils.blendARGB(colors[n], colors[1], seamFraction)
    positions[0] = 0f
    colors[0] = seam
    positions[n + 1] = 1f
    colors[n + 1] = seam
    val bounds = RectF().also { geometry.path.computeBounds(it, true) }
    return SweepGradient(bounds.centerX(), bounds.centerY(), colors, positions)
}
