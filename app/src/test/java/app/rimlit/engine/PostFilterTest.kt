package app.rimlit.engine

import android.app.Notification
import android.app.NotificationManager
import app.rimlit.engine.Fixtures.AUTO
import app.rimlit.engine.Fixtures.OTHER_APP
import app.rimlit.engine.Fixtures.post
import org.junit.Test
import kotlin.test.assertEquals
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

    /** A first post: its key wasn't up. */
    private fun Post.alertsFirst() = alerts(isUpdate = false, wasRinging = false)

    @Test
    fun onlyChosenAppsGlowOnlyTheAppsSwitchedOn() {
        val chosen = AUTO.copy(onlyChosenApps = true)
        assertFalse(chosen.wants(post("a")), "nothing is switched on at first")
        assertTrue(chosen.copy(allowedPackages = setOf(OTHER_APP)).wants(post("a")))
        assertFalse(
            chosen.copy(allowedPackages = setOf(OTHER_APP), enabled = false).wants(post("a")),
            "the master toggle still rules",
        )
        assertTrue(AUTO.wants(post("a")), "the chosen list counts only in its mode")
        assertTrue(
            chosen.copy(blockedPackages = setOf(OTHER_APP), allowedPackages = setOf(OTHER_APP)).wants(post("a")),
            "and the switched-off list only in its own",
        )
        assertTrue(chosen.wants(post("t", own = true, test = true)), "the test glows in both modes")
    }

    @Test
    fun switchingAnAppTouchesOnlyTheCurrentModesList() {
        val blocked = AUTO.copy(blockedPackages = setOf("x")).withAppSwitched(OTHER_APP, on = false)
        assertEquals(setOf("x", OTHER_APP), blocked.blockedPackages)
        val chosen = blocked.copy(onlyChosenApps = true).withAppSwitched(OTHER_APP, on = true)
        assertEquals(setOf(OTHER_APP), chosen.allowedPackages)
        assertEquals(blocked.blockedPackages, chosen.blockedPackages, "flipping the mode loses neither list")
        assertTrue(chosen.glowsFor(OTHER_APP))
        assertFalse(chosen.copy(onlyChosenApps = false).glowsFor(OTHER_APP))
    }

    @Test
    fun onlyAlertingPostsGlow() {
        assertTrue(post("a").alertsFirst())
        assertTrue(post("a", fullScreenIntent = true).alertsFirst(), "ringing events are ongoing but alert")
        assertFalse(post("a", fullScreenIntent = true, ongoing = false).isRinging, "a reminder left in the shade")
        assertFalse(post("a", ongoing = true).alertsFirst())
        assertTrue(post("a", updatesSilently = true).alertsFirst())
        assertFalse(post("a", updatesSilently = true).alerts(isUpdate = true, wasRinging = false), "silent update")
        assertTrue(post("a").alerts(isUpdate = true, wasRinging = false))
        assertFalse(post("a", importance = NotificationManager.IMPORTANCE_LOW).alertsFirst())
        assertTrue(post("a", importance = null).alertsFirst(), "no ranking: assume it alerts")
        assertFalse(post("a").copy(passesDnd = false).alertsFirst())
    }

    @Test
    fun aSilentUpdateGlowsOnlyWhenItStartsRinging() {
        val call = post("a", fullScreenIntent = true, updatesSilently = true)
        assertTrue(call.alerts(isUpdate = true, wasRinging = false), "a service notification re-posted as the call")
        assertFalse(call.alerts(isUpdate = true, wasRinging = true), "re-posted while it keeps ringing")
        assertTrue(post("a", fullScreenIntent = true).alerts(isUpdate = true, wasRinging = true), "not silent")
    }

    @Test
    fun silentUpdatesAreAlertOnceServicesAndRunningProgress() {
        fun silent(flags: Int = 0, progress: Int = 0, max: Int = 0, indeterminate: Boolean = false) =
            updatesSilently(flags, progress, max, indeterminate)

        assertFalse(silent(), "a plain notification")
        assertTrue(silent(flags = Notification.FLAG_ONLY_ALERT_ONCE))
        assertTrue(silent(flags = Notification.FLAG_FOREGROUND_SERVICE))
        assertFalse(silent(flags = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_AUTO_CANCEL), "other flags")
        assertTrue(silent(progress = 40, max = 100), "a running bar")
        assertTrue(silent(progress = 0, max = 100), "a bar that hasn't moved yet")
        assertTrue(silent(indeterminate = true))
        assertTrue(silent(progress = 100, max = 100, indeterminate = true), "indeterminate: still running")
        assertFalse(silent(progress = 100, max = 100), "a finished bar: \"Download complete\"")
        assertFalse(silent(progress = 120, max = 100), "past its max: finished")
        assertFalse(silent(progress = 5, max = 0), "no max: no bar")
        assertTrue(silent(flags = Notification.FLAG_FOREGROUND_SERVICE, progress = 100, max = 100), "still a service")
    }

    @Test
    fun aGroupGlowsOncePerAlert() {
        fun alerts(group: GroupRole, groupAlert: GroupAlert) =
            post("a").copy(group = group, groupAlert = groupAlert).alertsFirst()

        GroupAlert.entries.forEach { assertTrue(alerts(GroupRole.NONE, it), "ungrouped: the behaviour doesn't apply") }
        assertTrue(alerts(GroupRole.CHILD, GroupAlert.ALL))
        assertFalse(alerts(GroupRole.SUMMARY, GroupAlert.ALL), "its child already glows")
        assertFalse(alerts(GroupRole.CHILD, GroupAlert.SUMMARY), "the system keeps it silent")
        assertTrue(alerts(GroupRole.SUMMARY, GroupAlert.SUMMARY), "the group alerts only through it")
        assertTrue(alerts(GroupRole.CHILD, GroupAlert.CHILDREN))
        assertFalse(alerts(GroupRole.SUMMARY, GroupAlert.CHILDREN), "the system keeps it silent")
    }
}
