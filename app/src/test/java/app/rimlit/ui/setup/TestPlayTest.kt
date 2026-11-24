package app.rimlit.ui.setup

import app.rimlit.permissions.PermissionKind
import app.rimlit.permissions.PermissionStatus
import org.junit.Test
import kotlin.test.assertEquals

/** How "Try it" shows the glow: always somewhere the user sees it. */
class TestPlayTest {

    private fun statuses(vararg missing: PermissionKind) =
        PermissionKind.entries.map { PermissionStatus(it, granted = it !in missing) }

    @Test
    fun aRealTestNotificationWhenItCanGlow() {
        assertEquals(TestPlay.NOTIFICATION, testPlay(statuses(), testPostable = true))
        assertEquals(TestPlay.NOTIFICATION, testPlay(statuses(PermissionKind.ACCESSIBILITY), testPostable = true))
        assertEquals(TestPlay.NOTIFICATION, testPlay(statuses(PermissionKind.OVERLAY), testPostable = true))
    }

    @Test
    fun aPreviewOnScreenWhenNoTestNotificationCanGlow() {
        assertEquals(TestPlay.ON_SCREEN, testPlay(statuses(), testPostable = false), "notifications or its channel off")
        assertEquals(
            TestPlay.ON_SCREEN,
            testPlay(statuses(PermissionKind.NOTIFICATION_ACCESS), testPostable = true),
            "Rimlit wouldn't see it",
        )
    }

    @Test
    fun inRimlitsWindowWhenNothingMayDrawOverOtherApps() {
        val noOverlay = arrayOf(PermissionKind.ACCESSIBILITY, PermissionKind.OVERLAY)
        assertEquals(TestPlay.IN_WINDOW, testPlay(statuses(*noOverlay), testPostable = true))
        assertEquals(TestPlay.IN_WINDOW, testPlay(statuses(*noOverlay), testPostable = false))
        assertEquals(TestPlay.IN_WINDOW, testPlay(statuses(*PermissionKind.entries.toTypedArray()), false))
    }
}
