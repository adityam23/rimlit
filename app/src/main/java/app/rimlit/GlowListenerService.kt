package app.rimlit

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.rimlit.data.SettingsRepository
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSpec
import app.rimlit.notify.NotificationColorExtractor
import app.rimlit.overlay.EdgeOverlayController
import app.rimlit.overlay.GlowAccessibilityService
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

    /** What the system decided about a post, captured synchronously before the ranking map goes stale. */
    private data class PostFacts(
        /** Posting app's channel importance; `null` if the ranking was unavailable. */
        val importance: Int?,
        /** False when Do Not Disturb hides this notification. */
        val passesDnd: Boolean,
        /** Re-post of a notification that was already showing. */
        val isUpdate: Boolean,
    )

    override fun onListenerConnected() {
        super.onListenerConnected()
        createChannel()
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
        val facts = PostFacts(
            importance = ranking?.importance,
            passesDnd = ranking?.matchesInterruptionFilter() ?: true,
            isUpdate = !activeKeys.add(sbn.key),
        )
        scope.launch { handlePosted(sbn, facts, settings.filterNotNull().first()) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        activeKeys.remove(sbn.key)
        val source = overlaySource
        if (source != null && sbn.key == source.key) {
            overlaySource = null
            source.overlay.dismiss()
        }
    }

    private fun handlePosted(sbn: StatusBarNotification, facts: PostFacts, current: GlowSettings) {
        val notification = sbn.notification
        // Only our own test channel may bypass the master toggle — another app could reuse the channel id.
        val isTest = sbn.packageName == packageName && notification.channelId == TEST_CHANNEL_ID
        val isRinging = notification.fullScreenIntent != null

        // A ringing event (incoming call/alarm) that ends is re-delivered with the
        // same key but no full-screen intent (e.g. call answered) — stop the glow.
        val source = overlaySource
        if (source != null && source.ringing && sbn.key == source.key && !isRinging) {
            overlaySource = null
            source.overlay.dismiss()
        }

        if (!current.enabled && !isTest) return
        if (sbn.packageName == packageName && !isTest) return
        if (sbn.isOngoing && !isRinging) return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        // Mirror the system's own alerting: silent updates (alert-once) don't alert, so they don't glow.
        if (facts.isUpdate && notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) return
        if (facts.importance != null && facts.importance < NotificationManager.IMPORTANCE_DEFAULT) return
        if (!facts.passesDnd) return
        if (sbn.packageName in current.blockedPackages) return

        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive == true
        val spec = current.toGlowSpec(sbn.packageName) { NotificationColorExtractor.extract(this, sbn) }

        if (!interactive || LockScreenGlowActivity.isVisible) {
            // Screen off (with or without a keyguard): only the full-screen activity can wake it, and while
            // its AOD glow is up it takes re-deliveries too. Ringing events never take this path: their own
            // full-screen UI (call/alarm screen) is already launching, and our opaque activity would cover it.
            if (current.showWhenLocked && !isRinging) showLockScreenGlow(spec)
            return
        }
        // Screen on. The accessibility overlay draws above the lock screen too; the app overlay is hidden
        // by it, so without the accessibility service a visible lock screen gets no glow.
        val wanted = if (locked) current.showWhenLocked else current.showWhenUnlocked
        val overlay = GlowAccessibilityService.overlay ?: overlayController.takeIf { !locked }
        if (wanted && overlay != null) {
            overlay.show(spec)
            overlaySource = OverlaySource(sbn.key, isRinging, overlay)
        }
    }

    private fun showLockScreenGlow(spec: GlowSpec) {
        val intent = LockScreenGlowActivity.newIntent(this, spec)
        // Already glowing: a second full-screen intent would degrade to a heads-up banner over the glow.
        // Our visible window exempts us from background-launch limits, so re-deliver directly instead.
        if (LockScreenGlowActivity.isVisible) {
            startActivity(intent)
            return
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_glow_title))
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setTimeoutAfter(spec.durationMs)
            .build()
        // Updating a live notification doesn't re-alert, so its full-screen intent wouldn't fire again.
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(GLOW_NOTIFICATION_ID)
        notificationManager.notify(GLOW_NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "glow"
        private const val GLOW_NOTIFICATION_ID = 1
        const val TEST_CHANNEL_ID = "glow_test"
    }
}
