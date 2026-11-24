package app.rimlit.ui.glow

import app.rimlit.model.ALL_SIDES
import app.rimlit.model.ScreenSide
import app.rimlit.model.ScreenSide.BOTTOM
import app.rimlit.model.ScreenSide.LEFT
import app.rimlit.model.ScreenSide.RIGHT
import app.rimlit.model.ScreenSide.TOP
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The side mask's geometry: lit sides show, unlit ones don't, a corner only between two lit sides, with soft ends. */
class SideLightTest {

    /** A 1080 × 1920 screen edge with 100 px top corners and 60 px bottom corners. */
    private val screen = RoundRect(0f, 0f, 1080f, 1920f, floatArrayOf(100f, 100f, 60f, 60f))
    private val taper = 50f

    private fun light(vararg sides: ScreenSide) = assertNotNull(SideLight.of(screen, sides.toSet(), taper))

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
    fun allFourSidesNeedNoMask() {
        assertNull(SideLight.of(screen, ALL_SIDES, taper))
        assertNull(SideLight.of(screen, ALL_SIDES + TOP, taper))
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
