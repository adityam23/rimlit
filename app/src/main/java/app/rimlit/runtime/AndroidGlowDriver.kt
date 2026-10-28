package app.rimlit.runtime

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import app.rimlit.GlowListenerService
import app.rimlit.LockScreenGlowActivity
import app.rimlit.engine.Cancellable
import app.rimlit.engine.Environment
import app.rimlit.engine.GlowCoordinator
import app.rimlit.engine.GlowDriver
import app.rimlit.engine.Post
import app.rimlit.engine.Surface
import app.rimlit.engine.SurfaceInstance
import app.rimlit.model.AutoColors
import app.rimlit.model.GlowSpec
import app.rimlit.notify.AbandoningLane
import app.rimlit.notify.GlowNotifications
import app.rimlit.notify.NotificationColorExtractor
import app.rimlit.overlay.EdgeOverlayController
import app.rimlit.overlay.GlowAccessibilityService
import app.rimlit.permissions.PermissionKind
import app.rimlit.permissions.isGranted
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.temporal.ChronoField

/**
 * The machine's platform side: system state, overlay windows, the lock-screen activity, main-thread timers and
 * background palette extraction, and opening a glow's notification.
 */
class AndroidGlowDriver(
    private val context: Context,
    private val coordinator: GlowCoordinator,
    /** The process's main-thread scope: timers and extraction deliveries run in it. */
    private val scope: CoroutineScope,
    private val extractionDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GlowDriver {

    /** One notification image loads at a time: a superseded one that hasn't started never runs. */
    private val imageLane = AbandoningLane(extractionDispatcher, NotificationColorExtractor.IMAGE_LOAD_TIMEOUT_MS)

    private val appOverlay by lazy { EdgeOverlayController(context, Surface.APP_OVERLAY) }

    override fun environment() = Environment(
        screenOn = context.getSystemService(PowerManager::class.java)?.isInteractive == true,
        locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true,
        canWakeScreen = WAKE_ACCESS.all { it.isGranted(context) },
        minuteOfDay = LocalTime.now().get(ChronoField.MINUTE_OF_DAY),
    )

    override fun open(surface: Surface, wakeScreen: Boolean, spec: GlowSpec): Cancellable = when (surface) {
        Surface.APP_OVERLAY -> openOverlay(surface, appOverlay)

        Surface.ACCESSIBILITY_OVERLAY -> openOverlay(surface, GlowAccessibilityService.overlay)

        // The activity reports itself once started; one never delivered is given up by the open timeout.
        Surface.LOCK_SCREEN -> {
            val intent = LockScreenGlowActivity.newIntent(context)
            if (wakeScreen) {
                GlowNotifications.postLockScreenWake(context, intent, spec.durationMs)
                Cancellable { GlowNotifications.cancelLockScreenWake(context) }
            } else {
                context.startActivity(intent)
                NOTHING_TO_WITHDRAW
            }
        }
    }

    /** An overlay opens at once: its window is the whole request. */
    private fun openOverlay(surface: Surface, overlay: EdgeOverlayController?): Cancellable {
        val window = overlay?.open()
        if (window != null) coordinator.surfaceOpened(surface, window) else coordinator.surfaceGone(surface, null)
        return NOTHING_TO_WITHDRAW
    }

    override fun schedule(delayMs: Long, action: () -> Unit): Cancellable {
        val job = scope.launch {
            delay(delayMs)
            action()
        }
        return Cancellable { job.cancel() }
    }

    /**
     * Off the main thread, notification images on one lane: loading a URI or resource icon blocks on I/O. Only the
     * notification-image source reads the notification, as it is now, through the connected listener; the app-icon
     * source needs neither. The colours come back on the main thread; a result that lands after a cancel anyway is
     * dropped by the coordinator.
     */
    override fun extract(post: Post, autoColors: AutoColors, deliver: (List<Int>) -> Unit): Cancellable {
        val listener = GlowListenerService.connected
        val job = scope.launch {
            val colors = withContext(extractionDispatcher) {
                NotificationColorExtractor.extract(context, post.packageName, autoColors, imageLane) {
                    listener?.notification(post.key)
                }
            }
            deliver(colors)
        }
        return Cancellable { job.cancel() }
    }

    override fun wakeScreenDenied() {
        val missing = WAKE_ACCESS.filterNot { it.isGranted(context) }
        Log.w(TAG, "Can't wake the screen (missing $missing): skipping lock-screen glows until granted")
    }

    /**
     * Reads the notification now, on the main thread (one binder call for a user's gesture), so one removed since
     * doesn't ask the user to unlock; then reads it again once unlocked, since the bouncer can stay up for minutes and
     * one removed meanwhile must not open.
     */
    override fun openNotification(post: Post, from: SurfaceInstance) {
        if (opener(post) == null) {
            Log.i(TAG, "Nothing to open for ${post.key}: gone, or it leads nowhere")
            from.close()
            return
        }
        from.runUnlocked {
            opener(post)?.invoke() ?: Log.i(TAG, "Nothing to open for ${post.key}: removed while unlocking")
        }
    }

    /** What opens [post]'s notification as it is posted now; `null` if it is gone or leads nowhere. */
    private fun opener(post: Post): (() -> Unit)? = GlowListenerService.connected?.notification(post.key)
        ?.let { NotificationOpener.opener(context, post.packageName, it) }

    private companion object {
        const val TAG = "AndroidGlowDriver"
        val NOTHING_TO_WITHDRAW = Cancellable {}

        /** What the full-screen-intent trampoline needs to wake the screen. */
        val WAKE_ACCESS = listOf(PermissionKind.FULL_SCREEN_INTENT, PermissionKind.NOTIFICATIONS)
    }
}
