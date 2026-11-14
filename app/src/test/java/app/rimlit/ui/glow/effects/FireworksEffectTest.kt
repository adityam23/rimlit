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
    fun burstsReadOnASmallPunchHoleRing() {
        // A square camera 20 px across, its ring 10 px out all round: 40 px, the ring's shorter side (as in the bug
        // report that raised the shares below).
        val ring = CutoutRing(floatArrayOf(10f, 10f, 30f, 10f, 30f, 30f, 10f, 30f), radius = 10f)
        assertEquals(40f, min(ring.width, ring.height))
        // Thickness and glow wide enough that the absolute size (as on the screen edge) would ask for more than the
        // ring itself; the shares, not the absolute sizes, size the bursts here (the old 0.12 / 0.03 shares are
        // exactly the bug report's ~5 px reach and ~1 px sparks).
        val reach = burstReach(ring, thicknessPx = 20f, glowPx = 40f)
        val radius = burstRadius(ring, thicknessPx = 20f, glowPx = 40f)
        assertEquals(40f * 0.55f, reach, "reach is about the ring's own size, not the old ~5 px")
        assertEquals(40f * 0.1f, radius, "radius reads as a spark, not the old ~1 px")
        assertTrue(radius >= 3f, "radius $radius reads as a spark, not a stray pixel")
    }

    @Test
    fun burstsOnTheScreenEdgeStayAtTheirAbsoluteSize() {
        // A phone-sized screen edge, and the sliders' highest thickness and glow (Ranges.thicknessDp / glowWidthDp),
        // at a common density (3x, xxhdpi) — the screen edge this large relative to its glow is the worst case for
        // the share cap to bite.
        val screen = RoundRect(0f, 0f, 1_080f, 2_400f, FloatArray(4) { 0f })
        val density = 3f
        val thicknessPx = 12f * density
        val glowPx = 64f * density
        val expectedReach = thicknessPx * 6f + glowPx * 1.5f
        val expectedRadius = thicknessPx * 1.5f + glowPx * 0.15f
        val reach = burstReach(screen, thicknessPx, glowPx)
        val radius = burstRadius(screen, thicknessPx, glowPx)
        assertEquals(expectedReach, reach, "the share is well clear of the absolute reach, so it never caps it")
        assertEquals(expectedRadius, radius, "the share is well clear of the absolute radius, so it never caps it")
    }

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
