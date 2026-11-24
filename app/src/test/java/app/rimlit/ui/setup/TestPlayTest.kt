package app.rimlit.ui.setup

import app.rimlit.permissions.OverlayFallback
import app.rimlit.permissions.PermissionKind
import app.rimlit.permissions.PermissionStatus
import org.junit.Test
import kotlin.test.assertEquals

/** How setup shows a glow, "Try it" and a first look picked: always somewhere the user sees it. */
class TestPlayTest {

    private fun statuses(vararg missing: PermissionKind) =
        PermissionKind.entries.map { PermissionStatus(it, granted = it !in missing) }

    @Test
    fun aRealTestNotificationWhenItCanGlow() {
        assertEquals(TestPlay.NOTIFICATION, testPlay(statuses(), testPostable = true, overlayFallback = null))
        assertEquals(
            TestPlay.NOTIFICATION,
            testPlay(statuses(PermissionKind.ACCESSIBILITY), true, OverlayFallback.SERVICE_OFF),
        )
        assertEquals(TestPlay.NOTIFICATION, testPlay(statuses(PermissionKind.OVERLAY), true, overlayFallback = null))
        assertEquals(
            TestPlay.NOTIFICATION,
            testPlay(statuses(), true, OverlayFallback.SERVICE_NOT_RUNNING),
            "the app overlay stands in",
        )
    }

    @Test
    fun aPreviewOnScreenWhenNoTestNotificationCanGlow() {
        assertEquals(TestPlay.ON_SCREEN, testPlay(statuses(), false, null), "notifications or its channel off")
        assertEquals(
            TestPlay.ON_SCREEN,
            testPlay(statuses(PermissionKind.NOTIFICATION_ACCESS), testPostable = true, overlayFallback = null),
            "Rimlit wouldn't see it",
        )
    }

    @Test
    fun inRimlitsWindowWhenNothingMayDrawOverOtherApps() {
        val noOverlay = arrayOf(PermissionKind.ACCESSIBILITY, PermissionKind.OVERLAY)
        assertEquals(TestPlay.IN_WINDOW, testPlay(statuses(*noOverlay), true, OverlayFallback.NO_OVERLAY))
        assertEquals(TestPlay.IN_WINDOW, testPlay(statuses(*noOverlay), false, OverlayFallback.NO_OVERLAY))
        assertEquals(
            TestPlay.IN_WINDOW,
            testPlay(statuses(*PermissionKind.entries.toTypedArray()), false, OverlayFallback.NO_OVERLAY),
        )
        assertEquals(
            TestPlay.IN_WINDOW,
            testPlay(statuses(*noOverlay), true, overlayFallback = null),
            "the engine not started yet: neither granted, so nothing can draw",
        )
    }

    @Test
    fun aPreviewIsNeverATestNotification() {
        assertEquals(TestPlay.ON_SCREEN, previewPlay(statuses(), overlayFallback = null))
        assertEquals(TestPlay.ON_SCREEN, previewPlay(statuses(PermissionKind.ACCESSIBILITY), OverlayFallback.SERVICE_OFF))
        assertEquals(TestPlay.ON_SCREEN, previewPlay(statuses(), OverlayFallback.SERVICE_NOT_RUNNING))
        assertEquals(
            TestPlay.ON_SCREEN,
            previewPlay(statuses(PermissionKind.NOTIFICATION_ACCESS), overlayFallback = null),
            "a preview needs no notification access",
        )
    }

    @Test
    fun aPreviewInRimlitsWindowWhenNothingMayDrawOverOtherApps() {
        val noOverlay = arrayOf(PermissionKind.ACCESSIBILITY, PermissionKind.OVERLAY)
        assertEquals(TestPlay.IN_WINDOW, previewPlay(statuses(*noOverlay), overlayFallback = null))
        assertEquals(TestPlay.IN_WINDOW, previewPlay(statuses(PermissionKind.OVERLAY), OverlayFallback.NO_OVERLAY))
        assertEquals(TestPlay.IN_WINDOW, previewPlay(statuses(), OverlayFallback.NO_OVERLAY))
    }

    @Test
    fun tryItIsThePreviewWhereverItIsNoTestNotification() {
        val grants = PermissionKind.entries.map { statuses(it) } +
            listOf(statuses(), statuses(PermissionKind.ACCESSIBILITY, PermissionKind.OVERLAY))
        for (fallback in listOf(null) + OverlayFallback.entries) {
            for (granted in grants) {
                for (postable in listOf(true, false)) {
                    val test = testPlay(granted, postable, fallback)
                    if (test != TestPlay.NOTIFICATION) assertEquals(previewPlay(granted, fallback), test)
                }
            }
        }
    }

    @Test
    fun inRimlitsWindowWhenTheAccessibilityServiceIsOnButNotRunningWithoutTheOverlayGrant() {
        assertEquals(
            TestPlay.IN_WINDOW,
            testPlay(statuses(PermissionKind.OVERLAY), testPostable = true, OverlayFallback.NO_OVERLAY),
            "granted, so the grants alone would pick a test that can't show",
        )
    }
}
