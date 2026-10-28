package app.rimlit.runtime

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Opens what a notification leads to, as tapping it in the shade would: its content intent, or its app when it has
 * none, in the notification's own user (a work profile's notification opens the work profile's app). Called once
 * the device is unlocked, while Rimlit's lock-screen activity is still the visible window, since Android lets an app
 * start activities from a pending intent only with that standing (and, from Android 14, only when it opts in).
 */
internal object NotificationOpener {

    private const val TAG = "NotificationOpener"

    /** The action that opens [posted], or `null` if it leads nowhere. */
    fun opener(context: Context, posted: StatusBarNotification): (() -> Unit)? {
        val content = posted.notification.contentIntent
        if (content != null) return { send(content) }
        val launcher = launcherActivity(context, posted.packageName, posted.user) ?: return null
        return { start(context, launcher, posted.user) }
    }

    private fun send(intent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                intent.send(senderOptions())
            } else {
                intent.send()
            }
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "The notification's content intent was cancelled; nothing opened", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to send the notification's content intent", e)
        }
    }

    /** [packageName]'s launcher activity in [user]; `null` if it has none, or [user] isn't a profile Rimlit sees. */
    private fun launcherActivity(context: Context, packageName: String, user: UserHandle): ComponentName? = try {
        context.getSystemService(LauncherApps::class.java)
            ?.getActivityList(packageName, user)
            ?.firstOrNull()
            ?.componentName
    } catch (e: SecurityException) {
        Log.w(TAG, "Can't see $packageName's launcher activity in $user", e)
        null
    }

    private fun start(context: Context, component: ComponentName, user: UserHandle) {
        try {
            context.getSystemService(LauncherApps::class.java)?.startMainActivity(component, user, null, null)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "The app's launcher activity is gone; nothing opened", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to start the app's launcher activity", e)
        }
    }

    /**
     * Android 14+ starts the content intent's activity with the sender's background-launch standing only if the
     * sender opts in. Rimlit sends it from its visible activity, so on Android 16+ it asks for exactly that
     * (`ALLOW_IF_VISIBLE`, the least it can grant). Android 14 and 15 only have `ALLOWED` (`ALLOW_ALWAYS`'s old
     * name), which also lends the target Rimlit's other standing, such as Display over other apps.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun senderOptions(): Bundle {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
        } else {
            @Suppress("DEPRECATION")
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
        return ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(mode).toBundle()
    }
}
