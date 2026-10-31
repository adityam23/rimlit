package app.rimlit.data

import app.rimlit.model.newParticleImageId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParticleImageStoreTest {

    @Test
    fun imagesFitTheSquareKeepingTheirShape() {
        val side = PARTICLE_IMAGE_PX
        assertEquals(side to side, fittedSize(4000, 4000))
        assertEquals(side to side / 2, fittedSize(4000, 2000), "landscape")
        assertEquals(72 to side, fittedSize(3024, 5376), "a tall photo")
        assertEquals(side to side, fittedSize(16, 16), "a small icon scales up to the square")
        assertEquals(side to 1, fittedSize(MAX_IMPORT_SIDE, 1), "never thinner than a pixel")
    }

    @Test
    fun emptyOrAbsurdSizesAreRejectedBeforeDecoding() {
        for ((width, height) in listOf(0 to 10, 10 to 0, -1 to 10, MAX_IMPORT_SIDE + 1 to 1, 1 to Int.MAX_VALUE)) {
            assertNull(fittedSize(width, height), "$width × $height")
        }
        // Within each side's cap, but past the pixel cap (a decompression bomb claims both).
        assertNull(fittedSize(MAX_IMPORT_SIDE, MAX_IMPORT_SIDE))
        assertEquals(PARTICLE_IMAGE_PX to PARTICLE_IMAGE_PX, fittedSize(10_000, 10_000), "exactly the pixel cap")
    }

    @Test
    fun onlyReferencedImagesAreKept() {
        val kept = newParticleImageId()
        val dropped = newParticleImageId()
        val files = listOf("$kept.png", "$dropped.png", "$kept.tmp", "$kept", "notes.txt", ".png")
        assertEquals(files - "$kept.png", unreferencedFiles(files, setOf(kept)))
        assertEquals(files, unreferencedFiles(files, emptySet()), "no look references any: all go")
        assertEquals(emptyList(), unreferencedFiles(emptyList(), setOf(kept)))
    }
}
