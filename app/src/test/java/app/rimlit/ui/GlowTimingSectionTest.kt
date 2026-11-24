package app.rimlit.ui

import app.rimlit.model.GlowSettings.Ranges
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlowTimingSectionTest {
    @Test
    fun theDurationSnapsToTenthsOfASecondBelowOneSecondAndIsLeftAloneFromThere() {
        val snap = ::snapDurationMs
        assertEquals(300L, snap(300L))
        assertEquals(300L, snap(349L))
        assertEquals(400L, snap(350L))
        assertEquals(900L, snap(949L))
        assertEquals(1_000L, snap(950L))
        assertEquals(1_000L, snap(1_000L))
        assertEquals(1_234L, snap(1_234L))
        assertEquals(10_000L, snap(10_000L))
        for (ms in Ranges.durationMs) {
            assertEquals(snap(ms), snap(snap(ms)), "snapping $ms twice")
            assertTrue(snap(ms) in Ranges.durationMs, "snapping $ms stays in range")
        }
    }
}
