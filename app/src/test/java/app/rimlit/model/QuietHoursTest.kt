package app.rimlit.model

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuietHoursTest {

    private fun quiet(start: Int, end: Int, scope: QuietScope = QuietScope.ALL) =
        QuietHours(enabled = true, startMinute = start * 60, endMinute = end * 60, scope = scope)

    @Test
    fun aWindowPastMidnightWraps() {
        val night = quiet(22, 7)
        assertTrue(night.mutes(23 * 60, lockScreen = false))
        assertTrue(night.mutes(0, lockScreen = false))
        assertFalse(night.mutes(7 * 60, lockScreen = false), "the end is exclusive")
        assertFalse(night.mutes(12 * 60, lockScreen = false))
        assertTrue(night.mutes(22 * 60, lockScreen = false), "the start is inclusive")
    }

    @Test
    fun aWindowWithinADay() {
        val lunch = quiet(12, 14)
        assertTrue(lunch.mutes(13 * 60, lockScreen = false))
        assertFalse(lunch.mutes(11 * 60, lockScreen = false))
        assertFalse(lunch.mutes(14 * 60, lockScreen = false))
    }

    @Test
    fun equalEndsMeanTheWholeDay() {
        assertTrue(quiet(9, 9).mutes(3 * 60, lockScreen = false))
    }

    @Test
    fun theScopeAndTheSwitchDecide() {
        assertFalse(quiet(22, 7, QuietScope.SCREEN_OFF).mutes(23 * 60, lockScreen = false))
        assertTrue(quiet(22, 7, QuietScope.SCREEN_OFF).mutes(23 * 60, lockScreen = true))
        assertFalse(quiet(22, 7).copy(enabled = false).mutes(23 * 60, lockScreen = true))
    }
}
