package app.rimlit.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import app.rimlit.model.isValidParticleImageId
import app.rimlit.model.newParticleImageId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "ParticleImageStore"

/** Side of every stored particle image, in pixels: about the largest particle on a dense screen. */
internal const val PARTICLE_IMAGE_PX = 128

/** The most bytes read from a picked image: more than any phone photo, far less than a decompression bomb. */
internal const val MAX_IMPORT_BYTES = 24 * 1024 * 1024

/**
 * The largest side and pixel count an image may claim in its header; anything bigger is rejected unread. The pixel
 * count admits a 50 MP phone photo (8192 × 6144) and little more: a format that can't be sampled down as it is read
 * decodes in full first, at four bytes a pixel.
 */
internal const val MAX_IMPORT_SIDE = 16_384
internal const val MAX_IMPORT_PIXELS = 51_000_000L

/** A stored image larger than this isn't one of ours (a 128 px PNG is well under it): it is never decoded. */
internal const val MAX_STORED_BYTES = 512 * 1024

/** PNG is lossless: the quality is ignored. */
private const val PNG_QUALITY = 100
private const val EXTENSION = ".png"

/**
 * The user's particle images, in `filesDir/particles/<id>.png`. A picked image is untrusted input: it is read with a
 * byte cap, rejected from its header when its size is absurd, decoded straight to a [PARTICLE_IMAGE_PX] square (its
 * first frame, when animated) and re-encoded as PNG, so neither the original, its name nor its metadata is kept.
 */
class ParticleImageStore(context: Context, private val io: CoroutineDispatcher = Dispatchers.IO) : ParticleImageFiles {
    private val resolver = context.contentResolver
    private val dir = File(context.filesDir, "particles")

