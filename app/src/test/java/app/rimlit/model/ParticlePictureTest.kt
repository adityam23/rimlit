package app.rimlit.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ParticlePictureTest {

    private val ids = List(MAX_PARTICLE_IMAGES + 2) { newParticleImageId() }

    private fun custom(ids: List<String>) = ParticleSpec(ParticlePicture.Custom(ids), ParticleMotion.FALL)

    @Test
    fun onlyNewIdsAreImageIds() {
        assertTrue(ids.all(::isValidParticleImageId))
        val notIds = listOf("", "..", "../${ids[0]}", "${ids[0]}.png", ids[0].uppercase(), "${ids[0]}/", " ${ids[0]}")
        for (id in notIds) assertFalse(isValidParticleImageId(id), id)
    }

    @Test
    fun customPicturesHoldOneToFiveDistinctValidIds() {
        assertFailsWith<IllegalArgumentException> { ParticlePicture.Custom(emptyList()) }
        assertFailsWith<IllegalArgumentException> { ParticlePicture.Custom(ids.take(MAX_PARTICLE_IMAGES + 1)) }
        assertFailsWith<IllegalArgumentException> { ParticlePicture.Custom(listOf(ids[0], ids[0])) }
        assertFailsWith<IllegalArgumentException> { ParticlePicture.Custom(listOf("../settings")) }
    }

    @Test
    fun addedImagesAppendUpToTheLimit() {
        val tuned = custom(ids.take(3)).copy(motion = ParticleMotion.POP, sizeDp = 30f, tint = ParticleTint.PALETTE)
        val grown = tuned.withImages(ids.drop(2))
        val capped = ParticlePicture.Custom(ids.take(MAX_PARTICLE_IMAGES))
        assertEquals(tuned.copy(picture = capped), grown, "repeats dropped, capped, tuning and tint kept")
        assertSame(tuned, tuned.withImages(emptyList()))
    }

    @Test
    fun addingImagesToAnotherPictureSwitchesToThemInTheirOwnColours() {
        val snow = ParticlePreset.SNOW.spec.copy(tint = ParticleTint.PALETTE)
        assertEquals(
            snow.copy(picture = ParticlePicture.Custom(ids.take(2)), tint = ParticleTint.IMAGE),
            snow.withImages(ids.take(2)),
        )
        assertEquals(
            ParticleSpec(ParticlePicture.Custom(ids.take(1)), ParticleMotion.FALL, tint = ParticleTint.IMAGE),
            null.withImages(ids.take(1)),
        )
        assertNull(null.withImages(emptyList()))
    }

    @Test
    fun removingAnImageKeepsAtLeastOne() {
        val two = ParticleSpec(ParticlePicture.Custom(ids.take(2)), ParticleMotion.FALL)
        val one = two.withoutImage(ids[0])
        assertEquals(ParticlePicture.Custom(listOf(ids[1])), one.picture)
        assertSame(one, one.withoutImage(ids[1]), "the last image stays")
        assertSame(ParticlePreset.SNOW.spec, ParticlePreset.SNOW.spec.withoutImage(ids[0]))
    }

    @Test
    fun everyLooksImagesAreReferenced() {
        val settings = GlowSettings(
            looks = listOf(
                Look(particles = custom(ids.take(2))),
                Look(id = "b", name = "B", particles = custom(ids.drop(1).take(2))),
                Look(id = "c", name = "C", particles = ParticlePreset.SNOW.spec),
                Look(id = "d", name = "D"),
            ),
        )
        assertEquals(ids.take(3).toSet(), settings.particleImageIds)
        assertEquals(emptySet(), GlowSettings().particleImageIds)
    }

    @Test
    fun theAppIconNeverTakesThePalettesTint() {
        val icon = ParticleSpec(ParticlePicture.AppIcon, ParticleMotion.FALL, tint = ParticleTint.PALETTE)
        assertEquals(ParticleTint.IMAGE, icon.drawnTint)
        val own = custom(ids.take(1)).copy(tint = ParticleTint.PALETTE)
        assertEquals(ParticleTint.PALETTE, own.drawnTint, "your own images may take the palette's")
    }
}
