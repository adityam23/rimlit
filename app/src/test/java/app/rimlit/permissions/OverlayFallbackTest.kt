package app.rimlit.permissions

import app.rimlit.engine.Surface
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OverlayFallbackTest {

    private fun statuses(vararg granted: PermissionKind) =
        PermissionKind.entries.map { PermissionStatus(it, it in granted) }

    private val both = statuses(PermissionKind.OVERLAY, PermissionKind.ACCESSIBILITY)

    @Test
    fun theAccessibilityOverlayDrawsEveryOnScreenGlow() {
        assertNull(overlayFallback(both, Surface.ACCESSIBILITY_OVERLAY))
    }

    @Test
    fun nothingIsKnownBeforeTheEngineStarts() {
        assertNull(overlayFallback(both, overlay = null))
    }

    @Test
    fun withoutDisplayOverOtherAppsNothingDraws() {
        assertEquals(OverlayFallback.NO_OVERLAY, overlayFallback(statuses(), Surface.APP_OVERLAY))
    }

    @Test
    fun theServiceOffFallsBack() {
        assertEquals(OverlayFallback.SERVICE_OFF, overlayFallback(statuses(PermissionKind.OVERLAY), Surface.APP_OVERLAY))
    }

    @Test
    fun theServiceOnButUnboundFallsBack() {
        assertEquals(OverlayFallback.SERVICE_NOT_RUNNING, overlayFallback(both, Surface.APP_OVERLAY))
    }
}