    /** Imports the image at [uri]; its id, or `null` when it can't be read, is too large, or doesn't decode. */
    override suspend fun import(uri: Uri): String? = withContext(io) {
        try {
            val bytes = resolver.openInputStream(uri)?.use { it.readCapped(MAX_IMPORT_BYTES) }
                ?: throw IOException("Unreadable, or past $MAX_IMPORT_BYTES bytes")
            save(decodeSquare(ByteBuffer.wrap(bytes)))
        } catch (e: IOException) {
            Log.w(TAG, "Skipping a picked image", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Skipping a picked image Rimlit may no longer read", e)
            null
        }
    }

    /**
     * Imports the image [bytes] (untrusted: from a shared look file) as a picked image is imported, but only if it is
     * what an export writes (see [isExportedImage]); its id, or `null` when it isn't, or doesn't decode.
     */
    override suspend fun import(bytes: ByteArray): String? = withContext(io) {
        try {
            save(decodeSquare(ByteBuffer.wrap(bytes), exported = true))
        } catch (e: IOException) {
            Log.w(TAG, "Skipping a shared image", e)
            null
        }
    }

    /** The stored image [id], or `null` when there is none (or it isn't one of ours). Small enough to load anywhere. */
    fun load(id: String): Bitmap? = storedFile(id)?.let { BitmapFactory.decodeFile(it.path) }
        ?.takeIf { it.width == PARTICLE_IMAGE_PX && it.height == PARTICLE_IMAGE_PX }

    /** Whether the image [id] is stored (and one of ours). */
    override suspend fun has(id: String): Boolean = withContext(io) { storedFile(id) != null }

    /** The stored image [id]'s PNG bytes, to share; `null` when there is none (or it isn't one of ours). */
    suspend fun read(id: String): ByteArray? = withContext(io) { storedFile(id)?.readBytes() }

    /** The file of the stored image [id], or `null` when there is none, or it is too large to be one of ours. */
    private fun storedFile(id: String): File? {
        if (!isValidParticleImageId(id)) return null
        return File(dir, id + EXTENSION).takeIf { it.isFile && it.length() <= MAX_STORED_BYTES }
    }

    /** Deletes the stored images [ids] at once, whatever their grace. Only for [LookLibrary]'s failed imports. */
    override suspend fun delete(ids: Collection<String>) = withContext(io) {
        for (id in ids.filter(::isValidParticleImageId)) {
            val file = File(dir, id + EXTENSION)
            if (file.exists() && !file.delete()) Log.w(TAG, "Couldn't delete an imported particle image")
        }
    }

    /**
     * Deletes every stored file but the images [keep] names, once past its grace ([UNUSED_IMAGE_GRACE_MS]). Only for
     * [deleteUnusedParticleImages].
     */
    override suspend fun retainOnly(keep: Set<String>) = withContext(io) {
        val files = dir.listFiles() ?: return@withContext
        val modified = files.associate { it.name to it.lastModified() }
        for (name in unreferencedFiles(modified, keep, System.currentTimeMillis())) {
            if (!File(dir, name).delete()) Log.w(TAG, "Couldn't delete an unused particle image")
        }
    }

    /** [bitmap] written under a new id, through a temporary file so a partial write never reads as an image. */
    private fun save(bitmap: Bitmap): String {
        dir.mkdirs()
        val id = newParticleImageId()
        val temporary = File(dir, "$id.tmp")
        try {
            val written = temporary.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
            if (!written || !temporary.renameTo(File(dir, id + EXTENSION))) throw IOException("Couldn't save the image")
        } finally {
            temporary.delete()
            bitmap.recycle()
        }
        return id
    }
}

/**
 * [bytes] decoded at the size [fittedSize] gives (the decoder samples it down as it reads, so a large photo never
 * decodes in full), centred on a transparent [PARTICLE_IMAGE_PX] square.
 *
 * With [exported], only an image as Rimlit exports one is decoded at all (see [isExportedImage]).
 *
 * @throws IOException when it doesn't decode, claims a size past the caps, or needs more memory than there is.
 */
private fun decodeSquare(bytes: ByteBuffer, exported: Boolean = false): Bitmap = try {
    val image = ImageDecoder.decodeBitmap(ImageDecoder.createSource(bytes)) { decoder, info, _ ->
        if (exported && !isExportedImage(info.size.width, info.size.height, info.mimeType)) {
            throw IOException("Not an image Rimlit exported: ${info.mimeType}, ${info.size}")
        }
        val size = fittedSize(info.size.width, info.size.height) ?: throw IOException("Image too large: ${info.size}")
        decoder.setTargetSize(size.first, size.second)
        // A software bitmap in sRGB, to draw onto the square; animated images decode their first frame.
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
    }
    createBitmap(PARTICLE_IMAGE_PX, PARTICLE_IMAGE_PX).also { square ->
        val left = (PARTICLE_IMAGE_PX - image.width) / 2f
        val top = (PARTICLE_IMAGE_PX - image.height) / 2f
        Canvas(square).drawBitmap(image, left, top, Paint(Paint.FILTER_BITMAP_FLAG))
        image.recycle()
    }
} catch (e: OutOfMemoryError) {
    // An image within the caps can still be more than this device can decode (one that decodes in full before it is
    // scaled). The failed allocation is the decoder's own, so nothing is left half-done: the image is refused like
    // any other that doesn't decode, and logged with this cause by the importer, instead of ending the process.
    throw IOException("Out of memory decoding the image", e)
}

/**
 * An image as Rimlit exports one, by its header: a PNG of exactly the stored square. A look file's images are held to
 * this instead of a photo's caps: a few hundred kilobytes of PNG can claim tens of megapixels, and a file carries
 * dozens of images, so anything else is refused before a pixel is decoded.
 */
internal fun isExportedImage(width: Int, height: Int, mimeType: String): Boolean =
    width == PARTICLE_IMAGE_PX && height == PARTICLE_IMAGE_PX && mimeType == "image/png"

/**
 * The size an image of [width] × [height] decodes at: scaled to fit a [side] square, keeping its shape; `null` when
 * its claimed size is empty or absurd (past [MAX_IMPORT_SIDE] or [MAX_IMPORT_PIXELS]), so it is never decoded.
 */
internal fun fittedSize(width: Int, height: Int, side: Int = PARTICLE_IMAGE_PX): Pair<Int, Int>? {
    if (width !in 1..MAX_IMPORT_SIDE || height !in 1..MAX_IMPORT_SIDE) return null
    if (width.toLong() * height > MAX_IMPORT_PIXELS) return null
    val scale = min(side.toFloat() / width, side.toFloat() / height)
    return (width * scale).roundToInt().coerceIn(1, side) to (height * scale).roundToInt().coerceIn(1, side)
}

/**
 * How long an image no saved look draws is kept: one picked for a look still being edited, and saved only later (or
 * discarded), is never deleted under the editor, whatever happens to it meanwhile (rotation, the app killed and
 * restored, an import finishing late). A discarded one, small and app-private, lingers up to this long.
 */
internal const val UNUSED_IMAGE_GRACE_MS = 24 * 60 * 60 * 1000L

/**
 * The files among [modified] (last-modified time by name) to delete at [now]: those that aren't a stored image [keep]
 * names (unused images, and anything else), once older than [UNUSED_IMAGE_GRACE_MS].
 */
internal fun unreferencedFiles(modified: Map<String, Long>, keep: Set<String>, now: Long): List<String> =
    modified.filter { (name, at) ->
        !(name.endsWith(EXTENSION) && name.removeSuffix(EXTENSION) in keep) && now - at >= UNUSED_IMAGE_GRACE_MS
    }.keys.toList()
