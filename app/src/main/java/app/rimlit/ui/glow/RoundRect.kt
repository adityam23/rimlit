package app.rimlit.ui.glow

import android.graphics.Path
import kotlin.math.min

/**
 * A rectangle with each corner rounded to its own radius. As an outline it is the screen edge, which opens inward:
 * moved toward the screen, it shrinks.
 */
internal class RoundRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Top-left, top-right, bottom-right, bottom-left. */
    val cornerRadii: FloatArray,
) : OutlineShape {
    override val opensInward: Boolean get() = true

    override val cornerRadius: Float get() = cornerRadii.max()

    /** Scratch for [trace]: main thread only. */
    private val traceBounds = FloatArray(4)
    private val traceRadii = FloatArray(8)

    /**
     * Writes this shape grown by [by] on every side (negative shrinks it) into [bounds] (left, top, right, bottom) and
     * [radii] (x and y per corner, as `Path.addRoundRect` takes them). Each radius grows or shrinks with the sides, so
     * the result runs [by] away from this outline all round: down to a square corner, and at most half the shorter
     * side. Returns `false`, writing nothing, when it shrinks away entirely.
     */
    fun offset(by: Float, bounds: FloatArray, radii: FloatArray): Boolean {
        val width = right - left + 2f * by
        val height = bottom - top + 2f * by
        if (width <= 0f || height <= 0f) return false
        bounds[0] = left - by
        bounds[1] = top - by
        bounds[2] = right + by
        bounds[3] = bottom + by
        val maxRadius = min(width, height) / 2f
        for (corner in 0 until 4) {
            val radius = (cornerRadii[corner] + by).coerceIn(0f, maxRadius)
            radii[2 * corner] = radius
            radii[2 * corner + 1] = radius
        }
        return true
    }

    /** This shape grown by [by] on every side (negative shrinks it), as [offset] does; `null` when it shrinks away. */
    fun offset(by: Float): RoundRect? {
        val bounds = FloatArray(4)
        val radii = FloatArray(8)
        if (!offset(by, bounds, radii)) return null
        return RoundRect(bounds[0], bounds[1], bounds[2], bounds[3], FloatArray(4) { radii[2 * it] })
    }

    override fun trace(path: Path, by: Float): Boolean {
        path.rewind()
        if (!offset(-by, traceBounds, traceRadii)) return false
        val bounds = traceBounds
        path.addRoundRect(bounds[0], bounds[1], bounds[2], bounds[3], traceRadii, Path.Direction.CW)
        return true
    }
}
