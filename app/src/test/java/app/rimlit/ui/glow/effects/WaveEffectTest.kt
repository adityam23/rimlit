package app.rimlit.ui.glow.effects

import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WaveEffectTest {

    private val length = 5_900f
    private val amplitude = 22f

    @Test
    fun theWaveJoinsUpAcrossThePathStart() {
        val waves = waveCount(length, wavelength = 350f)
        for (phase in listOf(0f, 0.13f, 0.5f, 2.71f, 1_000.4f)) {
            val start = waveOffset(0f, length, waves, phase, amplitude)
            val end = waveOffset(length, length, waves, phase, amplitude)
            assertEquals(start, end, 1e-2f, "phase $phase")
            // Just either side of the start, too: no step in the line.
            val before = waveOffset(length - 0.5f, length, waves, phase, amplitude)
            val after = waveOffset(0.5f, length, waves, phase, amplitude)
            assertTrue(abs(before - after) < amplitude * 0.1f, "phase $phase")
        }
    }

    @Test
    fun theWaveStaysOnTheOpenSideWithinTwoAmplitudes() {
        val waves = waveCount(length, wavelength = 350f)
        for (step in 0..1_000) {
            val offset = waveOffset(length * step / 1_000, length, waves, phase = 0.3f, amplitude)
            assertTrue(offset in 0f..2 * amplitude + 1e-3f, "offset $offset")
        }
    }

    @Test
    fun anOutlineHoldsAWholeNumberOfWavesAtLeastTheMinimum() {
        assertEquals(17, waveCount(length, wavelength = 350f))
        assertEquals(4, waveCount(300f, wavelength = 350f), "a small ring still ripples")
        assertEquals(300, waveCount(300f, wavelength = 0f), "a zero wavelength counts as 1 px, not a division by zero")
    }
}
