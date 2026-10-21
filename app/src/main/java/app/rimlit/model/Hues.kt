package app.rimlit.model

import androidx.core.graphics.ColorUtils
import kotlin.math.floor

/** One fully saturated colour per whole degree of hue: the rainbow and the hue slider both pick from these. */
private val VividByDegree = IntArray(360) { ColorUtils.HSLToColor(floatArrayOf(it.toFloat(), 1f, 0.55f)) }

/** The vivid colour at [hue] degrees (any real; wraps every 360). Allocation-free, so it is safe per frame. */
fun vividColor(hue: Float): Int {
    val h = hue - 360f * floor(hue / 360f)
    val i = h.toInt() % 360
    return ColorUtils.blendARGB(VividByDegree[i], VividByDegree[(i + 1) % 360], h - floor(h))
}

/** Hue of [argb] in degrees, in [0, 360). */
fun hueOf(argb: Int): Float = FloatArray(3).also { ColorUtils.colorToHSL(argb, it) }[0]
