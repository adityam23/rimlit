package app.rimlit.engine

import android.app.NotificationManager
import app.rimlit.model.GlowSettings

/**
 * A posted notification, reduced to the facts the machine decides on. The listener captures it synchronously in
 * its callback, before the ranking map goes stale.
 */
data class Post(
    val key: String,
    val packageName: String,
    /** Posted by Rimlit itself: its test notification or the lock-screen trampoline. */
    val isOwn: Boolean,
    /** Rimlit's own test notification, which bypasses the master toggle. */
    val isTest: Boolean,
    /** Carries a full-screen intent: an incoming call, a ringing alarm, or Rimlit's own lock-screen trampoline. */
    val hasFullScreenIntent: Boolean,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    /** `FLAG_ONLY_ALERT_ONCE`: a re-post with it is a silent update. */
    val onlyAlertOnce: Boolean,
    /** The posting app's channel importance; `null` if the ranking was unavailable. */
    val importance: Int?,
    /** False when Do Not Disturb hides this notification. */
    val passesDnd: Boolean,
) {
    /**
     * An incoming call or alarm showing its own full-screen UI, which nothing may cover. Rimlit's own full-screen
     * intent is only the trampoline that wakes the screen into its lock-screen glow.
     */
    val isRinging: Boolean get() = hasFullScreenIntent && !isOwn
}

/** The user's settings allow a glow for this post. Our own test notification bypasses the master toggle. */
internal fun GlowSettings.wants(post: Post): Boolean =
    (post.isTest || (enabled && !post.isOwn)) && post.packageName !in blockedPackages

/** Mirrors the system's own alerting: only a post that alerts the user glows. [isUpdate]: its key was already up. */
internal fun Post.alerts(isUpdate: Boolean): Boolean = when {
    isOngoing && !isRinging -> false

    isGroupSummary -> false

    // Silent updates (alert-once) don't alert, so they don't glow.
    isUpdate && onlyAlertOnce -> false

    importance != null && importance < NotificationManager.IMPORTANCE_DEFAULT -> false

    else -> passesDnd
}
