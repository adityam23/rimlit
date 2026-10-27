package app.rimlit.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.permissions.Need
import app.rimlit.permissions.OverlayFallback
import app.rimlit.permissions.PermissionKind
import app.rimlit.permissions.PermissionStatus
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.SettingCard
import app.rimlit.ui.components.SettingIcon
import app.rimlit.ui.components.SettingsList
import app.rimlit.ui.components.SubScreenScaffold
import app.rimlit.ui.components.SwitchCard

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenApps: () -> Unit,
    onOpenStyle: () -> Unit,
    onOpenBehavior: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val fallback by viewModel.glowFallback.collectAsStateWithLifecycle()

    SubScreenScaffold(title = stringResource(R.string.app_name), onBack = null, modifier = modifier) { innerPadding ->
        SettingsList(innerPadding) {
            item {
                SwitchCard(
                    title = stringResource(R.string.master_toggle_title),
                    checked = settings.enabled,
                    onCheckedChange = { enabled -> viewModel.update { it.copy(enabled = enabled) } },
                    subtitle = stringResource(R.string.master_toggle_subtitle),
                    prominent = true,
                )
            }
            // Only while real glows fall back (see MainViewModel.glowFallback).
            fallback?.let { reason ->
                item {
                    NavCard(Icons.Filled.Info, reason.titleRes, reason.description()) {
                        viewModel.openPermission(PermissionKind.ACCESSIBILITY)
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.section_permissions)) }
            items(permissions, key = { it.kind.name }) { status ->
                SettingCard(
                    title = status.kind.title(),
                    subtitle = status.kind.description(),
                    onClick = { viewModel.openPermission(status.kind) },
                    leading = { SettingIcon(status.kind.icon()) },
                    trailing = { PermissionState(status) },
                )
            }
            item { SectionTitle(stringResource(R.string.section_settings)) }
            item { NavCard(Icons.Filled.Apps, R.string.nav_apps_title, R.string.nav_apps_subtitle, onOpenApps) }
            item { NavCard(Icons.Filled.Palette, R.string.nav_style_title, R.string.nav_style_subtitle, onOpenStyle) }
            item {
                NavCard(
                    Icons.Filled.Tune,
                    R.string.nav_behavior_title,
                    R.string.nav_behavior_subtitle,
                    onOpenBehavior,
                )
            }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.info_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavCard(icon: ImageVector, @StringRes titleRes: Int, @StringRes subtitleRes: Int, onClick: () -> Unit) =
    NavCard(icon, titleRes, stringResource(subtitleRes), onClick)

@Composable
private fun NavCard(icon: ImageVector, @StringRes titleRes: Int, subtitle: String, onClick: () -> Unit) {
    SettingCard(
        title = stringResource(titleRes),
        subtitle = subtitle,
        onClick = onClick,
        leading = { SettingIcon(icon) },
        trailing = { Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

@Composable
private fun PermissionState(status: PermissionStatus) {
    if (status.granted) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = stringResource(R.string.status_granted),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val need = status.kind.need
            Text(
                text = stringResource(
                    when (need) {
                        Need.REQUIRED -> R.string.status_needed
                        Need.RECOMMENDED -> R.string.status_recommended
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = when (need) {
                    Need.REQUIRED -> MaterialTheme.colorScheme.error
                    Need.RECOMMENDED -> MaterialTheme.colorScheme.tertiary
                },
            )
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
