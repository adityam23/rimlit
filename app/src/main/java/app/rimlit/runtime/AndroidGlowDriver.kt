package app.rimlit.runtime

import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import app.rimlit.LockScreenGlowActivity
import app.rimlit.engine.Cancellable
import app.rimlit.engine.Environment
import app.rimlit.engine.GlowCoordinator
import app.rimlit.engine.GlowDriver
import app.rimlit.engine.Surface
import app.rimlit.model.GlowSpec
import app.rimlit.notify.GlowNotifications
import app.rimlit.overlay.EdgeOverlayController
import app.rimlit.overlay.GlowAccessibilityService
import app.rimlit.permissions.PermissionKind
import app.rimlit.permissions.isGranted

/** The machine's platform side: system state, overlay windows, the lock-screen activity, main-thread timers. */
class AndroidGlowDriver(private val context: Context, private val coordinator: GlowCoordinator) : GlowDriver {

    private val handler = Handler(Looper.getMainLooper())
    private val appOverlay by lazy { EdgeOverlayController(context) }

    override fun environment() = Environment(
        screenOn = context.getSystemService(PowerManager::class.java)?.isInteractive == true,
        locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true,
        fullScreenIntentGranted = PermissionKind.FULL_SCREEN_INTENT.isGranted(context),
    )

    override fun open(surface: Surface, wakeScreen: Boolean, spec: GlowSpec) {
        when (surface) {
            Surface.APP_OVERLAY -> openOverlay(surface, appOverlay)

            Surface.ACCESSIBILITY_OVERLAY -> openOverlay(surface, GlowAccessibilityService.overlay)

            // The activity reports itself once started; one never delivered is given up by the open timeout.
            Surface.LOCK_SCREEN -> {
                val intent = LockScreenGlowActivity.newIntent(context)
                if (wakeScreen) {
                    GlowNotifications.postLockScreenWake(context, intent, spec.durationMs)
                } else {
                    context.startActivity(intent)
                }
            }
        }
    }

    private fun openOverlay(surface: Surface, overlay: EdgeOverlayController?) {
        val window = overlay?.open(coordinator.state, surface)
        if (window != null) coordinator.surfaceOpened(surface, window) else coordinator.surfaceGone(surface, null)
    }

    override fun close(surface: Surface) {
        if (surface == Surface.LOCK_SCREEN) GlowNotifications.cancelLockScreenWake(context)
    }

    override fun schedule(delayMs: Long, action: () -> Unit): Cancellable {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMs)
        return Cancellable { handler.removeCallbacks(runnable) }
    }

    /** Synchronous for now: the colours are delivered before this returns, so there is nothing to cancel. */
    override fun extract(extract: () -> List<Int>, deliver: (List<Int>) -> Unit): Cancellable {
        deliver(extract())
        return Cancellable {}
    }

    override fun fullScreenIntentDenied() {
        Log.w(TAG, "Full-screen intent access denied: skipping lock-screen glows until it is granted")
    }

    private companion object {
        const val TAG = "AndroidGlowDriver"
    }
}
