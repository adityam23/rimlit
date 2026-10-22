package app.rimlit.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import app.rimlit.engine.GlowCoordinator

/** Screen on/off and unlock: each asks the coordinator to re-sample the environment. */
class ScreenStateReceiver(private val coordinator: GlowCoordinator) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) = coordinator.environmentChanged()

    /** Registers for the life of the process: screen broadcasts reach only receivers registered at runtime. */
    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
