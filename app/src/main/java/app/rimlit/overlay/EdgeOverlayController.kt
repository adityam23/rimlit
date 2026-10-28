package app.rimlit.overlay

import android.app.KeyguardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.rimlit.engine.GlowCoordinator
import app.rimlit.engine.Surface
import app.rimlit.engine.SurfaceInstance
import app.rimlit.glowCoordinator
import app.rimlit.ui.glow.GlowHost
import app.rimlit.ui.glow.drawIntoCutout

/**
 * Opens the overlay window [surface] draws in, while the screen is on. One window at most. The window draws
 * whatever the machine shows on its surface, so it has no timeline of its own. Main thread only.
 *
 * [Surface.APP_OVERLAY] is a `TYPE_APPLICATION_OVERLAY` window ("Display over other apps"; hidden by the lock
 * screen); [Surface.ACCESSIBILITY_OVERLAY] is a `TYPE_ACCESSIBILITY_OVERLAY` one, so [context] must then be a
 * connected [GlowAccessibilityService].
 */
class EdgeOverlayController(private val context: Context, private val surface: Surface) {

    private val windowType = when (surface) {
        Surface.APP_OVERLAY -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        Surface.ACCESSIBILITY_OVERLAY -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        Surface.LOCK_SCREEN -> throw IllegalArgumentException("The lock screen is an activity, not an overlay")
    }

    private val windowManager = context.getSystemService(WindowManager::class.java)

    /** Read when used: the app creates the coordinator after its driver, which owns the app overlay. */
    private val coordinator: GlowCoordinator get() = context.glowCoordinator

    /** The window currently attached, if any. */
    private var window: Overlay? = null

    /** Attaches a window drawing [surface]'s frame of the machine's state; `null` if it couldn't be added. */
    fun open(): SurfaceInstance? {
        window?.close()
        val created = Overlay()
        runCatching { windowManager.addView(created.view, layoutParams()) }.onFailure {
            Log.w(TAG, "Failed to add overlay (permission revoked?)", it)
            return null
        }
        created.owner.lifecycle.currentState = Lifecycle.State.RESUMED
        window = created
        return created
    }

    /** The host is going away: the attached window reports itself gone, which closes it. */
    fun release() {
        window?.let { coordinator.surfaceGone(surface, it) }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        windowType,
        // Never steal input: the glow is purely decorative.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        drawIntoCutout()
        // API 30+ insets overlay windows by the system bars by default (FLAG_LAYOUT_NO_LIMITS doesn't
        // override it); without this the glow stops above the navigation bar instead of the screen corner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
    }

    /** One attached window: its view and lifecycle. Closing it removes it at once, without fading. */
    private inner class Overlay : SurfaceInstance {
        val owner = OverlayLifecycleOwner()
        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { GlowHost(coordinator.state, surface, Modifier.fillMaxSize()) }
        }

        override fun close() {
            if (window !== this) return
            window = null
            owner.lifecycle.currentState = Lifecycle.State.DESTROYED
            runCatching { windowManager.removeView(view) }
                .onFailure { Log.w(TAG, "Failed to remove overlay", it) }
        }

        /**
         * Unreached today: overlays take no touches (`FLAG_NOT_TOUCHABLE`), so nothing asks one to open. Kept as the
         * groundwork for the pill's on-screen double tap. A window can't dismiss the keyguard (only an activity can),
         * so above a locked one nothing opens.
         */
        override fun runUnlocked(action: () -> Unit) {
            if (context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) {
                Log.w(TAG, "Can't unlock from an overlay; not opening")
            } else {
                action()
            }
            close()
        }
    }

    private companion object {
        const val TAG = "EdgeOverlayController"
    }
}

/**
 * Minimal lifecycle + saved-state owner so a ComposeView can live in a window outside any Activity.
 * Without both, Compose crashes with "ViewTreeLifecycleOwner not found".
 */
private class OverlayLifecycleOwner : SavedStateRegistryOwner {

    override val lifecycle = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    init {
        savedStateRegistryController.performRestore(null as Bundle?)
    }
}
