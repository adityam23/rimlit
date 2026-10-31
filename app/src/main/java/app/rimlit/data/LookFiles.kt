package app.rimlit.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import app.rimlit.model.Look
import app.rimlit.model.particleImageIds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** The MIME type of a look file: it is a zip. */
const val LOOK_FILE_MIME = "application/zip"

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

    /** Writes [looks], with their images, to the document [uri]; an [IOException] when it can't be written. */
    suspend fun export(looks: List<Look>, uri: Uri) = withContext(io) {
        val archive = archiveOf(looks)
        resolver.openOutputStream(uri, "wt")?.use { writeLookArchive(archive, it) }
            ?: throw IOException("Couldn't open the document")
    }

    /** Writes [looks], with their images, to the shared cache file; its content URI, readable by whom it is granted. */
    suspend fun shareable(looks: List<Look>): Uri = withContext(io) {
        val archive = archiveOf(looks)
        val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
        val file = File(dir, LOOK_FILE_NAME)
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
}
