package app.rimlit.data

import app.rimlit.data.LookArchiveException.Problem
import app.rimlit.model.ColorMode
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.GlowStyle
import app.rimlit.model.Look
import app.rimlit.model.MAX_LOOKS
import app.rimlit.model.ParticleImage
import app.rimlit.model.ParticleMotion
import app.rimlit.model.ParticlePicture
import app.rimlit.model.ParticleSpec
import app.rimlit.model.addImportedLooks
import app.rimlit.model.newParticleImageId
import app.rimlit.model.particleImageIds
import app.rimlit.model.withImageIds
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Look files: what is written reads back, and a hostile file is refused or its extras ignored, never trusted. */
class LookArchiveTest {

    private val imageA = "0b8e1c5e-3f4a-4c2d-9a71-5d2e8f6b1a90"
    private val imageB = "f2c4a6e8-1b3d-4f5a-8c7e-9d0b2a4c6e81"
    private val pngA = byteArrayOf(1, 2, 3)
    private val pngB = byteArrayOf(4, 5, 6, 7)

    private val snow = Look(
        id = "snow",
        name = "Snow",
        style = GlowStyle.BLADE,
        colorMode = ColorMode.RAINBOW,
        particles = ParticleSpec(ParticlePicture.Builtin(ParticleImage.SNOWFLAKE), ParticleMotion.FALL),
    )
    private val photos = Look(
        id = "photos",
        name = "Photos",
        particles = ParticleSpec(ParticlePicture.Custom(listOf(imageA, imageB)), ParticleMotion.DRIFT),
    )
    private val also = Look(
        id = "also",
        name = "Also",
        particles = ParticleSpec(ParticlePicture.Custom(listOf(imageB)), ParticleMotion.RISE),
    )
    private val archive = LookArchive(listOf(snow, photos, also), mapOf(imageA to pngA, imageB to pngB))

    private fun written(archive: LookArchive) = ByteArrayOutputStream().also { writeLookArchive(archive, it) }.toByteArray()

