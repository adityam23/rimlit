package app.rimlit.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import app.rimlit.model.GlowSpec
import app.rimlit.ui.glow.GlowHost
import app.rimlit.ui.glow.GlowRequest
import app.rimlit.ui.glow.drawIntoCutout

/**
 * Draws the glow in an overlay window while the screen is on. One window at most: a new [show]
 * while visible re-targets the existing window instead of re-creating it (no flicker).
 * Main thread only.
 *
 * [windowType] is `TYPE_APPLICATION_OVERLAY` ("Display over other apps"; hidden by the lock screen) by
 * default, or `TYPE_ACCESSIBILITY_OVERLAY` when [context] is a connected [GlowAccessibilityService].
 */
class EdgeOverlayController(
    private val context: Context,
    private val windowType: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var overlay: Overlay? = null

    fun show(spec: GlowSpec) {
        val current = overlay
        if (current != null) {
            current.request = GlowRequest(spec)
            current.dismissed = false
            return
        }
        val created = Overlay(GlowRequest(spec))
        runCatching { windowManager.addView(created.view, layoutParams()) }
            .onSuccess {
                created.owner.lifecycle.currentState = Lifecycle.State.RESUMED
                overlay = created
            }
            .onFailure { Log.w(TAG, "Failed to add overlay (permission revoked?)", it) }
    }

    /** Fades the glow out early, then removes the window. */
    fun dismiss() {
        overlay?.dismissed = true
    }

    /** Removes the window immediately, without fading. */
    fun remove() {
        val current = overlay ?: return
        overlay = null
        current.owner.lifecycle.currentState = Lifecycle.State.DESTROYED
        runCatching { windowManager.removeView(current.view) }
            .onFailure { Log.w(TAG, "Failed to remove overlay", it) }
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

    /** One attached window: its Compose state, view and lifecycle. */
    private inner class Overlay(initial: GlowRequest) {
        var request by mutableStateOf(initial)
        var dismissed by mutableStateOf(false)
        val owner = OverlayLifecycleOwner()
        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                GlowHost(
                    request = request,
                    dismissed = dismissed,
                    onFinished = ::remove,
                    modifier = Modifier.fillMaxSize(),
                )
            }
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
