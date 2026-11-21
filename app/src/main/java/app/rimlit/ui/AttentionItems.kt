package app.rimlit.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.rimlit.R
import app.rimlit.permissions.Need
import app.rimlit.permissions.OverlayFallback
import app.rimlit.permissions.PermissionKind
import app.rimlit.permissions.PermissionStatus
import app.rimlit.ui.components.NavCard
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.SettingCard
import app.rimlit.ui.components.SettingIcon

/**
 * Home's items for what needs the user: why real glows fall back to the app overlay ([fallback]), and each permission
 * still [missing] (a granted one needs nothing more). Nothing at all once both are fine. [onOpen] opens a permission's
 * settings.
 */
internal fun LazyListScope.attentionItems(
    fallback: OverlayFallback?,
    missing: List<PermissionStatus>,
    onOpen: (PermissionKind) -> Unit,
) {
    fallback?.let { reason ->
        item {
            NavCard(
                icon = Icons.Filled.Info,
                title = stringResource(reason.titleRes),
                subtitle = reason.description(),
                onClick = { onOpen(PermissionKind.ACCESSIBILITY) },
            )
        }
    }
    if (missing.isEmpty()) return
    item { SectionTitle(stringResource(R.string.section_attention)) }
    items(missing, key = { it.kind.name }) { status ->
        SettingCard(
            title = status.kind.title(),
            subtitle = status.kind.description(),
            onClick = { onOpen(status.kind) },
            leading = { SettingIcon(status.kind.icon()) },
            trailing = { PermissionState(status) },
        )
    }
}

/** How much a missing permission is needed: required or recommended. */
@Composable
private fun PermissionState(status: PermissionStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val need = status.kind.need
        Text(
            text = stringResource(need.labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = need.color(),
        )
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun PermissionKind.icon(): ImageVector = when (this) {
    PermissionKind.NOTIFICATION_ACCESS -> Icons.Filled.Notifications
    PermissionKind.OVERLAY -> Icons.Filled.Layers
    PermissionKind.NOTIFICATIONS -> Icons.Filled.NotificationsActive
    PermissionKind.FULL_SCREEN_INTENT -> Icons.Filled.Fullscreen
    PermissionKind.ACCESSIBILITY -> Icons.Filled.AccessibilityNew
    PermissionKind.BATTERY_OPTIMIZATION -> Icons.Filled.BatteryFull
}

private val Need.labelRes
    get() = when (this) {
        Need.REQUIRED -> R.string.status_needed
        Need.RECOMMENDED -> R.string.status_recommended
    }

@Composable
@ReadOnlyComposable
private fun Need.color(): Color = when (this) {
    Need.REQUIRED -> MaterialTheme.colorScheme.error
    Need.RECOMMENDED -> MaterialTheme.colorScheme.tertiary
}

private val OverlayFallback.titleRes
    get() = when (this) {
        OverlayFallback.SERVICE_OFF, OverlayFallback.SERVICE_NOT_RUNNING -> R.string.fallback_title
        OverlayFallback.NO_OVERLAY -> R.string.no_overlay_title
    }

@Composable
@ReadOnlyComposable
private fun OverlayFallback.description(): String = stringResource(
    when (this) {
        OverlayFallback.SERVICE_OFF -> R.string.fallback_service_off
        OverlayFallback.SERVICE_NOT_RUNNING -> R.string.fallback_service_not_running
        OverlayFallback.NO_OVERLAY -> R.string.fallback_no_overlay
    },
    stringResource(R.string.accessibility_service_label),
)

@Composable
@ReadOnlyComposable
private fun PermissionKind.title(): String = stringResource(
    when (this) {
        PermissionKind.NOTIFICATION_ACCESS -> R.string.perm_notification_title
        PermissionKind.OVERLAY -> R.string.perm_overlay_title
        PermissionKind.NOTIFICATIONS -> R.string.perm_post_notifications_title
        PermissionKind.FULL_SCREEN_INTENT -> R.string.perm_fsi_title
        PermissionKind.ACCESSIBILITY -> R.string.perm_accessibility_title
        PermissionKind.BATTERY_OPTIMIZATION -> R.string.perm_battery_title
    },
)

@Composable
@ReadOnlyComposable
private fun PermissionKind.description(): String = when (this) {
    PermissionKind.NOTIFICATION_ACCESS -> stringResource(R.string.perm_notification_desc)

    PermissionKind.OVERLAY -> stringResource(R.string.perm_overlay_desc)

    PermissionKind.NOTIFICATIONS -> stringResource(R.string.perm_post_notifications_desc)

    PermissionKind.FULL_SCREEN_INTENT -> stringResource(R.string.perm_fsi_desc)

    PermissionKind.ACCESSIBILITY ->
        stringResource(R.string.perm_accessibility_desc, stringResource(R.string.accessibility_service_label))

    PermissionKind.BATTERY_OPTIMIZATION -> stringResource(R.string.perm_battery_desc)
}
