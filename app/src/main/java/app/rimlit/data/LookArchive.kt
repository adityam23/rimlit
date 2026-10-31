package app.rimlit.data

import app.rimlit.model.Look
import app.rimlit.model.MAX_LOOKS
import app.rimlit.model.MAX_PARTICLE_IMAGES
import app.rimlit.model.isValidParticleImageId
import app.rimlit.model.particleImageIds
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// A look file (`.rimlit`) is a zip of `looks.json` and `images/<n>.png`. `looks.json` is an object: `version`, `looks`
// (the looks as the settings store them, see `SettingsJson.kt`) and `images` (the image ids the looks use, the n-th
// stored as `images/<n>.png`). Fields a reader doesn't know are ignored, so a newer writer may add some without
// changing the version; a change older readers would misread raises it.

/** The version of the look files this app writes, and the newest it reads. */
internal const val LOOK_ARCHIVE_VERSION = 1

/** The most bytes read from a look file: far more than 10 looks with all their images, far less than a bomb. */
internal const val MAX_ARCHIVE_BYTES = 16 * 1024 * 1024

/** The most bytes all of a file's entries expand to, counted as they are read. */
internal const val MAX_ARCHIVE_EXPANDED_BYTES = 16 * 1024 * 1024

/** The most entries a look file holds: `looks.json` and every image, with room to spare. */
internal const val MAX_ARCHIVE_ENTRIES = 64

/** The most bytes `looks.json` expands to: 10 looks take a few kilobytes. */
internal const val MAX_LOOKS_JSON_BYTES = 256 * 1024

/** JSON nested deeper than this isn't a look file; the parser recurses, so a deeper one could overflow its stack. */
private const val MAX_JSON_DEPTH = 16

private const val MAX_IMAGES = MAX_LOOKS * MAX_PARTICLE_IMAGES
private const val LOOKS_ENTRY = "looks.json"
private const val IMAGES_DIR = "images/"
private const val PNG = ".png"

/** An image entry's name: a plain index, never a path; any other name is ignored. */
private val IMAGE_ENTRY = Regex("images/(0|[1-9][0-9]?)\\.png")

private const val VERSION = "version"
private const val LOOKS = "looks"
private const val IMAGES = "images"
private const val COPY_BUFFER_BYTES = 8 * 1024

/** Looks to share, and the PNG bytes of every custom image their particles draw, by the id the looks use. */
class LookArchive(val looks: List<Look>, val images: Map<String, ByteArray>) {
    init {
        require(looks.particleImageIds.all { it in images }) { "An image the looks use is missing" }
    }
}

/** Why a look file can't be read. */
class LookArchiveException(val problem: Problem, message: String, cause: Throwable? = null) :
    IOException(message, cause) {
    enum class Problem {
        /** Not a look file, or a damaged one. */
        INVALID,

        /** Past one of the size or entry caps. */
        TOO_LARGE,

        /** Written by a newer Rimlit, in a version this one can't read. */
        NEWER_VERSION,
    }
}

/** Writes [archive] to [out] as a look file, leaving [out] open. */
fun writeLookArchive(archive: LookArchive, out: OutputStream) {
    val ids = archive.looks.particleImageIds.toList()
    val json = JSONObject()
        .put(VERSION, LOOK_ARCHIVE_VERSION)
        // Through the settings' own encoding, as a string: one codec for stored and shared looks.
        .put(LOOKS, JSONArray(encodeLooks(archive.looks)))
        .put(IMAGES, JSONArray(ids))
    val zip = ZipOutputStream(out)
    zip.putNextEntry(ZipEntry(LOOKS_ENTRY))
    zip.write(json.toString().toByteArray(Charsets.UTF_8))
    ids.forEachIndexed { index, id ->
        zip.putNextEntry(ZipEntry("$IMAGES_DIR$index$PNG"))
        zip.write(archive.images.getValue(id))
    }
    zip.finish()
}

/**
 * The look file [input] holds. It is untrusted: at most [MAX_ARCHIVE_BYTES] are read, at most [MAX_ARCHIVE_ENTRIES]
 * entries, each entry's bytes are counted as it expands (its header's sizes are never trusted) against its own cap
 * and [MAX_ARCHIVE_EXPANDED_BYTES], and only `looks.json` and `images/<n>.png` are kept: no other entry, and no
 * path, is ever built from an entry's name. The looks decode through the settings' own decoder, which clamps them.
 *
 * @throws LookArchiveException when it isn't a valid look file; an [IOException] when it can't be read.
 */
fun readLookArchive(input: InputStream): LookArchive {
    val (json, images) = try {
        readEntries(ZipInputStream(CappedInputStream(input, MAX_ARCHIVE_BYTES)))
    } catch (e: ZipException) {
        // A damaged zip, or (on Android 14 and later, which refuses them itself) an entry named as a path out.
        invalid("Malformed zip", e)
    } catch (e: IllegalArgumentException) {
        invalid("Malformed entry name", e)
    }
    return decodeArchive(json ?: invalid("No $LOOKS_ENTRY"), images)
}

