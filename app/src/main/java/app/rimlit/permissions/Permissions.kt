package app.rimlit.permissions

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import app.rimlit.notify.GlowNotifications
import app.rimlit.overlay.GlowAccessibilityService

/** How much the glow depends on a grant. */
enum class Need {
    /** Without it, some glows never show. */
    REQUIRED,

    /** Glows show without it, but worse (delayed, or with Android's overlay notice). */
    RECOMMENDED,
}

/** Special-access grants the feature depends on, and how much ([need]). */
enum class PermissionKind(val need: Need = Need.REQUIRED) {
    NOTIFICATION_ACCESS,
    OVERLAY,

    /** Notifications allowed and the glow channel unblocked: the lock-screen trampoline is a notification. */
    NOTIFICATIONS,
    FULL_SCREEN_INTENT,

    /** The accessibility overlay: also above the lock screen, and without Android's overlay notice. */
    ACCESSIBILITY(Need.RECOMMENDED),
    BATTERY_OPTIMIZATION(Need.RECOMMENDED),
}

data class PermissionStatus(val kind: PermissionKind, val granted: Boolean)

/**
 * Why on-screen glows fall back to the app overlay (`TYPE_APPLICATION_OVERLAY`), which makes Android post its own
 * "Rimlit is displaying over other apps" notification while one is up.
 */
enum class OverlayFallback {
    /** The accessibility service is off. */
    SERVICE_OFF,

    /** The accessibility service is on in settings, but the system hasn't bound it (killed, or stuck after updates). */
    SERVICE_NOT_RUNNING,
}

/**
 * Why on-screen glows would fall back to the app overlay now, given the grants in [statuses] and whether the
 * accessibility service is [serviceConnected]; `null` if they don't: the service draws them, or without Display over
 * other apps nothing does.
 */
fun overlayFallback(statuses: List<PermissionStatus>, serviceConnected: Boolean): OverlayFallback? {
    fun granted(kind: PermissionKind) = statuses.any { it.kind == kind && it.granted }
    return when {
        serviceConnected || !granted(PermissionKind.OVERLAY) -> null
        granted(PermissionKind.ACCESSIBILITY) -> OverlayFallback.SERVICE_NOT_RUNNING
        else -> OverlayFallback.SERVICE_OFF
    }
}

/** Whether the user has granted this. */
fun PermissionKind.isGranted(context: Context): Boolean = when (this) {
    PermissionKind.NOTIFICATION_ACCESS ->
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    PermissionKind.OVERLAY -> Settings.canDrawOverlays(context)

    PermissionKind.NOTIFICATIONS -> GlowNotifications.canPostLockScreenWake(context)

    PermissionKind.FULL_SCREEN_INTENT ->
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true

    // On in Accessibility settings, bound or not: the system's own list of enabled services holds only bound ones,
    // so a service that is on but not running would read as off.
    PermissionKind.ACCESSIBILITY -> {
        val service = ComponentName(context, GlowAccessibilityService::class.java)
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')
            ?.any { ComponentName.unflattenFromString(it) == service } == true
    }

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
