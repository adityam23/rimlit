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
 * it, and saving it. Until saved, its images are kept by the clean-up's grace, so a discarded draft needs nothing.
 */
class LookDrafts internal constructor(private val library: LookLibrary, private val scope: CoroutineScope) {

    /**
     * Imports the picked images at [uris] (untrusted, see [ParticleImageStore]), up to [MAX_PARTICLE_IMAGES], for the
     * draft; [onDone] gets their ids, and how many couldn't be added.
     */
    fun importImages(uris: List<Uri>, onDone: (ids: List<String>, rejected: Int) -> Unit) {
        scope.launch {
            val ids = library.importDraftImages(uris)
            onDone(ids, uris.size - ids.size)
        }
    }

    /** Saves [look], applied as [useFor] says; [onDone] says whether it was saved. */
    fun save(look: Look, useFor: UseFor, onDone: (saved: Boolean) -> Unit) {
        scope.launch { onDone(library.saveLook(look, useFor)) }
    }
}
