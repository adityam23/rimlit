package app.rimlit.ui

import app.rimlit.model.UiPrefs
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Where the app opens: nothing until the preferences are read, then setup until it is finished, then Home. */
class StartRouteTest {

    @Test
    fun theAppOpensOnSetupUntilItIsFinished() {
        assertNull(startRoute(null), "not read yet: nothing drawn, so Home never flashes first")
        assertEquals(Routes.SETUP, startRoute(UiPrefs()))
        assertEquals(Routes.HOME, startRoute(UiPrefs(setupDone = true)))
    }

    @Test
    fun theStartIsChosenOnceTheyAreRead() {
        assertNull(startRoute(pinned = null, prefs = null))
        assertEquals(Routes.SETUP, startRoute(pinned = null, prefs = UiPrefs()))
        assertEquals(Routes.HOME, startRoute(pinned = null, prefs = UiPrefs(setupDone = true)))
    }

    @Test
    fun finishingSetupLeavesThePinnedStart() {
        assertEquals(
            Routes.SETUP,
            startRoute(pinned = Routes.SETUP, prefs = UiPrefs(setupDone = true)),
            "Finish has already gone Home; moving the start would rebuild the screens there",
        )
    }
}
