package app.rimlit.ui.setup

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether Android refused to post notifications without showing its dialog, from its rationale before and after the
 * request (Android 13+: none before the first request, one after a first refusal, none again after a second).
 */
class RefusedForGoodTest {

    @Test
    fun aDialogTheUserAnsweredIsNotRefusedForGood() {
        assertFalse(refusedForGood(granted = false, rationaleBefore = false, rationaleAfter = true), "first refusal")
        assertFalse(refusedForGood(granted = false, rationaleBefore = true, rationaleAfter = false), "second refusal")
    }

    @Test
    fun noDialogAtAllIsRefusedForGood() {
        assertTrue(refusedForGood(granted = false, rationaleBefore = false, rationaleAfter = false))
    }

    @Test
    fun aGrantIsNeverRefused() {
        for (before in listOf(false, true)) {
            for (after in listOf(false, true)) {
                assertFalse(refusedForGood(granted = true, rationaleBefore = before, rationaleAfter = after))
            }
        }
    }
}