/** The bytes of `looks.json` in [zip], if any, and of its images by index, read under the caps. */
private fun readEntries(zip: ZipInputStream): Pair<ByteArray?, Map<Int, ByteArray>> {
    var json: ByteArray? = null
    val images = mutableMapOf<Int, ByteArray>()
    var entries = 0
    var expanded = 0L
    while (true) {
        val entry = zip.nextEntry ?: break
        if (++entries > MAX_ARCHIVE_ENTRIES) tooLarge("More than $MAX_ARCHIVE_ENTRIES entries")
        val index = IMAGE_ENTRY.matchEntire(entry.name)?.groupValues?.get(1)?.toInt()
        val bytes = zip.readAtMost(if (entry.name == LOOKS_ENTRY) MAX_LOOKS_JSON_BYTES else MAX_STORED_BYTES)
        expanded += bytes.size
        if (expanded > MAX_ARCHIVE_EXPANDED_BYTES) tooLarge("Expands past $MAX_ARCHIVE_EXPANDED_BYTES bytes")
        when {
            entry.name == LOOKS_ENTRY -> if (json == null) json = bytes else invalid("Repeated $LOOKS_ENTRY")
            index != null -> if (images.put(index, bytes) != null) invalid("Repeated image $index")
        }
    }
    return json to images
}

/** The looks [json] describes, with the [images] by index it names. */
private fun decodeArchive(json: ByteArray, images: Map<Int, ByteArray>): LookArchive {
    val obj = parseObject(json.toString(Charsets.UTF_8))
    val version = obj.opt(VERSION) as? Int ?: invalid("No version")
    if (version < 1) invalid("Version $version")
    if (version > LOOK_ARCHIVE_VERSION) refuse(LookArchiveException.Problem.NEWER_VERSION, "Version $version")
    // Through the settings' own decoder, as a string: one validation path for stored and shared looks.
    val looks = decodeLooks(obj.optJSONArray(LOOKS)?.toString()) ?: invalid("No looks")
    val ids = obj.optJSONArray(IMAGES)?.let { array -> (0 until array.length()).map { array.opt(it) } }.orEmpty()
    if (ids.size > MAX_IMAGES || ids.distinct().size != ids.size) invalid("Bad image list")
    val bytes = ids.withIndex().associate { (index, id) ->
        if (id !is String || !isValidParticleImageId(id)) invalid("Bad image id")
        id to (images[index] ?: invalid("No image $index"))
    }
    if (!looks.particleImageIds.all { it in bytes }) invalid("A look's image is missing")
    // Only the images the looks use: any other is never imported.
    return LookArchive(looks, bytes.filterKeys { it in looks.particleImageIds })
}

/** The JSON object [text] holds, refused when malformed or nested deeper than [MAX_JSON_DEPTH]. */
private fun parseObject(text: String): JSONObject {
    if (jsonDepth(text) > MAX_JSON_DEPTH) invalid("Nested too deep")
    return try {
        JSONObject(text)
    } catch (e: JSONException) {
        invalid("Malformed $LOOKS_ENTRY", e)
    }
}

/** How deeply [json]'s arrays and objects nest, outside its strings. */
private fun jsonDepth(json: String): Int {
    var depth = 0
    var deepest = 0
    var inString = false
    var escaped = false
    for (c in json) {
        when {
            escaped -> escaped = false
            inString && c == '\\' -> escaped = true
            c == '"' -> inString = !inString
            inString -> Unit
            c == '[' || c == '{' -> deepest = maxOf(deepest, ++depth)
            c == ']' || c == '}' -> depth--
        }
    }
    return deepest
}

/** The rest of this stream's bytes, counted as they are read; a [LookArchiveException] past [cap]. */
private fun InputStream.readAtMost(cap: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(COPY_BUFFER_BYTES)
    while (true) {
        val read = read(chunk)
        if (read < 0) return out.toByteArray()
        if (out.size() + read > cap) tooLarge("An entry expands past $cap bytes")
        out.write(chunk, 0, read)
    }
}

/** [input], failing with a [LookArchiveException] once more than [cap] bytes are read from it. */
private class CappedInputStream(input: InputStream, private val cap: Int) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int = super.read().also { if (it >= 0) counted(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) counted(it) }

    override fun skip(n: Long): Long = super.skip(n).also { counted(it) }

    private fun counted(bytes: Number) {
        count += bytes.toLong()
        if (count > cap) tooLarge("Past $cap bytes")
    }
}

/** Refuses the file for [problem]. */
private fun refuse(problem: LookArchiveException.Problem, message: String, cause: Throwable? = null): Nothing =
    throw LookArchiveException(problem, message, cause)

private fun invalid(message: String, cause: Throwable? = null): Nothing =
    refuse(LookArchiveException.Problem.INVALID, message, cause)

private fun tooLarge(message: String): Nothing = refuse(LookArchiveException.Problem.TOO_LARGE, message)