    private fun read(bytes: ByteArray) = readLookArchive(ByteArrayInputStream(bytes))

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
            }
        }
        return out.toByteArray()
    }

    /** These zip bytes with every [from] name renamed [to] (as long): a repeated name, which a zip writer refuses. */
    private fun ByteArray.renamed(from: String, to: String): ByteArray {
        val (old, new) = from.toByteArray() to to.toByteArray()
        val bytes = copyOf()
        for (i in 0..bytes.size - old.size) {
            if (old.indices.all { bytes[i + it] == old[it] }) new.copyInto(bytes, i)
        }
        return bytes
    }

    private fun json(
        version: Any? = LOOK_ARCHIVE_VERSION,
        looks: List<Look> = listOf(photos),
        images: List<String> = listOf(imageA, imageB),
    ) = JSONObject()
        .apply { version?.let { put("version", it) } }
        .put("looks", JSONArray(encodeLooks(looks)))
        .put("images", JSONArray(images))

    private fun looksEntry(obj: JSONObject = json()) = "looks.json" to obj.toString().toByteArray()

    private fun imageEntries() = arrayOf("images/0.png" to pngA, "images/1.png" to pngB)

    private fun assertRefused(problem: Problem, bytes: ByteArray) {
        assertEquals(problem, assertFailsWith<LookArchiveException> { read(bytes) }.problem)
    }

    private fun assertSame(expected: LookArchive, actual: LookArchive) {
        assertEquals(expected.looks, actual.looks)
        assertEquals(expected.images.keys, actual.images.keys)
        for ((id, png) in expected.images) assertContentEquals(png, actual.images[id], id)
    }

    @Test
    fun looksAndTheirImagesReadBackAsWritten() {
        assertSame(archive, read(written(archive)))
        val plain = LookArchive(listOf(snow), emptyMap())
        assertSame(plain, read(written(plain)))
    }

    @Test
    fun importedLooksDrawTheirReimportedImagesUnderNewIdsAndNames() {
        val read = read(written(archive))
        // As the view model imports them: each image under a new id from the image pipeline.
        val newIds = read.images.keys.associateWith { newParticleImageId() }
        val settings = GlowSettings(looks = listOf(Look(), Look(id = "mine", name = "Photos")))
        val imported = settings.addImportedLooks(read.looks.map { it.withImageIds(newIds) })
        assertEquals(listOf("Default", "Photos", "Snow", "Photos (2)", "Also"), imported.looks.map { it.name })
        assertEquals(imported.looks.size, imported.looks.map { it.id }.distinct().size, "ids regenerated")
        assertEquals(newIds.values.toSet(), imported.particleImageIds)
        assertEquals(
            ParticlePicture.Custom(listOf(newIds.getValue(imageA), newIds.getValue(imageB))),
            imported.looks[3].particles?.picture,
        )
    }

    @Test
    fun theFileHoldsTheFixedLayoutOnly() {
        val names = mutableListOf<String>()
        val zip = java.util.zip.ZipInputStream(ByteArrayInputStream(written(archive)))
        while (true) names += (zip.nextEntry ?: break).name
        assertEquals(listOf("looks.json", "images/0.png", "images/1.png"), names)
    }

    @Test
    fun unknownFieldsAreIgnoredAndNumbersClamped() {
        val obj = json().put("future", JSONObject().put("x", 1))
        val look = obj.getJSONArray("looks").getJSONObject(0)
        look.put("glitter", true).put("thicknessDp", 1e9).put("style", "LASER")
        val read = read(zip(looksEntry(obj), *imageEntries()))
        val expected = photos.copy(thicknessDp = Ranges.thicknessDp.endInclusive, style = Look().style)
        assertEquals(listOf(expected), read.looks)
    }

    @Test
    fun theVersionIsRequiredAndANewerOneRefused() {
        assertSame(read(zip(looksEntry(), *imageEntries())), read(zip(looksEntry(json(version = 1)), *imageEntries())))
        assertRefused(Problem.NEWER_VERSION, zip(looksEntry(json(version = LOOK_ARCHIVE_VERSION + 1)), *imageEntries()))
        for (version in listOf(null, 0, -1, "1", 1.5)) {
            assertRefused(Problem.INVALID, zip(looksEntry(json(version = version)), *imageEntries()))
        }
    }

    @Test
    fun onlyTheImagesTheLooksUseAreKept() {
        val read = read(zip(looksEntry(json(looks = listOf(also))), *imageEntries()))
        assertEquals(setOf(imageB), read.images.keys)
    }

    @Test
    fun aFileMissingOrMisnamingItsPartsIsRefused() {
        assertRefused(Problem.INVALID, zip(*imageEntries()))
        assertRefused(Problem.INVALID, zip(looksEntry(), "images/0.png" to pngA))
        assertRefused(Problem.INVALID, zip(looksEntry(json(images = listOf(imageA))), *imageEntries()))
        assertRefused(Problem.INVALID, zip(looksEntry(json(images = listOf(imageA, imageA))), *imageEntries()))
        assertRefused(Problem.INVALID, zip(looksEntry(json(images = listOf(imageA, "../evil"))), *imageEntries()))
        assertRefused(Problem.INVALID, zip(looksEntry(json(looks = emptyList())), *imageEntries()))
        val twoLooks = zip(looksEntry(), "looks.jsoX" to looksEntry().second, *imageEntries())
        assertRefused(Problem.INVALID, twoLooks.renamed("looks.jsoX", "looks.json"))
        val twoImages = zip(looksEntry(), *imageEntries(), "images/9.png" to pngA)
        assertRefused(Problem.INVALID, twoImages.renamed("images/9.png", "images/1.png"))
    }

    @Test
    fun malformedContentIsRefused() {
        assertRefused(Problem.INVALID, zip("looks.json" to "{\"version\": 1, \"looks\": [".toByteArray(), *imageEntries()))
        assertRefused(Problem.INVALID, zip("looks.json" to "[]".toByteArray()))
        assertRefused(Problem.INVALID, zip("looks.json" to byteArrayOf(-1, -2, 0, 7)))
        // Nested far deeper than the parser's stack would take: refused before it is parsed. Android's parser also
        // takes single-quoted strings and comments, so a quote inside one must not hide the nesting from the bound.
        val nesting = "[".repeat(100_000) + "]".repeat(100_000)
        val deep = listOf(
            "{\"version\":1,\"x\":$nesting}",
            "{\"version\":1,'\"':$nesting}",
            "{\"version\":1,/*\"*/\"x\":$nesting}",
            "{\"version\":1,\"x\":// \"\n$nesting}",
            "{\"version\":1,\"x\":# \"\n$nesting}",
        )
        for (json in deep) assertRefused(Problem.INVALID, zip("looks.json" to json.toByteArray()))
        // Damaged compressed data: it fails to inflate, or its checksum doesn't match.
        val damaged = written(archive).also { bytes -> for (i in 40..60) bytes[i] = (bytes[i].toInt() xor 0xff).toByte() }
        assertRefused(Problem.INVALID, damaged)
        assertRefused(Problem.INVALID, Random(1).nextBytes(4096))
        assertRefused(Problem.INVALID, ByteArray(0))
    }

    @Test
    fun aFullLibraryIsWellWithinTheNestingBound() {
        val full = LookArchive(
            looks = List(MAX_LOOKS) { photos.copy(id = "look$it", name = "Look $it") },
            images = archive.images,
        )
        assertEquals(full.looks, read(written(full)).looks)
        val openers = json(looks = full.looks).toString().count { it == '[' || it == '{' }
        assertTrue(openers * 2 < MAX_JSON_OPENERS, "$openers arrays and objects in the largest file")
    }

    @Test
    fun pathNamesAreIgnoredNeverFollowed() {
        val hostile = arrayOf(
            "../looks.json" to "garbage".toByteArray(),
            "/looks.json" to "garbage".toByteArray(),
            "./looks.json" to "garbage".toByteArray(),
            "images/../looks.json" to "garbage".toByteArray(),
            "images/../../0.png" to pngB,
            "../images/0.png" to pngB,
            "images/0.png/../../../evil" to pngB,
            "images/00.png" to pngB,
            "images/-1.png" to pngB,
            "IMAGES/0.PNG" to pngB,
            "images\\1.png" to pngA,
        )
        assertSame(read(zip(looksEntry(), *imageEntries())), read(zip(*hostile, looksEntry(), *imageEntries())))
        assertRefused(Problem.INVALID, zip(*hostile))
    }

    @Test
    fun anEntryPastItsCapIsRefused() {
        val image = Random(2).nextBytes(MAX_STORED_BYTES + 1)
        assertRefused(Problem.TOO_LARGE, zip(looksEntry(), "images/0.png" to image, "images/1.png" to pngB))
        assertRefused(Problem.TOO_LARGE, zip(looksEntry(), *imageEntries(), "ignored.bin" to image))
        val padded = json().put("padding", "x".repeat(MAX_LOOKS_JSON_BYTES))
        assertRefused(Problem.TOO_LARGE, zip(looksEntry(padded), *imageEntries()))
    }

    @Test
    fun tooManyEntriesAreRefused() {
        val extras = (1..MAX_ARCHIVE_ENTRIES).map { "extra$it" to byteArrayOf() }.toTypedArray()
        assertRefused(Problem.TOO_LARGE, zip(looksEntry(), *imageEntries(), *extras))
    }

    @Test
    fun aLargeFileIsRefused() {
        // Each entry under its own cap, all of them past the file's.
        val chunk = MAX_STORED_BYTES - 1
        val entries = (0..MAX_ARCHIVE_BYTES / chunk).map { "extra$it" to Random(it).nextBytes(chunk) }.toTypedArray()
        assertRefused(Problem.TOO_LARGE, zip(looksEntry(), *imageEntries(), *entries))
    }

    @Test
    fun aZipBombWithALyingHeaderIsRefused() {
        // 64 MiB of zeros deflate to about 64 KiB; its header claims 100 bytes.
        val bomb = ByteArrayOutputStream()
        ZipOutputStream(bomb).use { zip ->
            zip.putNextEntry(ZipEntry("looks.json"))
            val zeros = ByteArray(1 shl 20)
            repeat(64) { zip.write(zeros) }
        }
        val bytes = bomb.toByteArray()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(6, (header.getShort(6).toInt() and 0x8.inv()).toShort()) // sizes in the header, no descriptor
        header.putInt(18, 100) // compressed size
        header.putInt(22, 100) // uncompressed size
        assertRefused(Problem.TOO_LARGE, bytes)
        // As an ignored entry too: it is still counted as it expands.
        assertRefused(Problem.TOO_LARGE, zip(looksEntry(), *imageEntries(), "bomb" to ByteArray(MAX_STORED_BYTES * 4)))
    }
}
