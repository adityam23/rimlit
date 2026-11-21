package app.rimlit

import android.net.Uri
import app.rimlit.data.LookLibrary
import app.rimlit.data.ParticleImageStore
import app.rimlit.model.Look
import app.rimlit.model.MAX_PARTICLE_IMAGES
import app.rimlit.model.UseFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the look editor's draft needs of the library, run in [scope] so it outlives the screen: images imported for
 * the draft, kept until it is saved or discarded, and saving it.
 */
class LookDrafts internal constructor(private val library: LookLibrary, private val scope: CoroutineScope) {

    /**
     * Imports the picked images at [uris] (untrusted, see [ParticleImageStore]), up to [MAX_PARTICLE_IMAGES], for the
     * draft; [onDone] gets their ids, kept until the draft is saved ([save]) or discarded ([discard]), and how many
     * couldn't be added.
     */
    fun importImages(uris: List<Uri>, onDone: (ids: List<String>, rejected: Int) -> Unit) {
        scope.launch {
            val ids = library.importDraftImages(uris)
            onDone(ids, uris.size - ids.size)
        }
    }

    /** Saves [look], applied as [useFor] says, and lets go of its draft's images, [images]; [onDone]: was it saved. */
    fun save(look: Look, useFor: UseFor, images: Set<String>, onDone: (saved: Boolean) -> Unit) {
        scope.launch { onDone(library.saveLook(look, useFor, images)) }
    }

    /** Keeps the images [images] of a draft restored after the app was away, until it is saved or discarded. */
    fun keep(images: Set<String>) {
        if (images.isNotEmpty()) scope.launch { library.keepDraftImages(images) }
    }

    /** Lets go of a discarded draft's images, [images]. */
    fun discard(images: Set<String>) {
        if (images.isNotEmpty()) scope.launch { library.discardDraft(images) }
    }
}
