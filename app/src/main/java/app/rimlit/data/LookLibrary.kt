package app.rimlit.data

import android.net.Uri
import android.util.Log
import app.rimlit.model.Look
import app.rimlit.model.LookSave
import app.rimlit.model.MAX_LOOKS
import app.rimlit.model.UseFor
import app.rimlit.model.addImportedLooks
import app.rimlit.model.particleImageIds
import app.rimlit.model.savingLook
import app.rimlit.model.withImageIds
import app.rimlit.model.withOnlyImages
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The saved looks with the particle image files they draw, kept consistent: every change that adds image files runs
 * under one lock with the clean-up, so it never deletes an image a look is about to draw, and a failed import leaves
 * no file behind. One per process, owned by `RimlitApp`, so every screen shares its lock.
 */
class LookLibrary(private val repository: SettingsRepository, private val images: ParticleImageFiles) {
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
     * Imports the picked images at [uris] (untrusted, see [ParticleImageStore]) for a look being edited, as many as
     * the look has [room] for; their ids. Those past the room, and those that didn't import, are left out. Until a
     * saved look draws them, the clean-up keeps them through their grace ([UNUSED_IMAGE_GRACE_MS]), so nothing needs
     * to hold on to them meanwhile.
     */
    suspend fun importDraftImages(uris: List<Uri>, room: Int): List<String> = lock.withLock {
        uris.take(room.coerceAtLeast(0)).mapNotNull { images.import(it) }
    }

    /**
     * Saves the edited [look], [isNew] or not, as [useFor] says (see [savingLook]); what it came to: saved, or why it
     * was refused, nothing written then. Images that are gone meanwhile (a draft restored after their grace ran out)
     * are left out of it.
     */
    suspend fun saveLook(look: Look, useFor: UseFor, isNew: Boolean): LookSave = lock.withLock {
        val stored = listOf(look).particleImageIds.filter { images.has(it) }.toSet()
        val kept = look.copy(particles = look.particles?.withOnlyImages(stored))
        lateinit var result: LookSave
        repository.update { settings ->
            result = settings.savingLook(kept, useFor, isNew)
            (result as? LookSave.Saved)?.settings ?: settings
        }
        result
    }

    /**
     * [write]'s result for the saved looks [ids], in the library's order, under the lock: no clean-up deletes an image
     * they draw meanwhile, so the look file it writes from them is whole.
     */
    suspend fun <T> writing(ids: Set<String>, write: suspend (List<Look>) -> T): T = lock.withLock {
        write(repository.settings.first().looks.filter { it.id in ids })
    }

    /** How many more looks fit. */
    suspend fun room(): Int = MAX_LOOKS - repository.settings.first().looks.size

    /**
     * Adds [looks] from [archive], as many as fit: their images are imported first, through the particle image
     * pipeline, then the looks drawing them in one settings write; how many were added. The images this call imported
     * that no added look draws are deleted at once, whatever their grace: those of the looks that didn't fit, and all
     * of them when it fails before the write.
     *
     * @throws LookArchiveException when one of the images doesn't decode.
     * @throws IOException when the settings can't be written.
     */
    suspend fun add(archive: LookArchive, looks: List<Look>): Int = lock.withLock {
        val imported = mutableListOf<String>()
        var added = 0
        val written = try {
            val ids = looks.particleImageIds.associateWith { id ->
                val imageId = images.import(archive.images.getValue(id))
                    ?: throw LookArchiveException(LookArchiveException.Problem.INVALID, "An image didn't decode")
                imageId.also { imported += it }
            }
            repository.update { settings ->
                settings.addImportedLooks(looks.map { it.withImageIds(ids) })
                    .also { added = it.looks.size - settings.looks.size }
            }
        } catch (e: IOException) {
            // Nothing was written. A cancelled call deletes nothing: its write may have landed, so its images are left
            // to the clean-up, which goes by what is stored once their grace is over.
            withContext(NonCancellable) { images.delete(imported) }
            throw e
        }
        images.delete(imported - written.particleImageIds)
        added
    }

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
