package app.rimlit.data

import android.net.Uri
import app.rimlit.model.GlowSettings
import app.rimlit.model.Look
import app.rimlit.model.LookSave
import app.rimlit.model.MAX_LOOKS
import app.rimlit.model.ParticleMotion
import app.rimlit.model.ParticlePicture
import app.rimlit.model.ParticleSpec
import app.rimlit.model.UseFor
import app.rimlit.model.newParticleImageId
import app.rimlit.model.particleImageIds
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * An import adds what fits, and deletes at once every image it imported that no added look draws; a save says what it
 * came to, and writes nothing when refused.
 */
class LookLibraryTest {

    /** Image files in memory: bytes [BAD] don't decode; nothing is past its grace, so a clean-up deletes nothing. */
    private class FakeImages : ParticleImageFiles {
        val stored = mutableSetOf<String>()

        override suspend fun import(uri: Uri): String? = error("Not used")

        override suspend fun import(bytes: ByteArray): String? =
            if (bytes.contentEquals(BAD)) null else newParticleImageId().also { stored += it }

        override suspend fun has(id: String) = id in stored

        override suspend fun delete(ids: Collection<String>) {
            stored -= ids.toSet()
        }

        override suspend fun retainOnly(keep: Set<String>) = Unit
    }

    private val store = FakeStore()
    private val repository = SettingsRepository(store)
    private val images = FakeImages()
    private val library = LookLibrary(repository, images)

    /** An image another look draws, or a draft's still in its grace: never this import's to delete. */
    private val earlier = newParticleImageId().also { images.stored += it }

    private val imageA = newParticleImageId()
    private val imageB = newParticleImageId()

    private fun drawing(id: String, name: String, vararg images: String) =
        Look(id = id, name = name, particles = ParticleSpec(ParticlePicture.Custom(images.toList()), ParticleMotion.FALL))

    private val looks = listOf(drawing("a", "A", imageA), drawing("b", "B", imageA, imageB))

    /** A look file of [looks], whose second image's bytes are [imageB]. */
    private fun archive(imageB: ByteArray = byteArrayOf(2)) =
        LookArchive(looks, mapOf(imageA to byteArrayOf(1), this.imageB to imageB))

    @Test
    fun anImageThatDoesNotDecodeDeletesTheOnesThisImportAdded() = runBlocking {
        val failed = assertFailsWith<LookArchiveException> { library.add(archive(imageB = BAD), looks) }
        assertEquals(LookArchiveException.Problem.INVALID, failed.problem)
        assertEquals(setOf(earlier), images.stored, "the image imported before it is gone, the earlier one kept")
        assertEquals(GlowSettings(), repository.stored(), "no look added")
    }

    @Test
    fun aFailedWriteDeletesEveryImageThisImportAdded() = runBlocking {
        store.failing = true
        assertFailsWith<IOException> { library.add(archive(), looks) }
        assertEquals(setOf(earlier), images.stored)
        store.failing = false
        assertEquals(GlowSettings(), repository.stored(), "no look added")
    }

    @Test
    fun theImagesOfLooksThatDoNotFitAreDeletedAndTheAddedOnesKept() = runBlocking {
        val full = GlowSettings().let { it.copy(looks = it.looks + List(MAX_LOOKS - 2) { n -> Look(id = "l$n", name = "L$n") }) }
        repository.update { full }
        assertEquals(1, library.add(archive(), looks), "only room for one")
        val added = repository.stored().looks.last()
        assertEquals("A", added.name)
        assertEquals(1, added.particles.particleImageIds.size)
        assertEquals(added.particles.particleImageIds + earlier, images.stored, "B's own image is gone, A's kept")
        assertEquals(0, library.room())
        assertEquals(0, library.add(archive(), looks), "the library is full")
        assertEquals(added.particles.particleImageIds + earlier, images.stored, "an import that adds none keeps none")
    }

    @Test
    fun aSaveSaysWhyItWasRefusedAndWritesNothingThen() = runBlocking {
        val gone = library.saveLook(drawing("x", "X", earlier), UseFor(isDefault = false, apps = setOf("a")), false)
        assertEquals(LookSave.Refused.LOOK_DELETED, gone)
        assertEquals(GlowSettings(), repository.stored(), "nothing written")
        val saved = library.saveLook(drawing("x", "X", earlier, imageA), UseFor(false, setOf("a")), isNew = true)
        assertIs<LookSave.Saved>(saved)
        val look = repository.stored().look("x")
        assertEquals(setOf(earlier), look?.particles.particleImageIds, "an image that is gone is left out")
        assertEquals(LookSave.Refused.NAME_TAKEN, library.saveLook(Look(id = "y", name = "x"), UseFor(false, emptySet()), true))
    }

    private val ParticleSpec?.particleImageIds: Set<String>
        get() = listOf(Look(particles = this)).particleImageIds

    private companion object {
        val BAD = byteArrayOf(0)
    }
}
