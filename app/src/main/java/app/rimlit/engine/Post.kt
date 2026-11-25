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
    /**
     * A re-post under its key is a silent update (see [updatesSilently]); its first post alerts, and so does the
     * re-post that makes it ring.
     */
    val updatesSilently: Boolean,
    /** The posting app's channel importance; `null` if the ranking was unavailable. */
    val importance: Int?,
    /** False when Do Not Disturb hides this notification. */
    val passesDnd: Boolean,
    /**
     * Android keeps this notification off the lock screen: it or its channel is secret, or the user shows no
     * notifications there. While locked, nothing may then name its app or act on it (see [GlowState.keepsSecret]).
     */
    val hiddenOnLockScreen: Boolean,
) {
    /**
     * An incoming call or alarm showing its own full-screen UI, which nothing may cover: ongoing, with a full-screen
     * intent. A non-ongoing one (a reminder left in the shade) doesn't ring, or it would block every lock-screen glow
     * until cleared. Rimlit's own full-screen intent is only the trampoline that wakes the screen into its glow.
     */
    val isRinging: Boolean get() = rings(hasFullScreenIntent, isOngoing, isOwn)
}

/** The ringing rule ([Post.isRinging]), for a notification not reduced to a [Post]. */
fun rings(hasFullScreenIntent: Boolean, isOngoing: Boolean, isOwn: Boolean): Boolean =
    hasFullScreenIntent && isOngoing && !isOwn

/** `Notification.FLAG_ONLY_ALERT_ONCE` and `FLAG_FOREGROUND_SERVICE`, restated so the engine stays free of Android. */
private const val FLAG_ONLY_ALERT_ONCE = 0x8
private const val FLAG_FOREGROUND_SERVICE = 0x40

/**
 * The one silent-update rule ([Post.updatesSilently]), from a notification's raw `flags` and progress extras. Its
 * re-posts are silent when it is alert-once (Android sounds only its first post), a foreground service's status
 * (Android doesn't count its re-posts as visually interruptive), or a running progress bar, indeterminate or short of
 * its max: a task's status, re-posted on every tick (Android 16's `ProgressStyle` writes the same extras). A finished
 * bar (progress at max, "Download complete") is news: Android counts completing as visually interruptive too.
 */
fun updatesSilently(flags: Int, progress: Int, progressMax: Int, progressIndeterminate: Boolean): Boolean {
    val progressRunning = progressIndeterminate || (progressMax > 0 && progress < progressMax)
    return flags and (FLAG_ONLY_ALERT_ONCE or FLAG_FOREGROUND_SERVICE) != 0 || progressRunning
}

/** A post's place in its app's notification group: grouped when its notification has a group key. */
enum class GroupRole { NONE, CHILD, SUMMARY }

/** `Notification.GROUP_ALERT_*`: which posts of a group alert. */
enum class GroupAlert { ALL, SUMMARY, CHILDREN }

/**
 * The user's settings allow a glow for this post: on, and its app switched on (see [GlowSettings.glowsFor]). Our own
 * test notification bypasses both, the master toggle and the app list, which never lists Rimlit.
 */
internal fun GlowSettings.wants(post: Post): Boolean =
    post.isTest || (enabled && !post.isOwn && glowsFor(post.packageName))

/**
 * The one lock-screen privacy rule: while the device is locked, [post] is one Android hides there, so the glow may
 * show (it names nothing) but no pill names its app and no gesture clears it. Opening stays possible: it unlocks first.
 */
internal fun GlowState.keepsSecret(post: Post): Boolean = environment.locked && post.hiddenOnLockScreen

/** `NotificationManager.IMPORTANCE_DEFAULT`, restated so the engine stays free of Android: below it, nothing alerts. */
private const val IMPORTANCE_DEFAULT = 3

/**
 * Only a post that alerts the user glows, close to the system's own alerting. [isUpdate]: its key was already up;
 * [wasRinging]: it was already ringing.
 */
internal fun Post.alerts(isUpdate: Boolean, wasRinging: Boolean): Boolean = when {
    isOngoing && !isRinging -> false

    // One glow per alert: a summary only when its group alerts through it, a child unless it does. Under ALL the
    // system alerts for both; the child alone glows, so posting a child with its summary doesn't glow twice.
    group == GroupRole.SUMMARY && groupAlert != GroupAlert.SUMMARY -> false

    group == GroupRole.CHILD && groupAlert == GroupAlert.SUMMARY -> false

    // Silent updates (alert-once, a running progress bar, a service's status) don't glow, except the re-post that
    // starts ringing: a VoIP app's "Connecting…" service notification re-posted as the incoming call.
    isUpdate && updatesSilently && (wasRinging || !isRinging) -> false

    importance != null && importance < IMPORTANCE_DEFAULT -> false

    else -> passesDnd
}
