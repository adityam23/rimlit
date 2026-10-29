package app.rimlit.ui.glow

import android.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Degrees in a full turn. */
private const val FULL_TURN = 360f

/**
 * The ring around a camera cutout as effects see it: the convex hull of the cutout's outline [polygon] (x, y pairs),
 * grown by [radius], i.e. every point within [radius] of the hull. It opens outward, and moves outward exactly: grown
 * further, it is the same hull grown by more, so it never crosses itself. For a convex cutout (a punch-hole, a pill,
 * every hand-drawn shape) it is the drawn ring itself; a concave one, such as a notch with flared shoulders, is
 * bridged across its hollows.
 */
internal class CutoutRing(polygon: FloatArray, val radius: Float) : OutlineShape {
    /** Hull corners, x, y pairs clockwise on screen. */
    private val hull = convexHull(polygon)
    private val corners = hull.size / 2

    /** Unit normal of each hull side (from corner i to the next), pointing out. */
    private val normalX = FloatArray(corners)
    private val normalY = FloatArray(corners)

    /** Direction of each side's normal, and the turn round the corner after it, in degrees clockwise on screen. */
    private val normalDegrees = FloatArray(corners)
    private val turnDegrees = FloatArray(corners)

    /** Length of each hull side. */
    private val sideLengths = FloatArray(corners)

    private val hullLeft = hull.filterIndexed { i, _ -> i % 2 == 0 }.min()
    private val hullTop = hull.filterIndexed { i, _ -> i % 2 == 1 }.min()
    private val hullRight = hull.filterIndexed { i, _ -> i % 2 == 0 }.max()
    private val hullBottom = hull.filterIndexed { i, _ -> i % 2 == 1 }.max()

    init {
        for (i in 0 until corners) {
            val next = (i + 1) % corners
            val dx = hull[2 * next] - hull[2 * i]
            val dy = hull[2 * next + 1] - hull[2 * i + 1]
            val length = hypot(dx, dy)
            sideLengths[i] = length
            // Clockwise on screen, the outside is to the left of travel: (dy, -dx). A lone point faces up.
            normalX[i] = if (length > 0f) dy / length else 0f
            normalY[i] = if (length > 0f) -dx / length else -1f
            normalDegrees[i] = Math.toDegrees(atan2(normalY[i], normalX[i]).toDouble()).toFloat()
        }
        for (i in 0 until corners) {
            val turn = ((normalDegrees[(i + 1) % corners] - normalDegrees[i]) % FULL_TURN + FULL_TURN) % FULL_TURN
            turnDegrees[i] = if (corners == 1) FULL_TURN else turn
        }
    }

    override val opensInward: Boolean get() = false

    override val width: Float get() = hullRight - hullLeft + 2f * radius

    override val height: Float get() = hullBottom - hullTop + 2f * radius

    override val cornerRadius: Float get() = min(width, height) / 2f

    override fun trace(path: Path, by: Float): Boolean {
        val r = radius + by
        path.rewind()
        path.moveTo(hull[0] + r * normalX[0], hull[1] + r * normalY[0])
        for (i in 0 until corners) {
            val next = (i + 1) % corners
            val x = hull[2 * next]
            val y = hull[2 * next + 1]
            path.lineTo(x + r * normalX[i], y + r * normalY[i])
            if (r > 0f) path.arcTo(x - r, y - r, x + r, y + r, normalDegrees[i], turnDegrees[i], false)
        }
        path.close()
        return true
    }

    /** The hull's sides and a whole turn of [radius]. */
    override val perimeter: Float = sideLengths.sum() + 2f * PI.toFloat() * radius

    /** Starts at the start of the first hull side; the normal points out. */
    override fun pointAt(distance: Float, point: FloatArray) {
        var rest = (distance % perimeter + perimeter) % perimeter
        for (i in 0 until corners) {
            // Along the side, then round the corner after it.
            val next = (i + 1) % corners
            if (rest <= sideLengths[i]) {
                val along = if (sideLengths[i] > 0f) rest / sideLengths[i] else 0f
                point[0] = hull[2 * i] + (hull[2 * next] - hull[2 * i]) * along + radius * normalX[i]
                point[1] = hull[2 * i + 1] + (hull[2 * next + 1] - hull[2 * i + 1]) * along + radius * normalY[i]
                point[2] = normalX[i]
                point[3] = normalY[i]
                return
            }
            rest -= sideLengths[i]
            val arc = radius * Math.toRadians(turnDegrees[i].toDouble()).toFloat()
            if (rest < arc) {
                val angle = Math.toRadians(normalDegrees[i].toDouble()).toFloat() + rest / radius
                point[2] = cos(angle)
                point[3] = sin(angle)
                point[0] = hull[2 * next] + radius * point[2]
                point[1] = hull[2 * next + 1] + radius * point[3]
                return
            }
            rest -= arc
        }
        // Rounding ran past the end: that is the start.
        pointAt(0f, point)
    }

    /** Moved out, a convex ring only grows rounder: it never crosses itself. */
    override fun smoothedFor(reach: Float): CutoutRing = this
}

/**
 * The convex hull of [polygon] (x, y pairs): its corners, x, y pairs clockwise on screen, without repeats or points
 * on a side. Monotone chain: the lower and upper chains of the points sorted by x, then y.
 */
internal fun convexHull(polygon: FloatArray): FloatArray {
    val order = (0 until polygon.size / 2).sortedWith(compareBy({ polygon[2 * it] }, { polygon[2 * it + 1] }))
    val chain = IntArray(2 * order.size + 1)
    var size = 0

    // y grows downward, so a positive cross product is a clockwise turn on screen; keep only those.
    fun turnsClockwise(a: Int, b: Int, c: Int): Boolean {
        val abx = polygon[2 * b] - polygon[2 * a]
        val aby = polygon[2 * b + 1] - polygon[2 * a + 1]
        val acx = polygon[2 * c] - polygon[2 * a]
        val acy = polygon[2 * c + 1] - polygon[2 * a + 1]
        return abx * acy - aby * acx > 0f
    }
    for (pass in 0 until 2) {
        val start = size
        val points = if (pass == 0) order else order.asReversed()
        for (p in points) {
            while (size >= start + 2 && !turnsClockwise(chain[size - 2], chain[size - 1], p)) size--
            chain[size++] = p
        }
        // The chain's last point starts the next one.
        size--
    }
    val corners = size.coerceAtLeast(1)
    return FloatArray(2 * corners) { polygon[2 * chain[it / 2] + it % 2] }
}
