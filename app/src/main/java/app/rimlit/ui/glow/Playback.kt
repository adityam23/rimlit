package app.rimlit.ui.glow

import android.animation.ValueAnimator
import android.content.Context
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.rimlit.model.BatterySaver
import app.rimlit.model.GlowSpec

/** Frame rate cap while battery saver applies: the glow keeps its look, at fewer frames. */
const val BATTERY_SAVER_FPS = 30

/** Device conditions that change how a glow plays. */
data class DeviceConditions(
    /** Remove animations (or an animator duration scale of 0) is on. */
    val animationsOff: Boolean,
    /** The system's battery saver is on. */
    val powerSave: Boolean,
) {
    companion object {
        fun of(context: Context) = DeviceConditions(
            animationsOff = !ValueAnimator.areAnimatorsEnabled(),
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
)

/** How [this] plays under [device], on the lock-screen surface when [lockScreen]. */
fun GlowSpec.playback(lockScreen: Boolean, device: DeviceConditions) = Playback(
    brightness = if (lockScreen) screenOffBrightness else brightness,
    still = device.animationsOff,
    maxFps = BATTERY_SAVER_FPS.takeIf {
        batterySaver == BatterySaver.ALWAYS || (batterySaver == BatterySaver.WITH_SYSTEM && device.powerSave)
    },
)

/** The device conditions when this composition started: a glow lasts seconds, so it isn't followed live. */
@Composable
fun rememberDeviceConditions(): DeviceConditions {
    val context = LocalContext.current
    return remember(context) { DeviceConditions.of(context) }
}
