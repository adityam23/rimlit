package app.rimlit.ui.glow

import app.rimlit.model.ScreenSide
import app.rimlit.model.ScreenSide.BOTTOM
import app.rimlit.model.ScreenSide.LEFT
import app.rimlit.model.ScreenSide.RIGHT
import app.rimlit.model.ScreenSide.TOP
import org.junit.Test
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The side mask's geometry: lit sides show, unlit ones don't, a corner only between two lit sides, with soft ends; and
 * its render, which computes only where the light may change, giving exactly what each pixel's own light would.
 */
class SideLightTest {

    /** A 1080 × 1920 screen edge with 100 px top corners and 60 px bottom corners. */
    private val screen = RoundRect(0f, 0f, 1080f, 1920f, floatArrayOf(100f, 100f, 60f, 60f))
    private val taper = 50f

    private fun light(vararg sides: ScreenSide) = SideLight(screen, sides.toSet(), taper)

    // On the outline, a point near each corner (inside its curve) and each side's midpoint.
    private val topLeft = 10f to 10f
    private val topRight = 1070f to 10f
    private val bottomRight = 1070f to 1910f
    private val bottomLeft = 10f to 1910f
    private val topMid = 540f to 0f
    private val rightMid = 1080f to 960f
    private val bottomMid = 540f to 1920f
    private val leftMid = 0f to 960f

    private fun SideLight.at(point: Pair<Float, Float>) = at(point.first, point.second)

    private fun SideLight.assertShows(expected: Float, vararg points: Pair<Float, Float>) {
        for (point in points) assertEquals(expected, at(point), 1e-4f, "at $point")
    }

    @Test
    fun aFadeNeedsALength() {
        assertFailsWith<IllegalArgumentException> { SideLight(screen, setOf(TOP), 0f) }
    }

    @Test
    fun cornersAndSidesShowNoLessThanNothing() {
        // A corner's check answers "in no corner" with a value below 0, which must never leak out as a light: points
        // on each corner square's edges, just inside and just outside, all show between 0 and 1.
        val top = light(TOP, LEFT)
        for (d in listOf(0f, 59.99f, 60f, 60.01f, 99.99f, 100f, 100.01f)) {
            for ((x, y) in listOf(d to d, d to 0f, 0f to d, 1080f - d to 1920f - d, 1080f - d to d, d to 1920f - d)) {
                assertTrue(top.at(x, y) in 0f..1f, "at ($x, $y)")
            }
        }
    }

    @Test
    fun aRenderIsExactlyEachPixelsLight() {
        // Insets as a line's half thickness makes them; radii none, mixed, and as round as the edge can be; fades
        // shorter than a mask pixel, ordinary, and longer than a short side.
        val edges = listOf(
            RoundRect(6f, 6f, 1074f, 1914f, floatArrayOf(100f, 100f, 60f, 60f)),
            RoundRect(0f, 0f, 1080f, 2400f, floatArrayOf(0f, 0f, 0f, 0f)),
            RoundRect(1.5f, 1.5f, 98.5f, 158.5f, floatArrayOf(48.5f, 48.5f, 48.5f, 48.5f)),
            RoundRect(60f, 10f, 2340f, 1070f, floatArrayOf(30f, 200f, 0f, 90f)),
        )
        // Every set of sides but none and all four.
        val sideSets = (1 until (1 shl ScreenSide.entries.size) - 1).map { bits ->
            ScreenSide.entries.filter { bits shr it.ordinal and 1 == 1 }.toSet()
        }
        for (edge in edges) {
            val width = edge.right + edge.left
            val height = edge.bottom + edge.top
            for (taper in listOf(2f, 50f, 400f)) {
                for (sides in sideSets) {
                    // The mask's own grid, and an uneven one with padded rows.
                    assertRendersExactly(SideLight(edge, sides, taper), width, height, ceil(width / 4f).toInt(), 0)
                    assertRendersExactly(SideLight(edge, sides, taper), width, height, 37, 3)
                }
            }
        }
    }

