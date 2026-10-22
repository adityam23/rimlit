package app.rimlit

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.rimlit.engine.GlowEvent
import app.rimlit.engine.Post
import app.rimlit.notify.GlowNotifications
import app.rimlit.notify.NotificationColorExtractor

/** Detects notifications from all apps and forwards them to the glow machine, which decides everything else. */
class GlowListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
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
        glowCoordinator.send(GlowEvent.ListenerDisconnected)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        sbn ?: return
        // The ranking carries the posting app's real channel importance and DND verdict (our own
        // NotificationManager can only see Rimlit's channels); read it now, before the map goes stale.
        val ranking = rankingMap?.let { map -> Ranking().takeIf { map.getRanking(sbn.key, it) } }
        glowCoordinator.posted(sbn.toPost(ranking)) { NotificationColorExtractor.extract(this, sbn) }
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
            isGroupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0,
            onlyAlertOnce = flags and Notification.FLAG_ONLY_ALERT_ONCE != 0,
            importance = ranking?.importance,
            passesDnd = ranking?.matchesInterruptionFilter() ?: true,
        )
    }
}
