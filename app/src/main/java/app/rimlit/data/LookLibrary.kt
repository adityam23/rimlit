package app.rimlit.data

import android.net.Uri
import android.util.Log
import app.rimlit.model.Look
import app.rimlit.model.MAX_LOOKS
import app.rimlit.model.MAX_PARTICLE_IMAGES
import app.rimlit.model.addImportedLooks
import app.rimlit.model.particleImageIds
import app.rimlit.model.withImageIds
import app.rimlit.model.withImages
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * The saved looks with the particle image files they draw, kept consistent: every change that adds image files runs
 * under one lock, saves the looks that draw them, then deletes the files no look draws, so a clean-up never deletes
 * an image a look is about to reference, and a failed change leaves no file behind.
 */
class LookLibrary(
    private val repository: SettingsRepository,
    private val images: ParticleImageStore,
    private val files: LookFiles,
) {
    private val lock = Mutex()

    /**
     * Deletes, whenever a look stops drawing an image (it is removed, its look edited or deleted), its file. The flow
     * only says when to look: it falls back to defaults on a failed read, so the clean-up reads for itself.
     */
    suspend fun deleteUnusedImagesOnChange() {
        repository.settings.map { it.particleImageIds }.distinctUntilChanged().collect {
            lock.withLock { deleteUnusedImages() }
        }
    }

    /**
     * Imports the picked images at [uris] (untrusted, see [ParticleImageStore]) as particles of the look [lookId],
     * after its own up to [MAX_PARTICLE_IMAGES]; how many were added.
     */
    suspend fun addParticleImages(lookId: String, uris: List<Uri>): Int = lock.withLock {
        val ids = uris.take(MAX_PARTICLE_IMAGES).mapNotNull { images.import(it) }
        val written = repository.update { settings ->
            settings.updateLook(lookId) { it.copy(particles = it.particles.withImages(ids)) }
        }
        // Any past the limit, or for a look deleted meanwhile, are no look's.
        deleteUnusedImages()
        ids.count { it in written.particleImageIds }
    }

    /** Writes the saved looks [ids], with their images, to the document [uri]; how many. */
    suspend fun export(ids: Set<String>, uri: Uri): Int = lock.withLock {
        looks(ids).also { files.export(it, uri) }.size
    }

    /** Writes the saved looks [ids], with their images, to a file for the share sheet; its URI. */
    suspend fun shareable(ids: Set<String>): Uri = lock.withLock { files.shareable(looks(ids)) }

    /** How many more looks fit. */
    suspend fun room(): Int = MAX_LOOKS - repository.settings.first().looks.size

    /**
     * Adds [looks] from [archive], as many as fit: their images are imported first, through the particle image
     * pipeline, then the looks drawing them in one settings write. On any failure no look is added, and the images
     * no look draws are deleted. How many were added.
     *
     * @throws LookArchiveException when one of the images doesn't decode.
     */
    suspend fun add(archive: LookArchive, looks: List<Look>): Int = lock.withLock {
        try {
            val ids = looks.particleImageIds.associateWith { id ->
                images.import(archive.images.getValue(id))
                    ?: throw LookArchiveException(LookArchiveException.Problem.INVALID, "An image didn't decode")
            }
            var added = 0
            repository.update { settings ->
                settings.addImportedLooks(looks.map { it.withImageIds(ids) })
                    .also { added = it.looks.size - settings.looks.size }
            }
            added
        } finally {
            deleteUnusedImages()
        }
    }

    /** The saved looks [ids], in the library's order. */
    private suspend fun looks(ids: Set<String>) = repository.settings.first().looks.filter { it.id in ids }

    /**
     * Deletes the image files no look draws, unless the settings can't be read: nothing is deleted then, and the
     * files wait for the next clean-up. Only while holding [lock].
     */
    private suspend fun deleteUnusedImages() {
        try {
            deleteUnusedParticleImages(repository, images::retainOnly)
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't read the settings; keeping every particle image", e)
        }
    }

    private companion object {
        const val TAG = "LookLibrary"
    }
}
