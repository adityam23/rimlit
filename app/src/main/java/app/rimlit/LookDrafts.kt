package app.rimlit

import android.net.Uri
import app.rimlit.data.LookLibrary
import app.rimlit.data.ParticleImageStore
import app.rimlit.model.Look
import app.rimlit.model.LookSave
import app.rimlit.model.UseFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The look editor's work on its draft, run in the process's [scope] (owned by [RimlitApp]), so an import or a save
 * finishes even when the editor closes first: images imported for the draft, and saving it. Until saved, its images
 * are kept by the clean-up's grace, so a discarded draft needs nothing.
 */
class LookDrafts internal constructor(private val library: LookLibrary, private val scope: CoroutineScope) {

    /**
     * Imports the picked images at [uris] (untrusted, see [ParticleImageStore]) for the draft, as many as it has
     * [room] for; [onDone] gets their ids, and how many weren't added: those past the room, and those that didn't
     * import.
     */
    fun importImages(uris: List<Uri>, room: Int, onDone: (ids: List<String>, rejected: Int) -> Unit) {
        scope.launch {
            val ids = library.importDraftImages(uris, room)
            onDone(ids, uris.size - ids.size)
        }
    }

    /** Saves [look], [isNew] or not, applied as [useFor] says; [onDone] gets what it came to: saved, or why not. */
    fun save(look: Look, useFor: UseFor, isNew: Boolean, onDone: (LookSave) -> Unit) {
        scope.launch { onDone(library.saveLook(look, useFor, isNew)) }
    }
}
