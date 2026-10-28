package app.rimlit.ui.glow

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoundRectTest {

    private val bounds = FloatArray(4)
    private val radii = FloatArray(8)

    /** A 1080 × 1920 screen edge with 100 px top corners and 60 px bottom corners. */
    private val screen = RoundRect(0f, 0f, 1080f, 1920f, floatArrayOf(100f, 100f, 60f, 60f))

    @Test
    fun anUnchangedShapeKeepsItsBoundsAndCorners() {
        assertTrue(screen.offset(0f, bounds, radii))
        assertContentEquals(floatArrayOf(0f, 0f, 1080f, 1920f), bounds)
        assertContentEquals(floatArrayOf(100f, 100f, 100f, 100f, 60f, 60f, 60f, 60f), radii)
    }

    @Test
    fun aRingInsideTheEdgeShrinksItsSidesAndCornersAlike() {
        assertTrue(screen.offset(-80f, bounds, radii))
        assertContentEquals(floatArrayOf(80f, 80f, 1000f, 1840f), bounds)
        // Corners tighter than the inset turn square, as an exact inset does.
        assertContentEquals(floatArrayOf(20f, 20f, 20f, 20f, 0f, 0f, 0f, 0f), radii)
    }

    @Test
    fun aRingAroundTheCutoutGrowsAndStaysRound() {
        val hole = RoundRect(500f, 30f, 580f, 110f, FloatArray(4) { 40f })
        assertTrue(hole.offset(50f, bounds, radii))
        assertContentEquals(floatArrayOf(450f, -20f, 630f, 160f), bounds)
        assertContentEquals(FloatArray(8) { 90f }, radii, "still a circle")
    }

    @Test
    fun cornersNeverExceedHalfTheShorterSide() {
        val pill = RoundRect(0f, 0f, 200f, 100f, FloatArray(4) { 50f })
        assertTrue(pill.offset(-30f, bounds, radii))
        assertContentEquals(FloatArray(8) { 20f }, radii)
    }

    @Test
    fun aRingThatShrinksAwayIsNotDrawn() {
        assertFalse(screen.offset(-540f, bounds, radii))
        assertFalse(screen.offset(-600f, bounds, radii))
    }
}
