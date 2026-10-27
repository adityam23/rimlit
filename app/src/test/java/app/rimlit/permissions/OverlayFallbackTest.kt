package app.rimlit.permissions

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OverlayFallbackTest {

    private fun statuses(vararg granted: PermissionKind) =
        PermissionKind.entries.map { PermissionStatus(it, it in granted) }

    @Test
    fun aConnectedServiceDrawsEveryOnScreenGlow() {
        assertNull(overlayFallback(statuses(PermissionKind.OVERLAY, PermissionKind.ACCESSIBILITY), serviceConnected = true))
    }

    @Test
    fun withoutDisplayOverOtherAppsNothingFallsBack() {
        assertNull(overlayFallback(statuses(), serviceConnected = false))
    }

    @Test
    fun theServiceOffFallsBack() {
        assertEquals(OverlayFallback.SERVICE_OFF, overlayFallback(statuses(PermissionKind.OVERLAY), false))
    }

    @Test
    fun theServiceOnButUnboundFallsBack() {
        assertEquals(
            OverlayFallback.SERVICE_NOT_RUNNING,
            overlayFallback(statuses(PermissionKind.OVERLAY, PermissionKind.ACCESSIBILITY), serviceConnected = false),
        )
    }
}
