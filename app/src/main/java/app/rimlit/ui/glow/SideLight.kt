package app.rimlit.ui.glow

import app.rimlit.model.ScreenSide
import kotlin.math.floor
import kotlin.math.max

/** The sides in [RoundRect]'s order, clockwise from the top: side `i` runs from corner `i` to corner `i + 1`. */
private val CLOCKWISE = arrayOf(ScreenSide.TOP, ScreenSide.RIGHT, ScreenSide.BOTTOM, ScreenSide.LEFT)

/** What [SideLight]'s corner check gives for a point in no corner: a light is never below 0, so it can't be one. */
private const val NOT_IN_CORNER = -1f

/** A pixel's alpha where the glow shows fully. */
private const val OPAQUE = 255

/** The most places a row's light may change at: eight by each of the left and right sides, and the middle. */
private const val MAX_ROW_CUTS = 17

/**
 * How much of the screen-edge glow shows at each point of the window when only some of the [sides] of [edge] (the
 * outline the glow is drawn along) are lit, in plain floats so it is testable off-device: the geometry of [SideMask].
 *
 * Each point belongs to a corner of [edge] or to its nearest side. A corner, the square between its circle's centre and
 * the window's corner, shows only where both its sides are lit. A lit side shows fully, except toward an unlit
 * neighbour, where it fades out over [taperPx], ending where the corner's curve begins. Away from the outline (where
 * effects move toward the open side: echo rings, sparks, the wave), a side's fade ends at the line bisecting the corner
 * instead, so whatever an effect draws inward fades out with the side it came from rather than being cut.
 */
internal class SideLight(private val edge: RoundRect, sides: Set<ScreenSide>, private val taperPx: Float) {
    init {
        require(taperPx > 0f) { "A fade needs a length" }
    }

    private val lit = BooleanArray(CLOCKWISE.size) { CLOCKWISE[it] in sides }

    /** Scratch for [at]: each side's distance inward. Main thread only. */
    private val distances = FloatArray(CLOCKWISE.size)

    /** How much of the glow shows at ([x], [y]), from 0 (none) to 1 (all). Allocates nothing. */
    fun at(x: Float, y: Float): Float {
        distances[0] = y - edge.top
        distances[1] = edge.right - x
        distances[2] = edge.bottom - y
        distances[3] = x - edge.left
        val corner = inCorner()
        return if (corner == NOT_IN_CORNER) alongSide() else corner
    }

    /**
     * This light at the centre of every pixel of a [columns] × [rows] grid over the [width] × [height] window, exactly
     * as [at] gives it, as alpha bytes in rows [rowBytes] apart (see [Raster]).
     */
    fun render(width: Float, height: Float, columns: Int, rows: Int, rowBytes: Int): LitPixels =
        Raster(width, height, columns, rows, rowBytes).render()

