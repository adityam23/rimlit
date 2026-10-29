package app.rimlit.ui.glow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.rimlit.model.BatterySaver
import app.rimlit.model.GlowSpec

/** Frame rate cap while battery saver applies: the glow keeps its look, at fewer frames. */
const val BATTERY_SAVER_FPS = 30

/** Share of a glow's particles drawn while battery saver applies. */
const val BATTERY_SAVER_PARTICLE_SHARE = 0.5f

/** Device conditions that change how a glow plays. */
data class DeviceConditions(
    /** Remove animations (an animator duration scale of 0) is on. */
    val animationsOff: Boolean,
    /** The system's battery saver is on. */
    val powerSave: Boolean,
) {
    companion object {
        // The setting itself rather than ValueAnimator.areAnimatorsEnabled(): when its change is observed, the
        // process's own copy of the scale may not have caught up yet.
        fun of(context: Context) = DeviceConditions(
            animationsOff = Settings.Global.getFloat(context.contentResolver, ANIMATOR_SCALE, 1f) == 0f,
            powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true,
        )
    }
}

/** How a glow plays on one surface; the renderer reads this instead of the device or the settings. */
data class Playback(
    /** Peak opacity in [0, 1]. */
    val brightness: Float,
    /** Holds still: no pulse, flow or moving blade. */
    val still: Boolean,
    /** At most this many frames per second; `null` for the display's own rate. */
    val maxFps: Int?,
    /** Share of the spec's particle amount drawn; 0 for none. */
    val particleShare: Float,
)

/** How [this] plays under [device], on the lock-screen surface when [lockScreen]. */
fun GlowSpec.playback(lockScreen: Boolean, device: DeviceConditions): Playback {
    val saving = batterySaver == BatterySaver.ALWAYS || (batterySaver == BatterySaver.WITH_SYSTEM && device.powerSave)
    return Playback(
        brightness = if (lockScreen) screenOffBrightness else brightness,
        still = device.animationsOff,
        maxFps = BATTERY_SAVER_FPS.takeIf { saving },
        // Particles are nothing but motion, so a still glow draws none.
        particleShare = when {
            device.animationsOff -> 0f
            saving -> BATTERY_SAVER_PARTICLE_SHARE
            else -> 1f
        },
    )
}

/**
 * The device conditions, followed live: the Style screen stays open while the user flips battery saver or Remove
 * animations from Quick Settings.
 */
@Composable
fun rememberDeviceConditions(): DeviceConditions {
    val context = LocalContext.current
    var conditions by remember(context) { mutableStateOf(DeviceConditions.of(context)) }
    DisposableEffect(context) {
        val refresh = { conditions = DeviceConditions.of(context) }
        val powerSave = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = refresh()
        }
        val animatorScale = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        ContextCompat.registerReceiver(
            context,
            powerSave,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(ANIMATOR_SCALE), false, animatorScale)
        onDispose {
            context.unregisterReceiver(powerSave)
            context.contentResolver.unregisterContentObserver(animatorScale)
        }
    }
    return conditions
}

private const val ANIMATOR_SCALE = Settings.Global.ANIMATOR_DURATION_SCALE
