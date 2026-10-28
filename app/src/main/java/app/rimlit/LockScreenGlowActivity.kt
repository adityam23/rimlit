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
 * instead, and a single tap waits out the double-tap timeout before ending the glow. Opening hands this instance
 * over: it is no longer the surface, never reports itself again, and finishes itself once the unlock ends.
 */
class LockScreenGlowActivity :
    ComponentActivity(),
    SurfaceInstance {

    /** Handed over to open a notification ([runUnlocked]): no longer the machine's surface. */
    private var handedOver = false

    /** A keyguard dismissal is in flight: stopping doesn't finish it, since the request needs this activity. */
    private var dismissing = false

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
                            // A press that isn't a tap (a cancelled or dragged-away touch) still ends the glow.
                            detectTapGestures(
                                onPress = { if (!tryAwaitRelease()) gone() },
                                onDoubleTap = { openRequested() },
                                onTap = { gone() },
                            )
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

    /** A handed-over instance stays out of the coordinator's way: a new glow must not adopt it. */
    override fun onStart() {
        super.onStart()
        if (!handedOver) glowCoordinator.surfaceOpened(Surface.LOCK_SCREEN, this)
    }

    /**
     * Screen turned off (or user left): the AOD moment is over. Not during a keyguard dismissal, which needs this
     * activity and finishes it when it ends, however it ends.
     */
    override fun onStop() {
        super.onStop()
        if (!dismissing) gone()
    }

    /** Idempotent: a closed instance still reports itself gone from `onStop`, which closes it again. */
    override fun close() {
        if (!isFinishing) finish()
    }

    /**
     * Dismisses the keyguard from this activity, which must still be showing, then runs [action] while this activity
     * is the visible window (its standing lets a notification's pending intent start an activity), and finishes,
     * whether the dismissal succeeded, was cancelled or failed. Meanwhile it stays, black, since the machine shows
     * nothing here any more; a touch still finishes it.
     */
    override fun runUnlocked(action: () -> Unit) {
        handedOver = true
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked != true) {
            dismissed(action)
            return
        }
        dismissing = true
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = dismissed(action)

                override fun onDismissCancelled() = dismissed(null)

                override fun onDismissError() {
                    Log.w(TAG, "Couldn't dismiss the keyguard; not opening")
                    dismissed(null)
                }
            },
        )
    }

    /** The keyguard is out of the way, or stays: runs [action] if it unlocked, then finishes. */
    private fun dismissed(action: (() -> Unit)?) {
        dismissing = false
        action?.invoke()
        close()
    }

    /** Reported gone, which also closes (finishes) it; a handed-over instance is only closed. */
    private fun gone() = glowCoordinator.surfaceGone(Surface.LOCK_SCREEN, this)

    /** Asks the machine to open the glow's notification; it ends the glow either way. */
    private fun openRequested() = glowCoordinator.openRequested(Surface.LOCK_SCREEN, this)

    companion object {
        private const val TAG = "LockScreenGlowActivity"

        fun newIntent(context: Context): Intent = Intent(context, LockScreenGlowActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