    /** How much shows at the point [distances] place, when it lies in a corner; [NOT_IN_CORNER] when it doesn't. */
    private fun inCorner(): Float {
        for (corner in CLOCKWISE.indices) {
            val before = (corner + CLOCKWISE.size - 1) % CLOCKWISE.size
            val radius = edge.cornerRadii[corner]
            if (distances[before] < radius && distances[corner] < radius) {
                return if (lit[before] && lit[corner]) 1f else 0f
            }
        }
        return NOT_IN_CORNER
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

    /**
     * Renders the light row by row, computing [at] only where it must. Along a row the light may change only at a few
     * places (see [findGuards]): a corner's edge, the line between two sides, and either end of a fade. Between two of
     * them each fade is not begun, done or under way throughout, so a run of pixels there that shows all or nothing at
     * both ends shows so throughout, and is filled at once; only a fading run is computed pixel by pixel. The two
     * pixels either side of each place are always computed, so rounding in where it falls never misplaces a run.
     */
    private inner class Raster(
        private val width: Float,
        private val height: Float,
        private val columns: Int,
        private val rows: Int,
        private val rowBytes: Int,
    ) {
        private val alphas = ByteArray(rowBytes * rows)

        /** The pixels by the current row's places, sorted: the first [guardCount] are set. */
        private val guards = IntArray(2 * MAX_ROW_CUTS)
        private var guardCount = 0

        private var row = 0
        private var y = 0f

        /** How far in from the top and the bottom of [edge] the current row lies. */
        private var fromTop = 0f
        private var fromBottom = 0f

        /** The bounds of the pixels that aren't 0 so far, as [LitPixels] holds them. */
        private var litLeft = columns
        private var litTop = rows
        private var litRight = 0
        private var litBottom = 0

        fun render(): LitPixels {
            for (row in 0 until rows) {
                this.row = row
                y = (row + 0.5f) * height / rows
                findGuards()
                var column = 0
                for (i in 0 until guardCount) {
                    val guard = guards[i]
                    // Two places may guard the same pixel.
                    if (guard < column) continue
                    fillRun(column, guard - 1)
                    put(guard, light(guard))
                    column = guard + 1
                }
                fillRun(column, columns - 1)
            }
            return if (litRight > litLeft) {
                LitPixels(alphas, litLeft, litTop, litRight, litBottom)
            } else {
                LitPixels(alphas, 0, 0, 0, 0)
            }
        }

        /** Sets [guards] for the row at [y]: the pixels by each place along it where the light may change. */
        private fun findGuards() {
            guardCount = 0
            fromTop = y - edge.top
            fromBottom = edge.bottom - y
            sidePlaces(edge.left, inward = 1f, topCorner = 0, bottomCorner = 3)
            sidePlaces(edge.right, inward = -1f, topCorner = 1, bottomCorner = 2)
            // Where the left and right sides meet, when the top and bottom are farther.
            guard((edge.left + edge.right) / 2f)
            guards.sort(0, guardCount)
        }

        /**
         * The places by the left or right side, which runs at [x], [inward] (+1 or -1) the way into the window, between
         * the corners [topCorner] and [bottomCorner]: each at a distance in from it.
         */
        private fun sidePlaces(x: Float, inward: Float, topCorner: Int, bottomCorner: Int) {
            val topRadius = edge.cornerRadii[topCorner]
            val bottomRadius = edge.cornerRadii[bottomCorner]
            // Where the row stops being nearest this side, and where this side's fade toward the top or bottom starts.
            guard(x + inward * fromTop)
            guard(x + inward * fromBottom)
            guard(x + inward * (fromTop - taperPx))
            guard(x + inward * (fromBottom - taperPx))
            // Where the top's or bottom's fade toward this side ends (it starts at the farther of the two above and the
            // corner's edge).
            guard(x + inward * (max(fromTop, topRadius) + taperPx))
            guard(x + inward * (max(fromBottom, bottomRadius) + taperPx))
            // A corner's edge, on the rows it spans.
            if (fromTop < topRadius) guard(x + inward * topRadius)
            if (fromBottom < bottomRadius) guard(x + inward * bottomRadius)
        }

        /** Guards the pixels either side of the window's [x]. */
        private fun guard(x: Float) {
            val before = floor(x * columns / width - 0.5f).toInt()
            if (before in 0 until columns) guards[guardCount++] = before
            if (before + 1 in 0 until columns) guards[guardCount++] = before + 1
        }

        /** The light at the centre of pixel [column] of the current row. */
        private fun light(column: Int): Float = at((column + 0.5f) * width / columns, y)

        /** Sets pixels [from] to [to] (inclusive) of the current row, which no guard lies between. */
        private fun fillRun(from: Int, to: Int) {
            if (from > to) return
            val first = light(from)
            if (from == to) return put(from, first)
            val last = light(to)
            if (first == last && (first == 0f || first == 1f)) {
                // The alphas start at 0.
                if (first == 1f) {
                    alphas.fill(OPAQUE.toByte(), row * rowBytes + from, row * rowBytes + to + 1)
                    grow(from, to)
                }
            } else {
                put(from, first)
                for (column in from + 1 until to) put(column, light(column))
                put(to, last)
            }
        }

        /** Sets pixel [column] of the current row to [light]. */
        private fun put(column: Int, light: Float) {
            val alpha = (OPAQUE * light + 0.5f).toInt()
            alphas[row * rowBytes + column] = alpha.toByte()
            if (alpha != 0) grow(column, column)
        }

        /** Grows the lit bounds over pixels [from] to [to] (inclusive) of the current row. */
        private fun grow(from: Int, to: Int) {
            litLeft = minOf(litLeft, from)
            litRight = maxOf(litRight, to + 1)
            litTop = minOf(litTop, row)
            litBottom = row + 1
        }
    }
}

/**
 * A [SideLight] rendered: [alphas], and the bounds of those that aren't 0, columns [left] to [right] and rows [top] to
 * [bottom], each end exclusive; all 0 when none is lit.
 */
internal class LitPixels(val alphas: ByteArray, val left: Int, val top: Int, val right: Int, val bottom: Int)
