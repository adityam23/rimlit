package app.rimlit.ui

import androidx.compose.runtime.saveable.SaverScope
import app.rimlit.permissions.Need
import app.rimlit.permissions.OverlayFallback
import app.rimlit.permissions.PermissionKind
import app.rimlit.permissions.PermissionStatus
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What needs the user: missing permissions, required first, and the accessibility glow on but not running. */
class AttentionTest {

    private fun statuses(vararg missing: PermissionKind) =
        PermissionKind.entries.map { PermissionStatus(it, granted = it !in missing) }

    @Test
    fun requiredComeFirstAndGrantedNeverShow() {
        val list = attentions(
            statuses(PermissionKind.BATTERY_OPTIMIZATION, PermissionKind.OVERLAY, PermissionKind.ACCESSIBILITY),
            fallback = null,
        )
        assertEquals(
            listOf(
                Attention.Missing(PermissionKind.OVERLAY),
                Attention.Missing(PermissionKind.ACCESSIBILITY),
                Attention.Missing(PermissionKind.BATTERY_OPTIMIZATION),
            ),
            list,
        )
        assertEquals(emptyList(), attentions(statuses(), fallback = null))
    }

    @Test
    fun withTheAccessibilityGlowOnTheOverlayIsOnlyRecommendedAndCanBePutOff() {
        val overlay = attentions(statuses(PermissionKind.OVERLAY), fallback = null).single()
        assertEquals(Attention.Missing(PermissionKind.OVERLAY, Need.RECOMMENDED), overlay)
        assertTrue(overlay.dismissible)
        val alone = attentions(statuses(PermissionKind.OVERLAY, PermissionKind.ACCESSIBILITY), fallback = null)
        assertEquals(Need.REQUIRED, alone.first { it.kind == PermissionKind.OVERLAY }.need, "the service is off")
    }

    @Test
    fun theOverlayNoticeIsTheAccessibilityRowsOwnReasonNotARowOfItsOwn() {
        val off = attentions(statuses(PermissionKind.ACCESSIBILITY), OverlayFallback.SERVICE_OFF)
        assertEquals(listOf(Attention.Missing(PermissionKind.ACCESSIBILITY)), off)
        assertEquals(listOf(Attention.ServiceNotRunning), attentions(statuses(), OverlayFallback.SERVICE_NOT_RUNNING))
    }

    @Test
    fun onlyMissingRecommendedPermissionsCanBePutOff() {
        assertTrue(Attention.Missing(PermissionKind.ACCESSIBILITY).dismissible)
        assertTrue(Attention.Missing(PermissionKind.BATTERY_OPTIMIZATION).dismissible)
        assertFalse(Attention.Missing(PermissionKind.NOTIFICATION_ACCESS).dismissible)
        assertFalse(Attention.ServiceNotRunning.dismissible, "the user turned it on: it's broken, not optional")
    }

    @Test
    fun anOpenSheetSurvivesAConfigurationChange() {
        val scope = SaverScope { true }
        val missing = PermissionKind.entries.flatMap { kind -> Need.entries.map { Attention.Missing(kind, it) } }
        for (attention in listOf(Attention.ServiceNotRunning) + missing) {
            val saved = with(Attention.Saver) { scope.save(attention) }!!
            assertEquals(attention, Attention.Saver.restore(saved))
        }
        assertNull(Attention.Saver.restore("SOMETHING_NEWER"))
        assertEquals(Attention.Missing(PermissionKind.OVERLAY), Attention.Saver.restore("OVERLAY"), "saved before needs")
    }
}
