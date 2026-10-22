package app.rimlit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.rimlit.engine.Surface
import app.rimlit.engine.SurfaceInstance
import app.rimlit.ui.glow.GlowHost
import app.rimlit.ui.glow.drawIntoCutout

/**
 * AOD-style glow for when the screen is off or locked: the machine's lock-screen surface, launched via a
 * full-screen-intent notification. Black background plus whatever the machine shows on this surface.
 *
 * Any instance is interchangeable: each reports itself to the coordinator once started, which binds the latest and
 * closes one nobody asked for. The machine closes it as soon as the glow fades, handing back to the keyguard and its
 * short timeout (holding the woken screen until the system timeout made screen-on time the app's main battery cost);
 * a touch or `onStop` ends it too.
 */
class LockScreenGlowActivity :
    ComponentActivity(),
    SurfaceInstance {

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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            gone()
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

    override fun close() = finish()

    private fun gone() {
        glowCoordinator.surfaceGone(Surface.LOCK_SCREEN, this)
        finish()
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, LockScreenGlowActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
