package app.rimlit.ui.editor

import android.content.Intent
import app.rimlit.data.toEnumOrNull
import app.rimlit.model.Premade

/** What the look editor opens on: a saved look, an unsaved copy of one, or a new look from a premade. */
sealed interface EditorTarget {
    data class Edit(val lookId: String) : EditorTarget

    data class Copy(val lookId: String) : EditorTarget

    data class New(val premade: Premade) : EditorTarget

    /** This target in [intent]'s extras, read back by [from]. */
    fun putIn(intent: Intent): Intent = when (this) {
        is Edit -> intent.putExtra(EDIT, lookId)
        is Copy -> intent.putExtra(COPY, lookId)
        is New -> intent.putExtra(NEW, premade.name)
    }

    companion object {
        private const val EDIT = "app.rimlit.editor.EDIT"
        private const val COPY = "app.rimlit.editor.COPY"
        private const val NEW = "app.rimlit.editor.NEW"

        /** The target [intent] carries ([putIn]); `null` for none. */
        fun from(intent: Intent): EditorTarget? = intent.getStringExtra(EDIT)?.let(::Edit)
            ?: intent.getStringExtra(COPY)?.let(::Copy)
            ?: intent.getStringExtra(NEW).toEnumOrNull<Premade>()?.let(::New)
    }
}