    private fun assertRendersExactly(light: SideLight, width: Float, height: Float, columns: Int, padding: Int) {
        val rows = (columns * height / width).toInt().coerceAtLeast(1)
        val rowBytes = columns + padding
        val pixels = light.render(width, height, columns, rows, rowBytes)
        assertEquals(rowBytes * rows, pixels.alphas.size)
        var anyLit = false
        for (row in 0 until rows) {
            val y = (row + 0.5f) * height / rows
            for (column in 0 until rowBytes) {
                val alpha = pixels.alphas[row * rowBytes + column].toInt() and 0xFF
                val at = "at column $column, row $row of $columns × $rows"
                if (column >= columns) {
                    assertEquals(0, alpha, "padding $at")
                    continue
                }
                val shown = light.at((column + 0.5f) * width / columns, y)
                assertTrue(shown in 0f..1f, at)
                assertEquals((255 * shown + 0.5f).toInt(), alpha, at)
                if (alpha != 0) {
                    anyLit = true
                    assertTrue(column >= pixels.left && column < pixels.right, "lit bounds' columns hold $at")
                    assertTrue(row >= pixels.top && row < pixels.bottom, "lit bounds' rows hold $at")
                }
            }
        }
        if (anyLit) {
            // The bounds are tight: each of their edges has a lit pixel.
            val lit = { column: Int, row: Int -> pixels.alphas[row * rowBytes + column].toInt() != 0 }
            assertTrue((pixels.top until pixels.bottom).any { lit(pixels.left, it) }, "left bound is lit")
            assertTrue((pixels.top until pixels.bottom).any { lit(pixels.right - 1, it) }, "right bound is lit")
            assertTrue((pixels.left until pixels.right).any { lit(it, pixels.top) }, "top bound is lit")
            assertTrue((pixels.left until pixels.right).any { lit(it, pixels.bottom - 1) }, "bottom bound is lit")
        } else {
            assertEquals(listOf(0, 0, 0, 0), listOf(pixels.left, pixels.top, pixels.right, pixels.bottom))
        }
    }

    @Test
    fun oneSideLitLightsOnlyItsPart() {
        // The top alone, on a 4 px grid: nothing deeper than half the width (540 px) is nearest the top, and nothing
        // within its 100 px corners, so the mask's layer skips all that.
        val pixels = light(TOP).render(1080f, 1920f, 270, 480, 270)
        assertEquals(0, pixels.top)
        assertTrue(pixels.bottom <= 540 / 4 + 1, "bottom ${pixels.bottom}")
        assertTrue(pixels.left >= 100 / 4 && pixels.right <= 270 - 100 / 4, "columns ${pixels.left}–${pixels.right}")
    }

    @Test
    fun oneSideShowsAloneAndNoCorner() {
        val top = light(TOP)
        top.assertShows(1f, topMid, 300f to 0f, 780f to 0f)
        top.assertShows(0f, topLeft, topRight, bottomRight, bottomLeft, rightMid, bottomMid, leftMid)
    }

    @Test
    fun aSideFadesOutBeforeItsUnlitCornersCurve() {
        val top = light(TOP)
        // The top-left curve ends at x = 100: the fade ends there and is done a taper later.
        top.assertShows(0f, 99f to 0f, 100f to 0f)
        top.assertShows(0.5f, 125f to 0f)
        top.assertShows(1f, 150f to 0f, 930f to 0f)
        top.assertShows(0.5f, 955f to 0f)
        top.assertShows(0f, 980f to 0f)
        var last = 0f
        for (x in 100..150) {
            val shown = top.at(x.toFloat(), 0f)
            assertTrue(shown >= last, "the fade rises steadily, at x = $x")
            last = shown
        }
    }

    @Test
    fun inwardTheFadeEndsAtTheCornersBisector() {
        val top = light(TOP)
        // 200 px in from the top, the side's fade ends on the corner's 45° line, x = 200, and is done 50 px on.
        top.assertShows(0f, 150f to 200f, 200f to 200f)
        top.assertShows(1f, 250f to 200f)
        assertTrue(top.at(220f, 200f) in 0.01f..0.99f)
    }

    @Test
    fun twoAdjacentSidesLightTheirCorner() {
        val corner = light(TOP, LEFT)
        corner.assertShows(1f, topLeft, topMid, leftMid, 100f to 0f, 0f to 100f)
        corner.assertShows(0f, topRight, bottomRight, bottomLeft, rightMid, bottomMid)
        corner.assertShows(0f, 980f to 0f, 0f to 1860f)
    }

    @Test
    fun twoOppositeSidesLightNoCorner() {
        val bars = light(TOP, BOTTOM)
        bars.assertShows(1f, topMid, bottomMid)
        bars.assertShows(0f, topLeft, topRight, bottomRight, bottomLeft, rightMid, leftMid)
    }

    @Test
    fun threeSidesLightTheCornersBetweenThem() {
        val three = light(TOP, RIGHT, BOTTOM)
        three.assertShows(1f, topMid, rightMid, bottomMid, topRight, bottomRight, 1000f to 0f, 1000f to 1920f)
        three.assertShows(0f, topLeft, bottomLeft, leftMid, 100f to 0f, 60f to 1920f)
    }
}
