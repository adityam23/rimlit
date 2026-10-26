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
import app.rimlit.model.GlowSpec
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

/**
 * The machine's platform side: system state, overlay windows, the lock-screen activity, main-thread timers and
 * background palette extraction.
 */
class AndroidGlowDriver(
    private val context: Context,
    private val coordinator: GlowCoordinator,
    /** The process's main-thread scope: timers and extraction deliveries run in it. */
    private val scope: CoroutineScope,
    extractionDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GlowDriver {

    /** One extraction at a time: a superseded one that hasn't started never runs. */
    private val extractionLane = extractionDispatcher.limitedParallelism(1)

    private val appOverlay by lazy { EdgeOverlayController(context) }

    override fun environment() = Environment(
        screenOn = context.getSystemService(PowerManager::class.java)?.isInteractive == true,
        locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true,
        canWakeScreen = WAKE_ACCESS.all { it.isGranted(context) },
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
        val window = overlay?.open(coordinator, surface)
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
     * Off the main thread, on one lane: loading a URI or resource icon blocks on I/O. Reads the notification as it is
     * now, through the connected listener. The colours come back on the main thread; a result that lands after a
     * cancel anyway is dropped by the coordinator.
     */
    override fun extract(post: Post, deliver: (List<Int>) -> Unit): Cancellable {
        val listener = GlowListenerService.connected
        val job = scope.launch {
            val colors = withContext(extractionLane) {
                NotificationColorExtractor.extract(context, listener?.notification(post.key), post.packageName)
            }
            deliver(colors)
        }
        return Cancellable { job.cancel() }
    }

    override fun wakeScreenDenied() {
        val missing = WAKE_ACCESS.filterNot { it.isGranted(context) }
        Log.w(TAG, "Can't wake the screen (missing $missing): skipping lock-screen glows until granted")
    }

    private companion object {
        const val TAG = "AndroidGlowDriver"
        val NOTHING_TO_WITHDRAW = Cancellable {}

        /** What the full-screen-intent trampoline needs to wake the screen. */
        val WAKE_ACCESS = listOf(PermissionKind.FULL_SCREEN_INTENT, PermissionKind.NOTIFICATIONS)
    }
}
