package app.rimlit.ui.glow

import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
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

    @Test
    fun aSizeFitsWithinAShareOfTheRing() {
        val ring = CutoutRing(floatArrayOf(10f, 10f, 30f, 10f, 30f, 50f, 10f, 50f), radius = 6f)
        assertEquals(32f, ring.width)
        assertEquals(52f, ring.height)
        assertEquals(6.4f, ring.fit(30f, share = 0.2f), 1e-4f)
    }

    @Test
    fun theRingIsWalkedClockwiseWithOutwardNormals() {
        val ring = CutoutRing(floatArrayOf(10f, 10f, 30f, 10f, 30f, 50f, 10f, 50f), radius = 6f)
        assertEquals(2f * (20f + 40f) + 2f * PI.toFloat() * 6f, ring.perimeter, 1e-3f)
        val point = FloatArray(4)
        ring.pointAt(0f, point)
        assertPoint(floatArrayOf(10f, 4f, 0f, -1f), point, "the top side's start, facing up")
        // Half way round the top-right corner.
        ring.pointAt(20f + 6f * PI.toFloat() / 4f, point)
        val diagonal = sqrt(0.5f)
        assertPoint(floatArrayOf(30f + 6f * diagonal, 10f - 6f * diagonal, diagonal, -diagonal), point, "corner")
        ring.pointAt(-1f, point)
        assertEquals(6f, hypot(point[0] - 10f, point[1] - 10f), 1e-3f, "wraps back into the top-left corner")
    }

    @Test
    fun everyPointOfATeardropRingIsItsRadiusFromTheCutout() {
        // A teardrop hanging from the top edge: square there, a round end of radius 10.
        val drop = FloatArray(2 * 40)
        for (i in 0 until 20) {
            val angle = PI.toFloat() * i / 19
            drop[2 * i] = 100f + 10f * cos(angle)
            drop[2 * i + 1] = 30f + 10f * sin(angle)
        }
        for (i in 20 until 40) {
            drop[2 * i] = if (i < 30) 90f else 110f
            drop[2 * i + 1] = if (i < 30) 30f - (i - 20) * 3f else (i - 30) * 3f
        }
        val ring = CutoutRing(drop, radius = 7f)
        val hull = convexHull(drop)
        val point = FloatArray(4)
        for (step in 0 until 200) {
            ring.pointAt(ring.perimeter * step / 200, point)
            assertEquals(7f, distanceToPolygon(point[0], point[1], hull), 0.05f, "step $step")
            // Its normal leads straight out, the same distance further.
            val further = distanceToPolygon(point[0] + point[2], point[1] + point[3], hull)
            assertEquals(8f, further, 0.05f, "step $step")
        }
    }

    private fun distanceToPolygon(x: Float, y: Float, polygon: FloatArray): Float {
        val n = polygon.size / 2
        return (0 until n).minOf { i ->
            val ax = polygon[2 * i]
            val ay = polygon[2 * i + 1]
            val bx = polygon[2 * ((i + 1) % n)]
            val by = polygon[2 * ((i + 1) % n) + 1]
            val lengthSquared = (bx - ax) * (bx - ax) + (by - ay) * (by - ay)
            val t = if (lengthSquared == 0f) 0f else (((x - ax) * (bx - ax) + (y - ay) * (by - ay)) / lengthSquared)
            val along = t.coerceIn(0f, 1f)
            hypot(x - (ax + along * (bx - ax)), y - (ay + along * (by - ay)))
        }
    }

    private fun assertPoint(expected: FloatArray, actual: FloatArray, message: String) {
        for (i in expected.indices) assertEquals(expected[i], actual[i], 1e-3f, "$message [$i]")
    }
}
