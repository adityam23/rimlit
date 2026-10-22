package app.rimlit.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.StatusBarNotification
import androidx.annotation.StringRes
import app.rimlit.R

/** Rimlit's own notifications: the full-screen-intent trampoline that wakes the screen, and the test notification. */
object GlowNotifications {

    private const val GLOW_CHANNEL_ID = "glow"
    private const val TEST_CHANNEL_ID = "glow_test"
    private const val GLOW_NOTIFICATION_ID = 1
    private const val TEST_NOTIFICATION_ID = 100

    /** Creates (or refreshes the names of) both channels; safe to call repeatedly. */
    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                quietChannel(
                    context,
                    GLOW_CHANNEL_ID,
                    R.string.notification_channel_name,
                    R.string.notification_channel_desc,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC },
                // DEFAULT, not HIGH: a heads-up banner would cover the glow being tested.
                quietChannel(
                    context,
                    TEST_CHANNEL_ID,
                    R.string.test_notification_channel_name,
                    R.string.test_notification_channel_desc,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }

    /** Rimlit's own test notification. The package must match too: another app could reuse the channel id. */
    fun isTest(context: Context, sbn: StatusBarNotification): Boolean =
        sbn.packageName == context.packageName && sbn.notification.channelId == TEST_CHANNEL_ID

    /** Posts a test notification that goes through the real listener pipeline and times out after [durationMs]. */
    fun postTest(context: Context, durationMs: Long) {
        createChannels(context)
        val notification = Notification.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.test_notification_title))
            .setContentText(context.getString(R.string.test_notification_desc))
            .setAutoCancel(true)
            .setTimeoutAfter(durationMs)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(TEST_NOTIFICATION_ID, notification)
    }

    /** Wakes the screen into [activity] through a full-screen intent; the trampoline times out after [durationMs]. */
    fun postLockScreenWake(context: Context, activity: Intent, durationMs: Long) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            activity,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, GLOW_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_glow_title))
            .setFullScreenIntent(pendingIntent, true)
            // Only a trampoline to wake the screen: never bridge it to a watch, where it would mask the real one.
            .setLocalOnly(true)
            .setAutoCancel(true)
            .setTimeoutAfter(durationMs)
            .build()
        // Updating a live notification doesn't re-alert, so its full-screen intent wouldn't fire again.
        cancelLockScreenWake(context)
        context.getSystemService(NotificationManager::class.java).notify(GLOW_NOTIFICATION_ID, notification)
    }

    /** Removes the trampoline once its lock-screen glow has ended. */
    fun cancelLockScreenWake(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(GLOW_NOTIFICATION_ID)
    }

    /** A channel that never makes a sound, vibrates or badges: the glow is the only signal. */
    private fun quietChannel(
        context: Context,
        id: String,
        @StringRes name: Int,
        @StringRes description: Int,
        importance: Int,
    ) = NotificationChannel(id, context.getString(name), importance).apply {
        this.description = context.getString(description)
        setSound(null, null)
        enableVibration(false)
        setShowBadge(false)
    }
}
