package app.rimlit.overlay

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
import app.rimlit.engine.GlowState
import app.rimlit.engine.Surface
import app.rimlit.engine.SurfaceInstance
import app.rimlit.ui.glow.GlowHost
import app.rimlit.ui.glow.drawIntoCutout
import kotlinx.coroutines.flow.StateFlow

/**
 * Opens the overlay window a glow surface draws in, while the screen is on. One window at most. The window draws
 * whatever the machine shows on its surface, so it has no timeline of its own. Main thread only.
 *
 * [windowType] is `TYPE_APPLICATION_OVERLAY` ("Display over other apps"; hidden by the lock screen) by
 * default, or `TYPE_ACCESSIBILITY_OVERLAY` when [context] is a connected [GlowAccessibilityService].
 */
class EdgeOverlayController(
    private val context: Context,
    private val windowType: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
) {

    private val windowManager = context.getSystemService(WindowManager::class.java)

    /** The window currently attached, if any. */
    var window: SurfaceInstance? = null
        private set

    /** Attaches a window drawing [surface]'s frame of [state]; `null` if it couldn't be added (permission revoked?). */
    fun open(state: StateFlow<GlowState?>, surface: Surface): SurfaceInstance? {
        window?.close()
        val created = Overlay(state, surface)
        runCatching { windowManager.addView(created.view, layoutParams()) }.onFailure {
            Log.w(TAG, "Failed to add overlay (permission revoked?)", it)
            return null
        }
        created.owner.lifecycle.currentState = Lifecycle.State.RESUMED
        window = created
        return created
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
    private inner class Overlay(state: StateFlow<GlowState?>, surface: Surface) : SurfaceInstance {
        val owner = OverlayLifecycleOwner()
        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { GlowHost(state, surface, Modifier.fillMaxSize()) }
        }

        override fun close() {
            if (window !== this) return
            window = null
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
