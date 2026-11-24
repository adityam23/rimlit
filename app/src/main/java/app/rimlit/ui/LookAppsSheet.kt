package app.rimlit.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.R
import app.rimlit.data.AppInfo
import app.rimlit.model.GlowSettings
import app.rimlit.model.Look
import app.rimlit.model.appsUsingByName
import kotlinx.coroutines.flow.StateFlow

/**
 * The apps that have [look] as their own, by name, to read only: an app's look is changed from the Apps screen or the
 * look's editor. [apps] are those Rimlit lists, which name them, collected only while the sheet is open, so the app
 * list loads when first needed; an app it doesn't list shows its package name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LookAppsSheet(settings: GlowSettings, look: Look, apps: StateFlow<List<AppInfo>>, onClose: () -> Unit) {
    val listed by apps.collectAsStateWithLifecycle()
    val using = remember(settings, look.id, listed) {
        val labels = listed.associate { it.packageName to it.label }
        settings.appsUsingByName(look.id, labels).map { AppInfo(it, labels[it] ?: it) }
    }
    ModalBottomSheet(onDismissRequest = onClose) {
        Text(
            text = stringResource(R.string.look_apps_title, look.name),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
            items(using, key = { it.packageName }) { app -> AppRow(app = app, trailing = {}) }
        }
    }
}
