package app.rimlit.ui.editor

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.rimlit.R
import app.rimlit.ui.AppRow
import app.rimlit.ui.components.AppSearchField
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.SwitchCard

/**
 * Where the look applies, saved with it: as the default look, and for the apps ticked, found by name. The default
 * look stays the default: another look takes it over by turning it on there.
 */
internal fun LazyListScope.appsTab(editor: LookEditorState) {
    val useFor = editor.applies
    val isSavedDefault = editor.look.id == editor.settings.defaultLookId
    item(key = "default") {
        SwitchCard(
            title = stringResource(R.string.editor_default_title),
            checked = useFor.isDefault,
            onCheckedChange = { editor.applies = useFor.copy(isDefault = it) },
            subtitle = stringResource(
                if (isSavedDefault) R.string.editor_default_fixed_desc else R.string.editor_default_desc,
            ),
            enabled = !isSavedDefault,
        )
    }
    item(key = "apps_title") { SectionTitle(stringResource(R.string.editor_apps_section)) }
    item(key = "search") { AppSearchField(editor.appQuery, onQueryChange = { editor.appQuery = it }) }
    items(editor.shownApps, key = { "app_${it.packageName}" }) { app ->
        val checked = app.packageName in useFor.apps
        AppRow(
            app = app,
            modifier = Modifier.toggleable(value = checked, role = Role.Checkbox) { use ->
                val apps = if (use) useFor.apps + app.packageName else useFor.apps - app.packageName
                editor.applies = useFor.copy(apps = apps)
            },
        ) {
            // Display only: the row handles the toggle.
            Checkbox(checked = checked, onCheckedChange = null)
        }
    }
}
