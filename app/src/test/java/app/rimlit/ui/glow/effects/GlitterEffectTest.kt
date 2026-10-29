package app.rimlit.ui.glow.effects

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlitterEffectTest {

    @Test
    fun theSameLayoutGetsTheSameSparkles() {
        val first = SparkleSeeds(count = 54, seed = 7)
        val again = SparkleSeeds(count = 54, seed = 7)
        assertContentEquals(first.fractions, again.fractions)
        assertContentEquals(first.phases, again.phases)
        assertContentEquals(first.rates, again.rates)
        assertFalse(first.fractions.contentEquals(SparkleSeeds(count = 54, seed = 8).fractions), "another outline differs")
    }

    @Test
    fun sparklesSpreadOnePerShareOfTheLapInOrder() {
        val seeds = SparkleSeeds(count = 40, seed = 1)
        for (i in 0 until seeds.count) {
            assertTrue(seeds.fractions[i] >= i / 40f && seeds.fractions[i] < (i + 1) / 40f, "sparkle $i")
            assertTrue(seeds.phases[i] >= 0f && seeds.phases[i] < 1f)
            assertTrue(seeds.rates[i] >= 0.7f && seeds.rates[i] < 1.3f)
        }
    }

    @Test
    fun aLongOutlineKeepsTheWantedSparkleSize() {
        val radius = sparkleRadius(wanted = 20f, length = 5_900f)
        assertEquals(20f, radius)
        assertEquals(118, sparkleCount(radius, length = 5_900f))
    }

    @Test
    fun aSmallRingGetsSmallerSparklesRatherThanOverlappingOnes() {
        val length = 200f
        val radius = sparkleRadius(wanted = 44f, length)
        assertEquals(10f, radius)
        val count = sparkleCount(radius, length)
        assertEquals(8, count, "still the fewest sparkles")
        assertTrue(length / count >= radius * 2.5f - 1e-3f, "each keeps its spacing")
    }

    @Test
    fun aTinyRingStillDrawsOnePixelSparkles() {
        assertEquals(1f, sparkleRadius(wanted = 44f, length = 5f))
    }

    @Test
    fun aSparkleFlashesOncePerCycle() {
        assertEquals(0f, twinkle(0f), 1e-6f)
        assertEquals(1f, twinkle(0.5f), 1e-6f)
        assertEquals(twinkle(0.2f), twinkle(3.2f), 1e-5f)
    }
}
