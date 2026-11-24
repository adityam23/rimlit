package app.rimlit.engine

import app.rimlit.model.GlowSettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FadesTest {

    private val full = FADE_IN_MS + FADE_OUT_MS

    @Test
    fun aGlowAsLongAsTheFullFadesOrLongerKeepsThem() {
        for (ms in listOf(full, full + 1, 1_000L, 4_000L, GlowSettings.Ranges.durationMs.last)) {
            assertEquals(Fades(FADE_IN_MS, FADE_OUT_MS), fadesFor(ms), "$ms ms")
        }
    }

    @Test
    fun aShorterGlowScalesBothFadesByItsShareOfTheFullLength() {
        assertEquals(Fades(85L, 215L), fadesFor(300L))
        assertEquals(Fades(114L, 286L), fadesFor(400L))
        assertEquals(Fades(179L, 450L), fadesFor(full - 1), "just below the full length")
    }

    @Test
    fun theFadesAddUpToTheDurationBelowTheFullLengthAndAreContinuousAcrossIt() {
        for (ms in GlowSettings.Ranges.durationMs.first until full) {
            val fades = fadesFor(ms)
            assertEquals(ms, fades.inMs + fades.outMs, "$ms ms")
        }
        val below = fadesFor(full - 1)
        assertTrue(FADE_IN_MS - below.inMs <= 1 && FADE_OUT_MS - below.outMs <= 1, "no jump at the full length: $below")
    }

    @Test
    fun noFadeIsEverZeroOrNegativeFromTheShortestDuration() {
        for (ms in GlowSettings.Ranges.durationMs.first..GlowSettings.Ranges.durationMs.last) {
            val fades = fadesFor(ms)
            assertTrue(fades.inMs > 0 && fades.outMs > 0, "$ms ms: $fades")
        }
    }
}
