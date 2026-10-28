package app.rimlit.ui

import app.rimlit.model.Look

/** What the user asked to do to a look, while its dialog is up. */
internal sealed interface LookAction {
    val look: Look

    /** A new look, copied from [look]: "new" or "duplicate", with their own title and suggested name. */
    data class Add(override val look: Look, val duplicate: Boolean) : LookAction

    data class Rename(override val look: Look) : LookAction

    data class Delete(override val look: Look) : LookAction
}
