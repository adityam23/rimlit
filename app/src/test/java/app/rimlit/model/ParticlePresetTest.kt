package app.rimlit.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParticlePresetTest {

    @Test
    fun everyPresetIsValidAndHasItsOwnPictureAndMotion() {
        // Each spec checks its ranges as it is built, so loading the entries validates them.
        assertEquals(8, ParticlePreset.entries.size)
        assertEquals(ParticlePreset.entries.size, ParticlePreset.entries.distinctBy { it.spec.image }.size)
        assertEquals(
            ParticlePreset.entries.size,
            ParticlePreset.entries.distinctBy { it.spec.image to it.spec.motion }.size,
        )
    }

    @Test
    fun aTunedSpecStillReadsAsItsPreset() {
        for (preset in ParticlePreset.entries) {
            assertEquals(preset, ParticlePreset.of(preset.spec))
            val tuned = preset.spec.copy(sizeDp = 40f, speed = 3f, amount = 1f, rotationDegPerSecond = 360f)
            assertEquals(preset, ParticlePreset.of(tuned.copy(tint = ParticleTint.entries.first { it != tuned.tint })))
        }
    }

    @Test
    fun noParticlesOrAnotherMotionIsNoPreset() {
        assertNull(ParticlePreset.of(null))
        assertNull(ParticlePreset.of(ParticleSpec(ParticleImage.SNOWFLAKE, ParticleMotion.POP)))
    }
}
