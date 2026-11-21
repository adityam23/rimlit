package app.rimlit.data

import android.net.Uri
import android.util.Log
import app.rimlit.model.Look
import app.rimlit.model.MAX_LOOKS
import app.rimlit.model.MAX_PARTICLE_IMAGES
import app.rimlit.model.UseFor
import app.rimlit.model.addImportedLooks
import app.rimlit.model.particleImageIds
import app.rimlit.model.savingLook
import app.rimlit.model.withImageIds
import app.rimlit.model.withOnlyImages
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

    /** Images imported for a look being edited, kept from the clean-up until it is saved or discarded. Under [lock]. */
    private val drafted = mutableSetOf<String>()

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
     * Imports the picked images at [uris] (untrusted, see [ParticleImageStore]), up to [MAX_PARTICLE_IMAGES], for a
     * look being edited; their ids, kept until [saveLook] or [discardDraft]. Those that didn't import are left out.
     */
    suspend fun importDraftImages(uris: List<Uri>): List<String> = lock.withLock {
        uris.take(MAX_PARTICLE_IMAGES).mapNotNull { images.import(it) }.also { drafted += it }
    }

    /**
     * Keeps the images [ids] of a draft restored after the app was away (they were let go of with the process) until
     * it is saved or discarded; those already deleted stay gone, and [saveLook] leaves them out.
     */
    suspend fun keepDraftImages(ids: Set<String>) = lock.withLock { drafted += ids }

    /**
     * Saves the edited [look] as [useFor] says (see [savingLook]), then lets go of its draft's images: those it no
     * longer draws are deleted. Images that are gone meanwhile (deleted while the app was away, after the draft was
     * restored) are left out of it. Whether it was saved: not when no look fits any more, or its name is taken; the
     * draft's images are kept then, since it stays open to try again.
     */
    suspend fun saveLook(look: Look, useFor: UseFor, draftImages: Set<String>): Boolean = lock.withLock {
        val stored = listOf(look).particleImageIds.filter { images.has(it) }.toSet()
        val kept = look.copy(particles = look.particles?.withOnlyImages(stored))
        val saved = repository.update { it.savingLook(kept, useFor) }.look(kept.id) == kept
        if (saved) {
            drafted -= draftImages
            deleteUnusedImages()
        }
        saved
    }

    /** Lets go of a discarded draft's images: those no saved look draws are deleted. */
    suspend fun discardDraft(draftImages: Set<String>) = lock.withLock {
        drafted -= draftImages
        deleteUnusedImages()
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
            deleteUnusedParticleImages(repository, images::retainOnly, drafted.toSet())
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't read the settings; keeping every particle image", e)
        }
    }

    private companion object {
        const val TAG = "LookLibrary"
    }
}
