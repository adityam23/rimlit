package app.rimlit.runtime

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Opens what a notification leads to, as tapping it in the shade would: its content intent, or its app when it has
 * none. Called once the device is unlocked, while Rimlit's lock-screen activity is still the visible window, since
 * Android lets an app start activities from a pending intent only with that standing (and, from Android 14, only
 * when it opts in).
 */
internal object NotificationOpener {

    private const val TAG = "NotificationOpener"

    /** The action that opens [notification] from [packageName], or `null` if it leads nowhere. */
    fun opener(context: Context, packageName: String, notification: Notification): (() -> Unit)? {
        val content = notification.contentIntent
        val launch = if (content == null) context.packageManager.getLaunchIntentForPackage(packageName) else null
        return when {
            content != null -> { -> send(content) }
            launch != null -> { -> start(context, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            else -> null
        }
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

    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
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
