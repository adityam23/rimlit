package app.rimlit.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Hosts the glow in an accessibility overlay, the only window an app can draw above the lock screen.
 *
 * Deliberately inert: `res/xml/glow_accessibility_service.xml` subscribes to no accessibility events and
 * denies window-content access, so the service can't observe or act on anything on screen. It exists only
 * to own that window.
 */
class GlowAccessibilityService : AccessibilityService() {

    private val glowOverlay by lazy {
        EdgeOverlayController(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun disconnect() {
        if (connected === this) connected = null
        glowOverlay.remove()
    }

    companion object {
        /** Set only between connect and unbind/destroy, so it never outlives the service. Main thread only. */
        private var connected: GlowAccessibilityService? = null

        /** The accessibility overlay, or `null` while the user hasn't enabled the service. Main thread only. */
        val overlay: EdgeOverlayController?
            get() = connected?.glowOverlay
    }
}
