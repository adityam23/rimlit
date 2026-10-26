package app.rimlit.permissions

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import app.rimlit.notify.GlowNotifications

/** Special-access grants the feature depends on; [optional] ones only unlock extra behaviour. */
enum class PermissionKind(val optional: Boolean = false) {
    NOTIFICATION_ACCESS,
    OVERLAY,

    /** Notifications allowed and the glow channel unblocked: the lock-screen trampoline is a notification. */
    NOTIFICATIONS,
    FULL_SCREEN_INTENT,
    ACCESSIBILITY(optional = true),
    BATTERY_OPTIMIZATION,
}

data class PermissionStatus(val kind: PermissionKind, val granted: Boolean)

/** Whether the user has granted this. */
fun PermissionKind.isGranted(context: Context): Boolean = when (this) {
    PermissionKind.NOTIFICATION_ACCESS ->
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    PermissionKind.OVERLAY -> Settings.canDrawOverlays(context)

    PermissionKind.NOTIFICATIONS -> GlowNotifications.canPostLockScreenWake(context)

    PermissionKind.FULL_SCREEN_INTENT ->
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true

    PermissionKind.ACCESSIBILITY ->
        context.getSystemService(AccessibilityManager::class.java)
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.any { it.resolveInfo.serviceInfo.packageName == context.packageName } == true

    PermissionKind.BATTERY_OPTIMIZATION ->
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
}

/** The exact system settings page where the user grants this. */
private fun PermissionKind.settingsIntent(context: Context): Intent {
    val packageUri = "package:${context.packageName}".toUri()
    return when (this) {
        PermissionKind.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

        PermissionKind.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)

        PermissionKind.NOTIFICATIONS -> appNotificationSettings(context)

        PermissionKind.FULL_SCREEN_INTENT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri)
        } else {
            appNotificationSettings(context)
        }

        PermissionKind.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        PermissionKind.BATTERY_OPTIMIZATION -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

private fun appNotificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

object Permissions {

    fun statuses(context: Context): List<PermissionStatus> =
        PermissionKind.entries.map { PermissionStatus(it, it.isGranted(context)) }

    /** Opens the system settings page for [kind]. */
    fun open(context: Context, kind: PermissionKind) {
        context.startActivity(kind.settingsIntent(context))
    }
}
