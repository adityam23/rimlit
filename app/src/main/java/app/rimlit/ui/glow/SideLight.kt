package app.rimlit.ui.glow

import app.rimlit.model.ALL_SIDES
import app.rimlit.model.ScreenSide
import kotlin.math.max

/** The sides in [RoundRect]'s order, clockwise from the top: side `i` runs from corner `i` to corner `i + 1`. */
private val CLOCKWISE = arrayOf(ScreenSide.TOP, ScreenSide.RIGHT, ScreenSide.BOTTOM, ScreenSide.LEFT)

/**
 * How much of the screen-edge glow shows at each point of the window when only some sides of [edge] (the outline the
 * glow is drawn along) are [lit], in plain floats so it is testable off-device: the geometry of [SideMask].
 *
 * Each point belongs to a corner of [edge] or to its nearest side. A corner, the square between its circle's centre and
 * the window's corner, shows only where both its sides are lit. A lit side shows fully, except toward an unlit
 * neighbour, where it fades out over [taperPx], ending where the corner's curve begins. Away from the outline (where
 * effects move toward the open side: echo rings, sparks, the wave), a side's fade ends at the line bisecting the corner
 * instead, so whatever an effect draws inward fades out with the side it came from rather than being cut.
 */
internal class SideLight private constructor(
    private val edge: RoundRect,
    private val lit: BooleanArray,
    taperPx: Float,
) {
    private val taperPx = taperPx.coerceAtLeast(MIN_TAPER_PX)

    /** Scratch for [at]: each side's distance inward. Main thread only. */
    private val distances = FloatArray(CLOCKWISE.size)

    /** How much of the glow shows at ([x], [y]), from 0 (none) to 1 (all). Allocates nothing. */
    fun at(x: Float, y: Float): Float {
        distances[0] = y - edge.top
        distances[1] = edge.right - x
        distances[2] = edge.bottom - y
        distances[3] = x - edge.left
        return inCorner() ?: alongSide()
    }

    /** How much shows at the point [distances] place, when it lies in a corner; `null` when it doesn't. */
    private fun inCorner(): Float? {
        for (corner in CLOCKWISE.indices) {
            val before = (corner + CLOCKWISE.size - 1) % CLOCKWISE.size
            val radius = edge.cornerRadii[corner]
            if (distances[before] < radius && distances[corner] < radius) {
                return if (lit[before] && lit[corner]) 1f else 0f
            }
        }
        return null
    }

    /** How much shows at the point [distances] place, along its nearest side. */
    private fun alongSide(): Float {
        var side = 0
        for (i in 1 until CLOCKWISE.size) if (distances[i] < distances[side]) side = i
        if (!lit[side]) return 0f
        val before = (side + CLOCKWISE.size - 1) % CLOCKWISE.size
        val after = (side + 1) % CLOCKWISE.size
        // How far along the side the point is from each end: from where the corner's curve ends, on the outline and
        // out past it; farther in, from the line bisecting the corner, as deep as the point.
        val depth = distances[side]
        val fromStart = if (lit[before]) 1f else taper(distances[before] - max(depth, edge.cornerRadii[side]))
        val fromEnd = if (lit[after]) 1f else taper(distances[after] - max(depth, edge.cornerRadii[after]))
        return fromStart * fromEnd
    }

    /** A smooth fade from 0, [along] 0 or less, to 1, [along] [taperPx] or more. */
    private fun taper(along: Float): Float {
        val x = (along / taperPx).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    companion object {
        /** The shortest fade: a pixel, so a fade is never a division by zero. */
        private const val MIN_TAPER_PX = 1f

        /** The light of [edge] with only [sides] lit, fading over [taperPx]; `null` when every side is lit. */
        fun of(edge: RoundRect, sides: Set<ScreenSide>, taperPx: Float): SideLight? =
            if (sides.containsAll(ALL_SIDES)) {
                null
            } else {
                SideLight(edge, BooleanArray(CLOCKWISE.size) { CLOCKWISE[it] in sides }, taperPx)
            }
    }
}
