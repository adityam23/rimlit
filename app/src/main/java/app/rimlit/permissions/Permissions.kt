package app.rimlit.permissions

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri

/** Special-access grants the feature depends on. */
enum class PermissionKind {
    NOTIFICATION_ACCESS,
    OVERLAY,
    FULL_SCREEN_INTENT,
    BATTERY_OPTIMIZATION,
}

data class PermissionStatus(
    val kind: PermissionKind,
    val granted: Boolean,
)

object PermissionChecks {

    fun statuses(context: Context): List<PermissionStatus> = PermissionKind.entries.map { kind ->
        PermissionStatus(
            kind = kind,
            granted = when (kind) {
                PermissionKind.NOTIFICATION_ACCESS -> hasNotificationAccess(context)
                PermissionKind.OVERLAY -> hasOverlayPermission(context)
                PermissionKind.FULL_SCREEN_INTENT -> canUseFullScreenIntent(context)
                PermissionKind.BATTERY_OPTIMIZATION -> ignoresBatteryOptimizations(context)
            },
        )
    }

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager != null && manager.canUseFullScreenIntent()
    }

    fun ignoresBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        return powerManager != null && powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}

/** Deep links to the exact system settings page for each grant. */
object PermissionLinks {

    fun notificationAccess(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun overlay(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun fullScreenIntent(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(context))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } else {
            notificationAppSettings(context)
        }
    }

    fun batteryOptimization(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun notificationAppSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun packageUri(context: Context) = "package:${context.packageName}".toUri()
}
