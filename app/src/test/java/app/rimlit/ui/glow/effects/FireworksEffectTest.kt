package app.rimlit.ui.glow.effects

import app.rimlit.ui.glow.CutoutRing
import app.rimlit.ui.glow.RoundRect
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FireworksEffectTest {

    @Test
    fun theSameLayoutGetsTheSameBursts() {
        val first = BurstSeeds(count = 12, sparks = 10, seed = 7)
        val again = BurstSeeds(count = 12, sparks = 10, seed = 7)
        assertContentEquals(first.fractions, again.fractions)
        assertContentEquals(first.phases, again.phases)
        assertContentEquals(first.angles, again.angles)
        assertContentEquals(first.rates, again.rates)
        assertFalse(first.fractions.contentEquals(BurstSeeds(count = 12, sparks = 10, seed = 8).fractions))
    }

    @Test
    fun sitesSpreadOnePerShareOfTheLapAndSparksFanOutAtVaryingSpeeds() {
        val seeds = BurstSeeds(count = 20, sparks = 10, seed = 1)
        for (i in 0 until seeds.count) {
            assertTrue(seeds.fractions[i] >= i / 20f && seeds.fractions[i] < (i + 1) / 20f, "site $i")
            assertTrue(seeds.phases[i] >= 0f && seeds.phases[i] < 1f)
        }
        for (s in seeds.angles.indices) {
            assertTrue(abs(seeds.angles[s]) < 1.3f + 1e-6f, "spark $s within the fan")
            assertTrue(seeds.rates[s] >= 0.6f && seeds.rates[s] < 1f)
        }
    }

    @Test
    fun burstsAreStaggeredSoSomeAreAlwaysGoingOffButNeverAllAtOnce() {
        for (count in 4..20) {
            val seeds = BurstSeeds(count, sparks = 10, seed = count)
            for (i in 1 until count) {
                val apart = abs(seeds.phases[i] - seeds.phases[i - 1])
                assertTrue(min(apart, 1f - apart) > 0.38f, "neighbours $i go off apart")
            }
            for (step in 0 until 500) {
                val cycle = step / 500f
                val live = seeds.phases.count { burstProgress(cycle + it) <= 1f }
                assertTrue(live in 1 until count, "$count sites at cycle $cycle: $live live")
            }
        }
    }

    @Test
    fun aBurstFliesForPartOfItsCycleThenRests() {
        assertEquals(0f, burstProgress(0f))
        assertEquals(0f, burstProgress(3f))
        assertEquals(burstProgress(0.3f), burstProgress(5.3f), 1e-5f)
        assertTrue(burstProgress(0.5f) < 1f, "still flying")
        assertTrue(burstProgress(0.9f) > 1f, "resting")
    }

    @Test
    fun sparksSlowAndFadeAsTheyFly() {
        var previousDistance = 0f
        var previousStep = Float.MAX_VALUE
        var previousAlpha = Float.MAX_VALUE
        for (step in 1..100) {
            val progress = step / 100f
            val distance = sparkDistance(progress, reach = 120f)
            assertTrue(distance - previousDistance < previousStep, "slows down")
            previousStep = distance - previousDistance
            previousDistance = distance
            val alpha = sparkAlpha(progress)
            assertTrue(alpha < previousAlpha, "fades")
            previousAlpha = alpha
        }
        assertEquals(0f, sparkDistance(0f, reach = 120f))
        assertEquals(0f, sparkAlpha(1f))
    }

    @Test
    fun aStillBurstStandsAtItsWidest() {
        val widest = (0..100).maxOf { sparkDistance(it / 100f, reach = 120f) }
        assertEquals(120f, sparkDistance(WIDEST_PROGRESS, reach = 120f))
        assertEquals(widest, sparkDistance(WIDEST_PROGRESS, reach = 120f))
    }

    @Test
    fun sparksFlyInwardFromTheEdgeAndOutwardFromTheCutout() {
        val screen = RoundRect(0f, 0f, 1080f, 2400f, FloatArray(4) { 120f })
        // A punch-hole ring: a square camera, its ring 12 px out all round.
        val camera = floatArrayOf(520f, 50f, 560f, 50f, 560f, 90f, 520f, 90f)
        val ring = CutoutRing(camera, radius = 12f)

        // How far ([x], [y]) lies from the camera square.
        fun fromCamera(x: Float, y: Float) = hypot(max(max(520f - x, x - 560f), 0f), max(max(50f - y, y - 90f), 0f))
        val point = FloatArray(4)
        val direction = FloatArray(2)
        for (shape in listOf(screen, ring)) {
            for (site in 0 until 40) {
                shape.pointAt(shape.perimeter * site / 40, point)
                for (step in 0..20) {
                    val angle = -1.3f + 2.6f * step / 20
                    sparkDirection(point[2], point[3], angle, direction)
                    assertEquals(1f, hypot(direction[0], direction[1]), 1e-5f)
                    // Within the fan about the open side's normal, so no spark skims the outline.
                    val along = direction[0] * point[2] + direction[1] * point[3]
                    assertTrue(along >= cos(1.3f) - 1e-5f, "site $site at $angle")
                    val x = point[0] + 5f * direction[0]
                    val y = point[1] + 5f * direction[1]
                    if (shape === screen) {
                        assertTrue(x > 0f && x < 1080f && y > 0f && y < 2400f, "into the screen: site $site")
                    } else {
                        val gained = fromCamera(x, y) - fromCamera(point[0], point[1])
                        assertTrue(gained > 0f, "away from the cutout: site $site at $angle")
                    }
                }
            }
        }
    }
}
