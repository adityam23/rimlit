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
}
