package app.rimlit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.data.AppInfo
import app.rimlit.data.matching
import app.rimlit.model.GlowSettings
import app.rimlit.model.Look
import app.rimlit.model.withAppLook
import app.rimlit.ui.components.AppSearchField
import app.rimlit.ui.components.SettingIcon
import app.rimlit.ui.components.SettingsList
import app.rimlit.ui.components.SubScreenScaffold
import app.rimlit.ui.components.SwitchCard
import app.rimlit.ui.editor.EditorTarget

/**
 * Which apps glow, and with which look: the reverse of the look editor's "Use for". Every app glows but those
 * switched off, or only those switched on ("Only apps I choose"); tapping an app opens its look ([AppLookSheet]).
 */
@Composable
fun AppsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenEditor: (EditorTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stored by viewModel.storedSettings.collectAsStateWithLifecycle()
    val settings = stored ?: GlowSettings()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppFilter.ALL) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    // Arranged by the settings as they are when the list is opened, searched or filtered, the mode flips, or the stored
    // settings are first read (so they decide, not the defaults before them): never as an app is changed, so its row
    // stays under the finger.
    val shown = remember(apps, query, filter, settings.onlyChosenApps, stored == null) {
        apps.matching(query).arranged(settings, filter)
    }

    SubScreenScaffold(
        title = stringResource(R.string.nav_apps_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        SettingsList(padding) {
            appsHeader(settings, query, filter, onQuery = { query = it }, onFilter = { filter = it }) { only ->
                viewModel.update { it.copy(onlyChosenApps = only) }
            }
            appRows(shown, settings, onOpen = { opened = it.packageName }) { app, on ->
                // One app set on or off, applied to the stored settings as they are when written: never this
                // screen's copy, so quick switches in a row each keep the others.
                viewModel.update { it.withAppSwitched(app.packageName, on) }
            }
        }
    }

    val app = opened?.let { name -> apps.firstOrNull { it.packageName == name } } ?: return
    AppLookSheet(
        app = app,
        settings = settings,
        onPick = { look -> viewModel.update { it.withAppLook(app.packageName, look.id) } },
        onOpenEditor = onOpenEditor,
        onClose = { opened = null },
    )
}

/** The mode, the search and the filter chips. */
private fun LazyListScope.appsHeader(
    settings: GlowSettings,
    query: String,
    filter: AppFilter,
    onQuery: (String) -> Unit,
    onFilter: (AppFilter) -> Unit,
    onOnlyChosen: (Boolean) -> Unit,
) {
    item(key = "mode") {
        SwitchCard(
            title = stringResource(R.string.apps_only_chosen_title),
            checked = settings.onlyChosenApps,
            onCheckedChange = onOnlyChosen,
            subtitle = stringResource(
                if (settings.onlyChosenApps) R.string.apps_only_chosen_on_desc else R.string.apps_only_chosen_desc,
            ),
            leading = { SettingIcon(Icons.Filled.Checklist) },
        )
    }
    item(key = "search") { AppSearchField(query, onQuery) }
    item(key = "filters") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (each in AppFilter.entries) {
                FilterChip(
                    selected = each == filter,
                    onClick = { onFilter(each) },
                    label = { Text(stringResource(each.labelRes(settings.onlyChosenApps))) },
                )
            }
        }
    }
}

/** The apps [shown], or that none are. */
private fun LazyListScope.appRows(
    shown: List<AppInfo>,
    settings: GlowSettings,
    onOpen: (AppInfo) -> Unit,
    onSwitch: (AppInfo, Boolean) -> Unit,
) {
    if (shown.isEmpty()) {
        item(key = "empty") {
            Text(
                text = stringResource(R.string.apps_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
        return
    }
    items(shown, key = { it.packageName }) { app ->
        val ownLook = settings.appLook(app.packageName)
        AppRow(
            app = app,
            modifier = Modifier.clickable(onClickLabel = stringResource(R.string.app_look_open)) { onOpen(app) },
            supporting = { LookLine(ownLook, settings.defaultLook) },
        ) {
            Switch(checked = settings.glowsFor(app.packageName), onCheckedChange = { onSwitch(app, it) })
        }
    }
}

/** The look an app glows with, under its name: its [ownLook]'s name, or "Default (Glow)" when it has none. */
@Composable
private fun LookLine(ownLook: Look?, defaultLook: Look) {
    Text(
        text = ownLook?.name ?: stringResource(R.string.app_look_default, defaultLook.name),
        style = MaterialTheme.typography.bodySmall,
        color = if (ownLook != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun AppFilter.labelRes(onlyChosenApps: Boolean): Int = when (this) {
    AppFilter.ALL -> R.string.apps_filter_all
    AppFilter.OWN_LOOK -> R.string.apps_filter_own_look
    AppFilter.SWITCHED -> if (onlyChosenApps) R.string.apps_filter_on else R.string.apps_filter_off
}
