package app.rimlit.ui.glow

import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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

    override val width: Float get() = right - left

    override val height: Float get() = bottom - top

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

    /** This shape with every corner rounded to at least [radius], and still at most half the shorter side. */
    fun withCornersAtLeast(radius: Float): RoundRect {
        val maxRadius = min(right - left, bottom - top) / 2f
        val radii = FloatArray(4) { max(cornerRadii[it], radius).coerceAtMost(maxRadius) }
        return RoundRect(left, top, right, bottom, radii)
    }

    /** Moving in shrinks a corner by as much, so each must be at least [reach] round. */
    override fun smoothedFor(reach: Float): RoundRect = withCornersAtLeast(reach)

    /** Four sides and four quarter circles. */
    override val perimeter: Float
        get() = 2f * (right - left + bottom - top) + (PI.toFloat() / 2f - 2f) * cornerRadii.sum()

    /** Starts at the start of the top side; the normal points inside. */
    override fun pointAt(distance: Float, point: FloatArray) {
        val length = perimeter
        var rest = (distance % length + length) % length
        for (side in 0 until 4) {
            // Along the side, then round the corner after it.
            val corner = (side + 1) % 4
            val straight = (centerX(corner) - centerX(side)) * SIDE_X[side] +
                (centerY(corner) - centerY(side)) * SIDE_Y[side]
            if (rest <= straight) return alongSide(side, rest, point)
            rest -= straight
            val radius = cornerRadii[corner]
            val quarter = PI.toFloat() / 2f
            // The arc starts square to the side: straight up from the top side, then a quarter turn per side.
            if (rest < radius * quarter) return roundCorner(corner, angle = (side - 1) * quarter + rest / radius, point)
            rest -= radius * quarter
        }
        // Rounding ran past the end: that is the start.
        alongSide(0, 0f, point)
    }

    /** Writes the point [distance] along [side] (0 top, then clockwise) and its inward normal into [point]. */
    private fun alongSide(side: Int, distance: Float, point: FloatArray) {
        val dx = SIDE_X[side]
        val dy = SIDE_Y[side]
        // The inward normal is (-dy, dx); the side runs one corner radius outward of the corner centres.
        point[0] = centerX(side) + dy * cornerRadii[side] + dx * distance
        point[1] = centerY(side) - dx * cornerRadii[side] + dy * distance
        point[2] = -dy
        point[3] = dx
    }

    /** Writes the point at [angle] (radians, screen coordinates) round [corner] and its inward normal into [point]. */
    private fun roundCorner(corner: Int, angle: Float, point: FloatArray) {
        val cos = cos(angle)
        val sin = sin(angle)
        point[0] = centerX(corner) + cornerRadii[corner] * cos
        point[1] = centerY(corner) + cornerRadii[corner] * sin
        point[2] = -cos
        point[3] = -sin
    }

    /** Centre of [corner]'s circle (0 top-left, then clockwise). */
    private fun centerX(corner: Int) =
        if (corner == 0 || corner == 3) left + cornerRadii[corner] else right - cornerRadii[corner]

    private fun centerY(corner: Int) = if (corner < 2) top + cornerRadii[corner] else bottom - cornerRadii[corner]
}

/** Clockwise direction of each side, from the top: right, down, left, up. */
private val SIDE_X = floatArrayOf(1f, 0f, -1f, 0f)
private val SIDE_Y = floatArrayOf(0f, 1f, 0f, -1f)
