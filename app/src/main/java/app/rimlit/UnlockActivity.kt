package app.rimlit

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import java.util.UUID

/**
 * A transparent activity with no UI that unlocks the device for an overlay, since only an activity can dismiss the
 * keyguard: it asks for the dismissal (the user unlocks on the bouncer), runs the pending action once unlocked, while
 * it is still the visible window (its standing lets a notification's pending intent start an activity), and finishes
 * however the dismissal ends. Started by [runUnlocked]; main thread only.
 */
class UnlockActivity : ComponentActivity() {

    /** The action this instance runs once unlocked; taken from [pending] when it starts, so nothing else runs it. */
    private var action: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreated (the process or a configuration change), it has nothing to run: the action wasn't kept.
        action = pending.takeIf { savedInstanceState == null }
        pending = null
        if (action == null) {
            finish()
            return
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked != true) {
            done(unlocked = true)
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = done(unlocked = true)

                override fun onDismissCancelled() = done(unlocked = false)

                override fun onDismissError() {
                    Log.w(TAG, "Couldn't dismiss the keyguard; not opening")
                    done(unlocked = false)
                }
            },
        )
    }

    /** Runs the action if the device [unlocked], once, then finishes. */
    private fun done(unlocked: Boolean) {
        val run = action
        action = null
        if (unlocked) run?.invoke()
        finish()
    }

    override fun onDestroy() {
        // Never run late: an instance gone before its dismissal ended drops its action.
        action = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "UnlockActivity"

        /** The action the next instance takes; at most one, replaced by a newer request. Main thread only. */
        private var pending: (() -> Unit)? = null

        /**
         * Asks the user to unlock, then runs [action]; never if they don't. A newer request replaces one whose activity
         * hasn't started yet. Rimlit may start it from the background: it holds Display over other apps, or its
         * accessibility service is bound, and the overlay asking for it is a visible window.
         */
        fun runUnlocked(context: Context, action: () -> Unit) {
            pending = action
            context.startActivity(
                Intent(context, UnlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // Its own identity per launch, as for the lock-screen glow: an intent equal to a task's root still
                    // waiting on an unlock would only bring that task forward.
                    .setIdentifier(UUID.randomUUID().toString()),
            )
        }
    }
}
