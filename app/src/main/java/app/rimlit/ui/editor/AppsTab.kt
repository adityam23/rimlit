package app.rimlit.ui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.rimlit.R
import app.rimlit.ui.components.CheckboxCard
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.SwitchCard
import java.util.Locale

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
    item(key = "search") {
        OutlinedTextField(
            value = editor.appQuery,
            onValueChange = { editor.appQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.apps_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
    }
    val query = editor.appQuery.trim().lowercase(Locale.getDefault())
    val shown = editor.apps.filter { query.isEmpty() || query in it.label.lowercase(Locale.getDefault()) }
    items(shown, key = { "app_${it.packageName}" }) { app ->
        CheckboxCard(
            title = app.label,
            checked = app.packageName in useFor.apps,
            onCheckedChange = { use ->
                val apps = if (use) useFor.apps + app.packageName else useFor.apps - app.packageName
                editor.applies = useFor.copy(apps = apps)
            },
        )
    }
}
