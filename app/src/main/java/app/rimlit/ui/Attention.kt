package app.rimlit.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.rimlit.R
import app.rimlit.permissions.Need
import app.rimlit.permissions.OverlayFallback
import app.rimlit.permissions.PermissionKind
import app.rimlit.permissions.PermissionStatus

/**
 * Something that needs the user: a permission still missing, or the accessibility glow turned on but not running.
 * Android's overlay notice, shown while the accessibility glow is off, is the accessibility permission's own reason.
 */
internal sealed interface Attention {
    /** How much glows depend on it. */
    val need: Need

    /** The permission whose settings page sorts it out. */
    val kind: PermissionKind

    /** A recommended permission may be put off ("Not now"): it then shows in Behavior instead of on Home. */
    val dismissible: Boolean get() = need == Need.RECOMMENDED && this is Missing

    data class Missing(override val kind: PermissionKind) : Attention {
        override val need: Need get() = kind.need
    }

    /** The accessibility glow is on in settings, but the system hasn't started it: off and on again fixes it. */
    data object ServiceNotRunning : Attention {
        override val need = Need.RECOMMENDED
        override val kind = PermissionKind.ACCESSIBILITY
    }

    companion object {
        private const val NOT_RUNNING = "not_running"

        /** Saves an attention as its permission's name, or [NOT_RUNNING]; `null` (none) as an empty string. */
        val Saver: Saver<Attention?, String> = Saver(
            save = { attention ->
                when (attention) {
                    null -> ""
                    ServiceNotRunning -> NOT_RUNNING
                    is Missing -> attention.kind.name
                }
            },
            restore = { saved ->
                if (saved == NOT_RUNNING) {
                    ServiceNotRunning
                } else {
                    PermissionKind.entries.firstOrNull { it.name == saved }?.let(::Missing)
                }
            },
        )
    }
}

/**
 * What needs the user, given the grants in [statuses] and why on-screen glows fall back ([fallback]), required first.
 */
internal fun attentions(statuses: List<PermissionStatus>, fallback: OverlayFallback?): List<Attention> {
    val missing = statuses.filterNot { it.granted }.map { Attention.Missing(it.kind) }
    val notRunning = listOfNotNull(
        Attention.ServiceNotRunning.takeIf { fallback == OverlayFallback.SERVICE_NOT_RUNNING },
    )
    return (missing + notRunning).sortedBy { it.need }
}

internal val Attention.icon: ImageVector
    get() = when (this) {
        Attention.ServiceNotRunning -> Icons.Filled.SyncProblem

        is Attention.Missing -> when (kind) {
            PermissionKind.NOTIFICATION_ACCESS -> Icons.Filled.Notifications
            PermissionKind.OVERLAY -> Icons.Filled.Layers
            PermissionKind.NOTIFICATIONS -> Icons.Filled.NotificationsActive
            PermissionKind.FULL_SCREEN_INTENT -> Icons.Filled.Fullscreen
            PermissionKind.ACCESSIBILITY -> Icons.Filled.AccessibilityNew
            PermissionKind.BATTERY_OPTIMIZATION -> Icons.Filled.BatteryFull
        }
    }

@Composable
@ReadOnlyComposable
internal fun Attention.title(): String = when (this) {
    Attention.ServiceNotRunning -> stringResource(R.string.attention_not_running_title)

    is Attention.Missing -> stringResource(
        when (kind) {
            PermissionKind.NOTIFICATION_ACCESS -> R.string.perm_notification_title
            PermissionKind.OVERLAY -> R.string.perm_overlay_title
            PermissionKind.NOTIFICATIONS -> R.string.perm_post_notifications_title
            PermissionKind.FULL_SCREEN_INTENT -> R.string.perm_fsi_title
            PermissionKind.ACCESSIBILITY -> R.string.perm_accessibility_title
            PermissionKind.BATTERY_OPTIMIZATION -> R.string.perm_battery_title
        },
    )
}

/** Why it matters, in a line: the row on Home. */
@Composable
@ReadOnlyComposable
internal fun Attention.reason(): String = when (this) {
    Attention.ServiceNotRunning -> stringResource(R.string.attention_not_running_reason)

    is Attention.Missing -> stringResource(
        when (kind) {
            PermissionKind.NOTIFICATION_ACCESS -> R.string.perm_notification_reason
            PermissionKind.OVERLAY -> R.string.perm_overlay_reason
            PermissionKind.NOTIFICATIONS -> R.string.perm_post_notifications_reason
            PermissionKind.FULL_SCREEN_INTENT -> R.string.perm_fsi_reason
            PermissionKind.ACCESSIBILITY -> R.string.perm_accessibility_reason
            PermissionKind.BATTERY_OPTIMIZATION -> R.string.perm_battery_reason
        },
    )
}

/** The whole explanation: the sheet a row opens. */
@Composable
@ReadOnlyComposable
internal fun Attention.explanation(): String {
    val service = stringResource(R.string.accessibility_service_label)
    return when (this) {
        Attention.ServiceNotRunning -> stringResource(R.string.fallback_service_not_running, service)

        is Attention.Missing -> when (kind) {
            PermissionKind.NOTIFICATION_ACCESS -> stringResource(R.string.perm_notification_desc)
            PermissionKind.OVERLAY -> stringResource(R.string.perm_overlay_desc)
            PermissionKind.NOTIFICATIONS -> stringResource(R.string.perm_post_notifications_desc)
            PermissionKind.FULL_SCREEN_INTENT -> stringResource(R.string.perm_fsi_desc)
            PermissionKind.ACCESSIBILITY -> stringResource(R.string.perm_accessibility_desc, service)
            PermissionKind.BATTERY_OPTIMIZATION -> stringResource(R.string.perm_battery_desc)
        }
    }
}
