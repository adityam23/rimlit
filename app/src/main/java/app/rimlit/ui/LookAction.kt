package app.rimlit.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/**
 * What the user asked to do to the look [lookId], while its dialog is up. It names the look by id, so it survives a
 * configuration change with [Saver]; the look itself is read from the settings when the dialog shows.
 */
internal sealed interface LookAction {
    val lookId: String

    /** A new look, copied from [lookId]: "new" or "duplicate", with their own title and suggested name. */
    data class Add(override val lookId: String, val duplicate: Boolean) : LookAction

    data class Rename(override val lookId: String) : LookAction

    data class Delete(override val lookId: String) : LookAction

    companion object {
        private const val ADD = "add"
        private const val DUPLICATE = "duplicate"
        private const val RENAME = "rename"
        private const val DELETE = "delete"

        /** Saves an action as its kind and look id; `null` (no dialog) as an empty list. */
        val Saver: Saver<LookAction?, Any> = listSaver<LookAction?, String>(
            save = { action ->
                when (action) {
                    null -> emptyList()
                    is Add -> listOf(if (action.duplicate) DUPLICATE else ADD, action.lookId)
                    is Rename -> listOf(RENAME, action.lookId)
                    is Delete -> listOf(DELETE, action.lookId)
                }
            },
            restore = ::restore,
        )

        private fun restore(saved: List<String>): LookAction? {
            if (saved.size != 2) return null
            val (kind, id) = saved
            return when (kind) {
                ADD -> Add(id, duplicate = false)
                DUPLICATE -> Add(id, duplicate = true)
                RENAME -> Rename(id)
                DELETE -> Delete(id)
                else -> null
            }
        }
    }
}
