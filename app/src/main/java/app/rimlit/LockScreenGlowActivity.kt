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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.rimlit.model.GlowSpec
import app.rimlit.ui.glow.GlowHost
import app.rimlit.ui.glow.GlowRequest
import app.rimlit.ui.glow.drawIntoCutout

/**
 * AOD-style glow for when the screen is off or locked, launched via a full-screen-intent notification.
 *
 * Black background + glow. The activity closes as soon as the glow fades (or on any touch, or when the screen
 * turns off), handing back to the keyguard and its short timeout: holding the woken screen until the system
 * timeout made screen-on time the app's main battery cost.
 */
class LockScreenGlowActivity : ComponentActivity() {

    private var request by mutableStateOf<GlowRequest?>(null)

    /** Identifies this instance in [visibleInstance] (a static Activity reference would leak it). */
    private val instanceId = nextInstanceId++

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // showWhenLocked / turnScreenOn are declared in the manifest (minSdk 29 ≥ API 27).
        window.attributes = window.attributes.apply { drawIntoCutout() }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        show(intent)

        setContent {
            val current = request ?: return@setContent
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            finish()
                        }
                    },
            ) {
                GlowHost(
                    request = current,
                    dismissed = false,
                    onFinished = ::finish,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    /** singleInstance: a notification arriving while this is visible re-delivers here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        show(intent)
    }

    override fun onStart() {
        super.onStart()
        visibleInstance = instanceId
    }

    /** Screen turned off (or user left): the AOD moment is over. */
    override fun onStop() {
        super.onStop()
        finish()
    }

    /**
     * Clears [isVisible] at once, on every exit, so a notification racing the finish takes the normal routing.
     * Only if it is still ours: that notification may already have started a newer instance.
     */
    override fun finish() {
        if (visibleInstance == instanceId) visibleInstance = null
        super.finish()
    }

    private fun show(intent: Intent) {
        val spec = IntentCompat.getParcelableExtra(intent, EXTRA_SPEC, GlowSpec::class.java)
        if (spec == null) {
            finish()
            return
        }
        request = GlowRequest(spec)
    }

    companion object {
        private const val EXTRA_SPEC = "extra_spec"

        private var nextInstanceId = 0

        /** The instance currently started and not finishing, if any. Main thread only. */
        private var visibleInstance: Int? = null

        /** Read by the listener (same process, main thread) to re-deliver instead of re-posting the FSI. */
        val isVisible: Boolean
            get() = visibleInstance != null

        fun newIntent(context: Context, spec: GlowSpec): Intent =
            Intent(context, LockScreenGlowActivity::class.java)
                .putExtra(EXTRA_SPEC, spec)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
