package app.rimlit.ui.setup

import app.rimlit.permissions.PermissionKind
import app.rimlit.permissions.PermissionStatus
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The guided first launch's order, and how each step moves on. */
class SetupStepTest {

    private fun statuses(vararg missing: PermissionKind) =
        PermissionKind.entries.map { PermissionStatus(it, granted = it !in missing) }

    private val none = statuses(*PermissionKind.entries.toTypedArray())

    @Test
    fun theStepsRunInTheDesignedOrder() {
        assertEquals(
            listOf(
                "WELCOME", "NOTIFICATION_ACCESS", "ON_SCREEN", "SCREEN_OFF", "BATTERY", "DEVICE_FIT", "FIRST_LOOK",
                "TEST",
            ),
            SetupStep.entries.map { it.name },
        )
        assertNull(SetupStep.WELCOME.previous, "back on the first step leaves the app")
        assertNull(SetupStep.TEST.next)
        assertEquals(1f, SetupStep.TEST.progress)
    }

    @Test
    fun onlyNotificationAccessHasNoWayPast() {
        assertEquals(Onward.WAIT, SetupStep.NOTIFICATION_ACCESS.onward(none))
        assertEquals(Onward.NEXT, SetupStep.NOTIFICATION_ACCESS.onward(statuses()))
        for (step in listOf(SetupStep.ON_SCREEN, SetupStep.SCREEN_OFF, SetupStep.BATTERY)) {
            assertEquals(Onward.NOT_NOW, step.onward(none), "$step can be put off")
            assertEquals(Onward.NEXT, step.onward(statuses()), "$step granted: shown, then Next")
        }
        assertEquals(Onward.START, SetupStep.WELCOME.onward(none))
        assertEquals(Onward.NEXT, SetupStep.DEVICE_FIT.onward(none))
        assertEquals(Onward.NEXT, SetupStep.FIRST_LOOK.onward(none))
        assertEquals(Onward.FINISH, SetupStep.TEST.onward(none))
    }

    @Test
    fun eitherOverlayGlowsOnScreenButTheScreenOffWakeNeedsBothGrants() {
        assertEquals(Onward.NEXT, SetupStep.ON_SCREEN.onward(statuses(PermissionKind.OVERLAY)))
        assertEquals(Onward.NEXT, SetupStep.ON_SCREEN.onward(statuses(PermissionKind.ACCESSIBILITY)))
        assertEquals(Onward.NOT_NOW, SetupStep.SCREEN_OFF.onward(statuses(PermissionKind.FULL_SCREEN_INTENT)))
        assertEquals(Onward.NOT_NOW, SetupStep.SCREEN_OFF.onward(statuses(PermissionKind.NOTIFICATIONS)))
    }
}
