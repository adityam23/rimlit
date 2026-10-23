package app.rimlit.engine

import android.app.NotificationManager
import app.rimlit.engine.Fixtures.AUTO
import app.rimlit.engine.Fixtures.OTHER_APP
import app.rimlit.engine.Fixtures.post
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostFilterTest {

    @Test
    fun settingsDecideWhichAppsGlow() {
        assertTrue(AUTO.wants(post("a")))
        assertFalse(AUTO.copy(enabled = false).wants(post("a")))
        assertFalse(AUTO.copy(blockedPackages = setOf(OTHER_APP)).wants(post("a")))
        assertFalse(AUTO.wants(post("a", own = true)), "Rimlit's own trampoline never glows")
        assertTrue(AUTO.copy(enabled = false).wants(post("a", own = true, test = true)), "the test bypasses the toggle")
    }

    @Test
    fun onlyAlertingPostsGlow() {
        assertTrue(post("a").alerts(isUpdate = false))
        assertTrue(post("a", fullScreenIntent = true).alerts(isUpdate = false), "ringing events are ongoing but alert")
        assertFalse(post("a", fullScreenIntent = true, ongoing = false).isRinging, "a reminder left in the shade")
        assertFalse(post("a", ongoing = true).alerts(isUpdate = false))
        assertFalse(post("a").copy(isGroupSummary = true).alerts(isUpdate = false))
        assertTrue(post("a", onlyAlertOnce = true).alerts(isUpdate = false))
        assertFalse(post("a", onlyAlertOnce = true).alerts(isUpdate = true), "silent update")
        assertTrue(post("a").alerts(isUpdate = true))
        assertFalse(post("a", importance = NotificationManager.IMPORTANCE_LOW).alerts(isUpdate = false))
        assertTrue(post("a", importance = null).alerts(isUpdate = false), "no ranking: assume it alerts")
        assertFalse(post("a").copy(passesDnd = false).alerts(isUpdate = false))
    }
}
