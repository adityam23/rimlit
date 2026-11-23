package app.rimlit.ui.editor

import android.content.Intent
import app.rimlit.data.AppInfo
import app.rimlit.data.toEnumOrNull
import app.rimlit.model.Premade

/** What the look editor opens on: a saved look, an unsaved copy of one, or a new look from a premade. */
sealed interface EditorTarget {
    data class Edit(val lookId: String) : EditorTarget

    data class Copy(val lookId: String) : EditorTarget

    /** A new look from [premade]; made for [app] (named after it, and used for it once saved), or for none. */
    data class New(val premade: Premade, val app: AppInfo? = null) : EditorTarget

    /** This target in [intent]'s extras, read back by [from]. */
    fun putIn(intent: Intent): Intent = when (this) {
        is Edit -> intent.putExtra(EDIT, lookId)

        is Copy -> intent.putExtra(COPY, lookId)

        is New -> intent.putExtra(NEW, premade.name).apply {
            app?.let { putExtra(APP_PACKAGE, it.packageName).putExtra(APP_LABEL, it.label) }
        }
    }

    companion object {
        private const val EDIT = "app.rimlit.editor.EDIT"
        private const val COPY = "app.rimlit.editor.COPY"
        private const val NEW = "app.rimlit.editor.NEW"
        private const val APP_PACKAGE = "app.rimlit.editor.APP_PACKAGE"
        private const val APP_LABEL = "app.rimlit.editor.APP_LABEL"

        /** The target [intent] carries ([putIn]); `null` for none. */
        fun from(intent: Intent): EditorTarget? = intent.getStringExtra(EDIT)?.let(::Edit)
            ?: intent.getStringExtra(COPY)?.let(::Copy)
            ?: intent.getStringExtra(NEW).toEnumOrNull<Premade>()?.let { New(it, appIn(intent)) }

        private fun appIn(intent: Intent): AppInfo? {
            val packageName = intent.getStringExtra(APP_PACKAGE) ?: return null
            return AppInfo(packageName, intent.getStringExtra(APP_LABEL) ?: packageName)
        }
    }
}
