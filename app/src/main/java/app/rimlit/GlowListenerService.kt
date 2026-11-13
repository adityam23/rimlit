package app.rimlit

import android.app.Notification
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.rimlit.engine.GlowEvent
import app.rimlit.engine.GroupAlert
import app.rimlit.engine.GroupRole
import app.rimlit.engine.Post
import app.rimlit.notify.GlowNotifications

/** Detects notifications from all apps and forwards them to the glow machine, which decides everything else. */
class GlowListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = this
        GlowNotifications.createChannels(this)
        val posts = activeNotifications.orEmpty().map { it.toPost(ranking = null) }
        glowCoordinator.send(
            GlowEvent.ListenerConnected(
                activeKeys = posts.mapTo(mutableSetOf()) { it.key },
                ringingKeys = posts.filter { it.isRinging }.mapTo(mutableSetOf()) { it.key },
            ),
        )
    }

    override fun onListenerDisconnected() {
        forget()
        glowCoordinator.send(GlowEvent.ListenerDisconnected)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        forget()
        super.onDestroy()
    }

    private fun forget() {
        if (connected === this) connected = null
    }

    /**
     * The notification posted under [key] now, with its app and user; `null` if it is gone or the listener was
     * unbound, including when access was revoked before it noticed (the system then rejects the call). Any thread.
     */
    fun posted(key: String): StatusBarNotification? = try {
        getActiveNotifications(arrayOf(key))?.firstOrNull()
    } catch (e: SecurityException) {
        Log.w(TAG, "Notification access is gone; reading $key failed", e)
        null
    }

    /** [posted]'s notification alone. Any thread. */
    fun notification(key: String): Notification? = posted(key)?.notification

    /**
     * Dismisses the notification posted under [key], as the user would in the shade (the system keeps ongoing and
     * non-clearable ones). Main thread.
     */
    fun cancel(key: String) {
        try {
            cancelNotification(key)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification access is gone; cancelling $key failed", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        sbn ?: return
        // The ranking carries the posting app's real channel importance and DND verdict (our own
        // NotificationManager can only see Rimlit's channels); read it now, before the map goes stale.
        val ranking = rankingMap?.let { map -> Ranking().takeIf { map.getRanking(sbn.key, it) } }
        glowCoordinator.send(GlowEvent.Posted(sbn.toPost(ranking)))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        glowCoordinator.send(GlowEvent.Removed(sbn.key))
    }

    /** The facts the machine decides on, captured now: the ranking map goes stale after this callback. */
    private fun StatusBarNotification.toPost(ranking: Ranking?): Post {
        val flags = notification.flags
        return Post(
            key = key,
            packageName = packageName,
            isOwn = packageName == this@GlowListenerService.packageName,
            isTest = GlowNotifications.isTest(this@GlowListenerService, this),
            hasFullScreenIntent = notification.fullScreenIntent != null,
            isOngoing = isOngoing,
            group = when {
                notification.group == null -> GroupRole.NONE
                flags and Notification.FLAG_GROUP_SUMMARY != 0 -> GroupRole.SUMMARY
                else -> GroupRole.CHILD
            },
            groupAlert = when (notification.groupAlertBehavior) {
                Notification.GROUP_ALERT_SUMMARY -> GroupAlert.SUMMARY
                Notification.GROUP_ALERT_CHILDREN -> GroupAlert.CHILDREN
                else -> GroupAlert.ALL
            },
            onlyAlertOnce = flags and Notification.FLAG_ONLY_ALERT_ONCE != 0,
            importance = ranking?.importance,
            passesDnd = ranking?.matchesInterruptionFilter() ?: true,
            hiddenOnLockScreen = hiddenOnLockScreen(ranking),
        )
    }

    /**
     * Android keeps this notification off the lock screen, by the rules its own keyguard applies: the notification
     * or its channel is secret, or the user shows no notifications there. Unknown counts as hidden: without the
     * ranking, or if the setting can't be read, nothing may name the app above a locked screen.
     */
    private fun StatusBarNotification.hiddenOnLockScreen(ranking: Ranking?): Boolean {
        val secret = ranking == null ||
            notification.visibility == Notification.VISIBILITY_SECRET ||
            ranking.channel?.lockscreenVisibility == Notification.VISIBILITY_SECRET
        if (secret) return true
        // The user's "notifications on lock screen" setting is "don't show any".
        return try {
            Settings.Secure.getInt(contentResolver, LOCK_SCREEN_SHOW_NOTIFICATIONS, 1) == 0
        } catch (e: SecurityException) {
            Log.w(TAG, "Can't read the lock screen's notification setting; treating notifications as hidden there", e)
            true
        }
    }

    companion object {
        private const val TAG = "GlowListenerService"

        /** `Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS`: hidden from the SDK, but readable by any app. */
        private const val LOCK_SCREEN_SHOW_NOTIFICATIONS = "lock_screen_show_notifications"

        /** Set only between connect and disconnect/destroy, so it never outlives the service. Main thread only. */
        var connected: GlowListenerService? = null
            private set
    }
}
