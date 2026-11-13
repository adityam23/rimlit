package app.rimlit.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.FileProvider
import app.rimlit.model.Look
import app.rimlit.model.particleImageIds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

/** The MIME type of a look file: it is a zip. */
const val LOOK_FILE_MIME = "application/zip"

/**
 * The MIME type a look file is saved as and may come back as: saved as a zip, a documents provider appends `.zip`
 * to [LOOK_FILE_NAME], and some file managers and messaging apps label a zip with an unknown extension like this.
 */
const val LOOK_DOCUMENT_MIME = "application/octet-stream"

/** The name a look file is saved and shared under. */
const val LOOK_FILE_NAME = "Rimlit looks.rimlit"

/** The cache directory shared look files are written to, the only one the [FileProvider] serves (`file_paths.xml`). */
private const val SHARED_DIR = "shared"

/** How exporting, sharing or importing looks ended. */
sealed interface LookFileResult {
    data class Exported(val looks: Int) : LookFileResult

    /** Ready for the share sheet, at [uri]. */
    data class Shareable(val uri: Uri) : LookFileResult

    data class Imported(val looks: Int) : LookFileResult

    /** No look fits: the library is full. */
    data object NoRoom : LookFileResult

    /** Nothing was written; [problem] says why the file was refused, `null` when it couldn't be read or written. */
    data class Failed(val problem: LookArchiveException.Problem?) : LookFileResult
}

/**
 * Look files (see `LookArchive.kt`): written to a document the user picked, or to a cache file served to the share
 * sheet, and read from a document the user picked, on [io].
 */
class LookFiles(
    private val context: Context,
    private val images: ParticleImageStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val resolver = context.contentResolver

    /**
     * Writes [looks], with their images, to the document [uri]; an [IOException] when it can't be written, and the
     * document is then deleted, so no empty or cut-off file is left where the user saved it.
     */
    suspend fun export(looks: List<Look>, uri: Uri) = withContext(io) {
        try {
            val archive = archiveOf(looks)
            resolver.openOutputStream(uri, "wt")?.use { writeLookArchive(archive, it) }
                ?: throw IOException("Couldn't open the document")
        } catch (e: IOException) {
            delete(uri)
            throw e
        }
    }

    /**
     * Deletes the document [uri], if its provider lets it. A documents provider is another app's code: whatever it
     * throws, the failed export is what gets reported, with this logged.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun delete(uri: Uri) {
        try {
            if (!DocumentsContract.deleteDocument(resolver, uri)) Log.w(TAG, "The failed export's document stays")
        } catch (e: FileNotFoundException) {
            Log.i(TAG, "The failed export's document is already gone", e)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't delete the failed export's document", e)
        }
    }

    /**
     * Writes [looks], with their images, to a fresh file under the shared cache directory, clearing whatever an
     * earlier share left there; its content URI, readable by whom it is granted. A fresh file each time means a
     * grant an earlier receiver still holds can never see a later share's content.
     */
    suspend fun shareable(looks: List<Look>): Uri = withContext(io) {
        val archive = archiveOf(looks)
        val file = freshShareFile(File(context.cacheDir, SHARED_DIR))
        file.outputStream().use { writeLookArchive(archive, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** The look file at [uri]; a [LookArchiveException] when it isn't a valid one, an [IOException] when unreadable. */
    suspend fun read(uri: Uri): LookArchive = withContext(io) {
        resolver.openInputStream(uri)?.use(::readLookArchive) ?: throw IOException("Couldn't open the document")
    }

    private suspend fun archiveOf(looks: List<Look>) = LookArchive(
        looks = looks,
        images = looks.particleImageIds.associateWith { images.read(it) ?: throw IOException("An image is missing") },
    )

    private companion object {
        const val TAG = "LookFiles"
    }
}

/**
 * Deletes everything under [dir] — nothing in the app reads a share back, so it is only ever cache the share sheet
 * might still hold open — and returns a file for a fresh share within it, under a newly made unique subdirectory
 * named [name], so the file a grant points at is never the one a later share writes to.
 */
internal fun freshShareFile(dir: File, name: String = LOOK_FILE_NAME): File {
    dir.deleteRecursively()
    val holder = File(dir, UUID.randomUUID().toString()).apply { mkdirs() }
    return File(holder, name)
}

/** Clears the shared cache directory of anything an earlier run left there. Blocking: call it off the main thread. */
fun clearSharedLookFiles(context: Context) {
    File(context.cacheDir, SHARED_DIR).deleteRecursively()
}
