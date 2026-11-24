package app.rimlit.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.rimlit.R
import app.rimlit.permissions.PermissionKind

// How a grant is shown, wherever it is asked for (Home's attention rows, setup's grant steps): its icon and words.

internal val PermissionKind.icon: ImageVector
    get() = when (this) {
        PermissionKind.NOTIFICATION_ACCESS -> Icons.Filled.Notifications
        PermissionKind.OVERLAY -> Icons.Filled.Layers
        PermissionKind.NOTIFICATIONS -> Icons.Filled.NotificationsActive
        PermissionKind.FULL_SCREEN_INTENT -> Icons.Filled.Fullscreen
        PermissionKind.ACCESSIBILITY -> Icons.Filled.AccessibilityNew
        PermissionKind.BATTERY_OPTIMIZATION -> Icons.Filled.BatteryFull
    }

@Composable
@ReadOnlyComposable
internal fun PermissionKind.title(): String = stringResource(
    when (this) {
        PermissionKind.NOTIFICATION_ACCESS -> R.string.perm_notification_title
        PermissionKind.OVERLAY -> R.string.perm_overlay_title
        PermissionKind.NOTIFICATIONS -> R.string.perm_post_notifications_title
        PermissionKind.FULL_SCREEN_INTENT -> R.string.perm_fsi_title
        PermissionKind.ACCESSIBILITY -> R.string.perm_accessibility_title
        PermissionKind.BATTERY_OPTIMIZATION -> R.string.perm_battery_title
    },
)

/** Why it matters, in a line. */
@Composable
@ReadOnlyComposable
internal fun PermissionKind.reason(): String = stringResource(
    when (this) {
        PermissionKind.NOTIFICATION_ACCESS -> R.string.perm_notification_reason
        PermissionKind.OVERLAY -> R.string.perm_overlay_reason
        PermissionKind.NOTIFICATIONS -> R.string.perm_post_notifications_reason
        PermissionKind.FULL_SCREEN_INTENT -> R.string.perm_fsi_reason
        PermissionKind.ACCESSIBILITY -> R.string.perm_accessibility_reason
        PermissionKind.BATTERY_OPTIMIZATION -> R.string.perm_battery_reason
    },
)

/** The whole explanation. */
@Composable
@ReadOnlyComposable
internal fun PermissionKind.explanation(): String {
    val service = stringResource(R.string.accessibility_service_label)
    return when (this) {
        PermissionKind.NOTIFICATION_ACCESS -> stringResource(R.string.perm_notification_desc)
        PermissionKind.OVERLAY -> stringResource(R.string.perm_overlay_desc)
        PermissionKind.NOTIFICATIONS -> stringResource(R.string.perm_post_notifications_desc)
        PermissionKind.FULL_SCREEN_INTENT -> stringResource(R.string.perm_fsi_desc)
        PermissionKind.ACCESSIBILITY -> stringResource(R.string.perm_accessibility_desc, service)
        PermissionKind.BATTERY_OPTIMIZATION -> stringResource(R.string.perm_battery_desc)
    }
}
