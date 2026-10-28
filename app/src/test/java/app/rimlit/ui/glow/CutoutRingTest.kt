package app.rimlit.ui.glow

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CutoutRingTest {

    @Test
    fun theHullDropsInnerRepeatedAndSidePointsAndRunsClockwise() {
        // A square given anticlockwise, with a repeat, a point inside and one on a side.
        val square = floatArrayOf(0f, 0f, 0f, 10f, 5f, 5f, 10f, 10f, 10f, 10f, 10f, 5f, 10f, 0f)
        // Clockwise on screen (y down): along the top to the right, then down.
        assertContentEquals(floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f), convexHull(square))
    }

    @Test
    fun aNotchWithFlaredShouldersIsBridgedAcrossItsHollows() {
        // Hangs from the top edge: shoulders flare out along it, curving in to a narrower notch.
        val flared = floatArrayOf(0f, 0f, 100f, 0f, 80f, 5f, 75f, 30f, 25f, 30f, 20f, 5f)
        val hull = convexHull(flared)
        assertEquals(4, hull.size / 2, "the hollow corners at (80, 5) and (20, 5) are bridged")
        assertFalse((0 until hull.size / 2).any { hull[2 * it] == 80f && hull[2 * it + 1] == 5f })
    }

    @Test
    fun theRingReachesItsRadiusPastTheHull() {
        val ring = CutoutRing(floatArrayOf(10f, 10f, 30f, 10f, 30f, 50f, 10f, 50f), radius = 6f)
        assertEquals(16f, ring.cornerRadius, "half the 20 px width plus the radius")
        assertFalse(ring.opensInward)
    }
}
