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
 *
 * Each launch carries its own id (set by [runUnlocked]), so it only ever takes its own action from [pending] — a
 * second launch started before a first one's activity exists can't steal or overwrite it.
 */
class UnlockActivity : ComponentActivity() {

    /** The action this instance runs once unlocked; taken from [pending] by its own id, so nothing else runs it. */
    private var action: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always taken, recreated or not, so a launch that's never kept doesn't linger in pending.
        val taken = intent.getStringExtra(EXTRA_ID)?.let { pending.take(it) }
        // Recreated (the process or a configuration change), it has nothing to run: the action wasn't kept.
        action = taken.takeIf { savedInstanceState == null }
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
        private const val EXTRA_ID = "app.rimlit.UnlockActivity.ID"

        /** Actions awaiting their own launch, keyed by that launch's id; see [PendingUnlockActions]. */
        private val pending = PendingUnlockActions()

        /**
         * Asks the user to unlock, then runs [action]; never if they don't. Each call generates its own id, so a
         * second call before a first call's activity exists doesn't overwrite it — both run. Rimlit may start it
         * from the background: it holds Display over other apps, or its accessibility service is bound, and the
         * overlay asking for it is a visible window.
         */
        fun runUnlocked(context: Context, action: () -> Unit) {
            val id = UUID.randomUUID().toString()
            pending.add(id, action)
            context.startActivity(
                Intent(context, UnlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_ID, id)
                    // Its own identity per launch, as for the lock-screen glow: an intent equal to a task's root still
                    // waiting on an unlock would only bring that task forward.
                    .setIdentifier(id),
            )
        }
    }
}
