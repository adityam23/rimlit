package app.rimlit.overlay

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.rimlit.UnlockActivity
import app.rimlit.engine.GlowCoordinator
import app.rimlit.engine.Surface
import app.rimlit.engine.SurfaceInstance
import app.rimlit.glowCoordinator
import app.rimlit.ui.glow.BriefPillHost
import app.rimlit.ui.glow.CutoutSide
import app.rimlit.ui.glow.GlowHost
import app.rimlit.ui.glow.PillMetrics
import app.rimlit.ui.glow.PillPlacement
import app.rimlit.ui.glow.displayCutout
import app.rimlit.ui.glow.drawIntoCutout
import app.rimlit.ui.glow.pillPlacement

/**
 * Opens the overlay windows [surface] draws in, while the screen is on: the full-screen edge window, which never takes
 * a touch, and the small pill window above it, sized to the pill, which takes the pill's gestures. Both open and close
 * together as one surface instance, at most one at a time. They draw whatever the machine shows on their surface, so
 * they have no timeline of their own. Main thread only.
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

    /** The windows currently attached, if any. */
    private var window: Overlay? = null

    /**
     * Attaches the windows drawing [surface]'s frame of the machine's state; `null` if the edge window couldn't be
     * added. A pill window that can't be added only leaves the glow without its pill.
     */
    fun open(): SurfaceInstance? {
        window?.close()
        val created = Overlay()
        if (!created.edge.add(edgeParams())) return null
        window = created
        created.addPill()
        return created
    }

    /** The host is going away: the attached windows report themselves gone, which closes them. */
    fun release() {
        window?.let { coordinator.surfaceGone(surface, it) }
    }

    /** The edge: the whole screen, never stealing input, since it is purely decorative. */
    private fun edgeParams() = layoutParams(
        size = WindowManager.LayoutParams.MATCH_PARENT,
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
    )

    /**
     * The pill: as large as its content, at [placement] (the top-left corner until it is known). It takes touches on
     * itself only; any outside it go to the windows below, since it can't take focus. Its x is from the physical left
     * edge, where the cutout is measured from, in either layout direction.
     */
    @SuppressLint("RtlHardcoded")
    private fun pillParams(placement: PillPlacement?) = layoutParams(
        size = WindowManager.LayoutParams.WRAP_CONTENT,
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
    ).apply {
        val centred = placement != null && placement.x == null
        gravity = Gravity.TOP or if (centred) Gravity.CENTER_HORIZONTAL else Gravity.LEFT
        x = placement?.x ?: 0
        y = placement?.y ?: 0
    }

    private fun layoutParams(size: Int, flags: Int) = WindowManager.LayoutParams(
        size,
        size,
        windowType,
        flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        drawIntoCutout()
        // API 30+ insets overlay windows by the system bars by default (FLAG_LAYOUT_NO_LIMITS doesn't
        // override it); without this the glow stops above the navigation bar instead of the screen corner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
    }

    /**
     * One attached edge window and its pill window. Closing it removes both at once, without fading. The pill's
     * gestures report through it, so a stale pill's request only closes it.
     */
    private inner class Overlay : SurfaceInstance {
        private val metrics = PillMetrics.at(context.resources.displayMetrics.density)

        /** Where the pill sits; `null` until the edge window has laid out and so knows the cutout. */
        private var placement by mutableStateOf<PillPlacement?>(null)

        val edge = OverlayWindow { GlowHost(coordinator.state, surface, Modifier.fillMaxSize()) }

        private val pill = OverlayWindow {
            BriefPillHost(
                state = coordinator.state,
                surface = surface,
                placement = placement,
                metrics = metrics,
                onOpen = { coordinator.openRequested(surface, this@Overlay) },
                onDismiss = { coordinator.dismissRequested(surface, this@Overlay) },
            )
        }

        /** Adds the pill window, placed from the edge window's cutout each time that lays out (e.g. on rotation). */
        fun addPill() {
            if (!pill.add(pillParams(placement))) return
            edge.view.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> place(view) }
        }

        /**
         * Moves the pill to where it belongs in [edgeView]'s window, if that changed: around the camera's real outline
         * when it is on the top edge (some displays report a bounding rect from the edge down to the hole).
         */
        private fun place(edgeView: View) {
            val cutout = edgeView.displayCutout()?.takeIf { it.side == CutoutSide.TOP }?.bounds
                ?.let { Rect(it.left, it.top, it.right, it.bottom) }
            val next = pillPlacement(edgeView.width, cutout, metrics)
            if (next == placement || window !== this) return
            placement = next
            runCatching { windowManager.updateViewLayout(pill.view, pillParams(next)) }
                .onFailure { Log.w(TAG, "Failed to move the pill", it) }
        }

        override fun close() {
            if (window !== this) return
            window = null
            pill.remove()
            edge.remove()
        }

        /**
         * Reached by the pill's double tap. Unlocked, [action] runs at once, while these windows are still visible.
         * Above a locked keyguard (only the accessibility overlay draws there), [UnlockActivity] asks for the unlock
         * and runs it, since a window can't dismiss the keyguard; it is started first, while these windows are up.
         */
        override fun runUnlocked(action: () -> Unit) {
            if (context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) {
                UnlockActivity.runUnlocked(context, action)
            } else {
                action()
            }
            close()
        }
    }

    /** One window's Compose view and lifecycle. */
    private inner class OverlayWindow(content: @Composable () -> Unit) {
        private val owner = OverlayLifecycleOwner()
        private var added = false

        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent(content)
        }

        /** Adds the window; whether it could be added. */
        fun add(params: WindowManager.LayoutParams): Boolean {
            runCatching { windowManager.addView(view, params) }.onFailure {
                Log.w(TAG, "Failed to add overlay (permission revoked?)", it)
                return false
            }
            added = true
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            return true
        }

        fun remove() {
            if (!added) return
            added = false
            owner.lifecycle.currentState = Lifecycle.State.DESTROYED
            runCatching { windowManager.removeView(view) }
                .onFailure { Log.w(TAG, "Failed to remove overlay", it) }
        }
    }

    private companion object {
        const val TAG = "EdgeOverlayController"
    }
}

/**
 * Minimal lifecycle + saved-state owner so a ComposeView can live in a window outside any Activity.
 * Without both, Compose crashes with "ViewTreeLifecycleOwner not found". One per window: two Compose views can't share
 * a saved-state registry.
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
