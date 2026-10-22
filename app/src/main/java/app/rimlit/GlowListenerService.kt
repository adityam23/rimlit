package app.rimlit

import android.app.KeyguardManager
import android.app.Notification
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.rimlit.data.SettingsRepository
import app.rimlit.engine.Post
import app.rimlit.engine.alerts
import app.rimlit.engine.wants
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSpec
import app.rimlit.notify.GlowNotifications
import app.rimlit.notify.NotificationColorExtractor
import app.rimlit.overlay.EdgeOverlayController
import app.rimlit.overlay.GlowAccessibilityService
import app.rimlit.permissions.PermissionKind
import app.rimlit.permissions.isGranted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Detects notifications from all apps and routes them to the lock-screen or on-screen glow. */
class GlowListenerService : NotificationListenerService() {

    // Main.immediate: once settings are loaded, handlers run synchronously, preserving callback order.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { SettingsRepository(this) }
    private val overlayController by lazy { EdgeOverlayController(this) }

    /** `null` until DataStore has emitted — never decide on defaults, or the first notification is dropped. */
    private val settings: StateFlow<GlowSettings?> by lazy {
        repository.settings.stateIn(scope, SharingStarted.Eagerly, null)
    }

    /** The notification driving an overlay, and which overlay, so only its own dismissal/answer hides the glow. */
    private data class OverlaySource(val key: String, val ringing: Boolean, val overlay: EdgeOverlayController)

    private var overlaySource: OverlaySource? = null

    /** Keys currently posted, so a re-post can be recognised as an update to an existing notification. */
    private val activeKeys = mutableSetOf<String>()

    /** Whether the missing full-screen-intent grant was logged since the listener connected. */
    private var warnedNoFullScreenIntent = false

    override fun onListenerConnected() {
        super.onListenerConnected()
        GlowNotifications.createChannels(this)
        warnedNoFullScreenIntent = false
        activeKeys.clear()
        activeNotifications?.mapTo(activeKeys) { it.key }
        settings // start loading before the first notification arrives
    }

    override fun onDestroy() {
        overlayController.remove()
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        sbn ?: return
        // The ranking carries the posting app's real channel importance and DND verdict (our own
        // NotificationManager can only see Rimlit's channels); read it now, before the map goes stale.
        val ranking = rankingMap?.let { map -> Ranking().takeIf { map.getRanking(sbn.key, it) } }
        val post = sbn.toPost(ranking)
        val isUpdate = !activeKeys.add(sbn.key)
        scope.launch { handlePosted(sbn, post, isUpdate, settings.filterNotNull().first()) }
    }

    /** The facts the filter decides on, captured now: the ranking map goes stale after this callback. */
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

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        activeKeys.remove(sbn.key)
        overlaySource?.takeIf { it.key == sbn.key }?.let { dismissOverlay(it) }
    }

    private fun handlePosted(sbn: StatusBarNotification, post: Post, isUpdate: Boolean, current: GlowSettings) {
        val isRinging = post.isRinging
        // A ringing event (incoming call/alarm) that ends is re-delivered with the
        // same key but no full-screen intent (e.g. call answered) — stop the glow.
        if (!isRinging) overlaySource?.takeIf { it.ringing && it.key == sbn.key }?.let { dismissOverlay(it) }

        if (!current.wants(post) || !post.alerts(isUpdate)) return
        val spec = current.toGlowSpec(sbn.packageName) { NotificationColorExtractor.extract(this, sbn) }
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive == true
        if (!interactive || LockScreenGlowActivity.isVisible) {
            // Screen off (with or without a keyguard): only the full-screen activity can wake it, and while
            // its AOD glow is up it takes re-deliveries too. Ringing events never take this path: their own
            // full-screen UI (call/alarm screen) is already launching, and our opaque activity would cover it.
            if (current.showWhenLocked && !isRinging) showLockScreenGlow(spec)
            return
        }
        // Screen on. The accessibility overlay draws above the lock screen too; the app overlay is hidden
        // by it, so without the accessibility service a visible lock screen gets no glow.
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val wanted = if (locked) current.showWhenLocked else current.showWhenUnlocked
        val overlay = GlowAccessibilityService.overlay ?: overlayController.takeIf { !locked }
        if (wanted && overlay != null) {
            overlay.show(spec)
            overlaySource = OverlaySource(sbn.key, isRinging, overlay)
        }
    }

    private fun dismissOverlay(source: OverlaySource) {
        overlaySource = null
        source.overlay.dismiss()
    }

    private fun showLockScreenGlow(spec: GlowSpec) {
        val intent = LockScreenGlowActivity.newIntent(this, spec)
        // Already glowing: a second full-screen intent would degrade to a heads-up banner over the glow.
        // Our visible window exempts us from background-launch limits, so re-deliver directly instead.
        if (LockScreenGlowActivity.isVisible) {
            startActivity(intent)
            return
        }
        // Without the grant (Android 14+) the trampoline can't wake the screen; it would only show as a notification.
        if (!PermissionKind.FULL_SCREEN_INTENT.isGranted(this)) {
            if (!warnedNoFullScreenIntent) {
                Log.w(TAG, "Full-screen intent access denied: skipping lock-screen glows until it is granted")
                warnedNoFullScreenIntent = true
            }
            return
        }
        GlowNotifications.postLockScreenWake(this, intent, spec.durationMs)
    }

    private companion object {
        const val TAG = "GlowListenerService"
    }
}
