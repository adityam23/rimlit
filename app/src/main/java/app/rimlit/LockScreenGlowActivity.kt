package app.rimlit

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.engine.Surface
import app.rimlit.engine.SurfaceInstance
import app.rimlit.engine.opensOnRequest
import app.rimlit.ui.glow.GlowHost
import app.rimlit.ui.glow.drawIntoCutout

/**
 * AOD-style glow for when the screen is off or locked: the machine's lock-screen surface, launched via a
 * full-screen-intent notification. Black background plus whatever the machine shows on this surface.
 *
 * Any instance is interchangeable: each reports itself to the coordinator once started, which binds the latest and
 * closes one nobody asked for. The machine closes it as soon as the glow fades, handing back to the keyguard and its
 * short timeout (holding the woken screen until the system timeout made screen-on time the app's main battery cost);
 * a touch or `onStop` ends it too. While the glow would open something, a double tap asks the machine to open it
 * instead, and a single tap waits out the double-tap timeout before ending the glow.
 */
class LockScreenGlowActivity :
    ComponentActivity(),
    SurfaceInstance {

    /** A keyguard dismissal is in flight: closing waits for its outcome, since the request needs this activity. */
    private var unlocking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // showWhenLocked / turnScreenOn are declared in the manifest (minSdk 29 ≥ API 27).
        window.attributes = window.attributes.apply { drawIntoCutout() }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        setContent {
            val state = glowCoordinator.state.collectAsStateWithLifecycle()
            val opens by remember { derivedStateOf { state.value?.opensOnRequest(Surface.LOCK_SCREEN) != null } }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(opens) {
                        if (opens) {
                            detectTapGestures(onDoubleTap = { openRequested() }, onTap = { gone() })
                        } else {
                            // Nothing to open: end at the first touch, without waiting for a second tap.
                            awaitEachGesture {
                                awaitFirstDown()
                                gone()
                            }
                        }
                    },
            ) {
                GlowHost(glowCoordinator.state, Surface.LOCK_SCREEN, Modifier.fillMaxSize())
            }
        }
    }

    override fun onStart() {
        super.onStart()
        glowCoordinator.surfaceOpened(Surface.LOCK_SCREEN, this)
    }

    /** Screen turned off (or user left): the AOD moment is over. */
    override fun onStop() {
        super.onStop()
        gone()
    }

    /**
     * Idempotent: a closed instance still reports itself gone from `onStop`, which closes it again. During a keyguard
     * dismissal it stays (black, since the machine shows nothing here any more) until the dismissal ends.
     */
    override fun close() {
        if (!unlocking && !isFinishing) finish()
    }

    /**
     * Dismisses the keyguard from this activity, which must still be showing, then runs [action] while this activity
     * is the visible window (its standing lets a notification's pending intent start an activity), and finishes:
     * the open request that led here also closed it.
     */
    override fun runUnlocked(action: () -> Unit) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked != true) {
            action()
            return
        }
        unlocking = true
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = unlocked(action)

                override fun onDismissCancelled() = unlocked(null)

                override fun onDismissError() {
                    Log.w(TAG, "Couldn't dismiss the keyguard; not opening")
                    unlocked(null)
                }
            },
        )
    }

    /** The keyguard dismissal ended: runs [action] if it succeeded, then finishes the deferred close. */
    private fun unlocked(action: (() -> Unit)?) {
        unlocking = false
        action?.invoke()
        close()
    }

    /** Reported gone, which also closes (finishes) it. */
    private fun gone() = glowCoordinator.surfaceGone(Surface.LOCK_SCREEN, this)

    /** Asks the machine to open the glow's notification; it ends the glow either way. */
    private fun openRequested() = glowCoordinator.openRequested(Surface.LOCK_SCREEN, this)

    companion object {
        private const val TAG = "LockScreenGlowActivity"

        fun newIntent(context: Context): Intent = Intent(context, LockScreenGlowActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
