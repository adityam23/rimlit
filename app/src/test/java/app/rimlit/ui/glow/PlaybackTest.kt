package app.rimlit.ui.glow

import app.rimlit.model.BatterySaver
import app.rimlit.model.GlowPalette
import app.rimlit.model.GlowSettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackTest {

    private val normal = DeviceConditions(animationsOff = false, powerSave = false)
    private val palette = GlowPalette.Rainbow

    private fun spec(settings: GlowSettings = GlowSettings()) = settings.toGlowSpec(palette)

    @Test
    fun theLockScreenUsesTheScreenOffBrightness() {
        val own = spec(GlowSettings(brightness = 0.9f, screenOffBrightness = 0.3f))
        assertEquals(0.3f, own.playback(lockScreen = true, normal).brightness)
        assertEquals(0.9f, own.playback(lockScreen = false, normal).brightness)

        val same = spec(GlowSettings(brightness = 0.9f))
        assertEquals(0.9f, same.playback(lockScreen = true, normal).brightness, "unset follows the on-screen one")
    }

    @Test
    fun removeAnimationsHoldsTheGlowStill() {
        assertTrue(spec().playback(lockScreen = false, normal.copy(animationsOff = true)).still)
        assertFalse(spec().playback(lockScreen = false, normal).still)
    }

    @Test
    fun batterySaverCapsTheFrameRate() {
        val powerSave = normal.copy(powerSave = true)
        fun fps(saver: BatterySaver, device: DeviceConditions) =
            spec(GlowSettings(batterySaver = saver)).playback(lockScreen = false, device).maxFps

        assertEquals(BATTERY_SAVER_FPS, fps(BatterySaver.WITH_SYSTEM, powerSave))
        assertNull(fps(BatterySaver.WITH_SYSTEM, normal))
        assertEquals(BATTERY_SAVER_FPS, fps(BatterySaver.ALWAYS, normal))
        assertNull(fps(BatterySaver.OFF, powerSave))
    }
}
