package app.rimlit.engine

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
    val group: GroupRole,
    /** Which of its group's posts alert (`Notification.getGroupAlertBehavior`); only matters in a group. */
    val groupAlert: GroupAlert,
    /** `FLAG_ONLY_ALERT_ONCE`: a re-post with it is a silent update. */
    val onlyAlertOnce: Boolean,
    /** The posting app's channel importance; `null` if the ranking was unavailable. */
    val importance: Int?,
    /** False when Do Not Disturb hides this notification. */
    val passesDnd: Boolean,
) {
    /**
     * An incoming call or alarm showing its own full-screen UI, which nothing may cover: ongoing, with a full-screen
     * intent. A non-ongoing one (a reminder left in the shade) doesn't ring, or it would block every lock-screen glow
     * until cleared. Rimlit's own full-screen intent is only the trampoline that wakes the screen into its glow.
     */
    val isRinging: Boolean get() = hasFullScreenIntent && isOngoing && !isOwn
}

/** A post's place in its app's notification group: grouped when its notification has a group key. */
enum class GroupRole { NONE, CHILD, SUMMARY }

/** `Notification.GROUP_ALERT_*`: which posts of a group alert. */
enum class GroupAlert { ALL, SUMMARY, CHILDREN }

/** The user's settings allow a glow for this post. Our own test notification bypasses the master toggle. */
internal fun GlowSettings.wants(post: Post): Boolean =
    (post.isTest || (enabled && !post.isOwn)) && post.packageName !in blockedPackages

/** `NotificationManager.IMPORTANCE_DEFAULT`, restated so the engine stays free of Android: below it, nothing alerts. */
private const val IMPORTANCE_DEFAULT = 3

/** Mirrors the system's own alerting: only a post that alerts the user glows. [isUpdate]: its key was already up. */
internal fun Post.alerts(isUpdate: Boolean): Boolean = when {
    isOngoing && !isRinging -> false

    // One glow per alert: a summary only when its group alerts through it, a child unless it does. Under ALL the
    // system alerts for both; the child alone glows, so posting a child with its summary doesn't glow twice.
    group == GroupRole.SUMMARY && groupAlert != GroupAlert.SUMMARY -> false

    group == GroupRole.CHILD && groupAlert == GroupAlert.SUMMARY -> false

    // Silent updates (alert-once) don't alert, so they don't glow.
    isUpdate && onlyAlertOnce -> false

    importance != null && importance < IMPORTANCE_DEFAULT -> false

    else -> passesDnd
}
