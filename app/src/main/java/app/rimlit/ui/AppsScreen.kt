package app.rimlit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.data.AppIcons
import app.rimlit.data.AppInfo
import app.rimlit.data.matching
import app.rimlit.model.GlowSettings
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
            appRows(shown, settings, viewModel.appIcons, onOpen = { opened = it.packageName }) { app, on ->
                viewModel.update { it.withAppSwitched(app.packageName, on) }
            }
        }
    }

    val app = opened?.let { name -> apps.firstOrNull { it.packageName == name } } ?: return
    AppLookSheet(
        app = app,
        settings = settings,
        icons = viewModel.appIcons,
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
    icons: AppIcons,
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
        AppRow(app, settings, icons, onOpen = { onOpen(app) }, onSwitch = { onSwitch(app, it) })
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    settings: GlowSettings,
    icons: AppIcons,
    onOpen: () -> Unit,
    onSwitch: (Boolean) -> Unit,
) {
    val ownLook = settings.appLook(app.packageName) != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.app_look_open), onClick = onOpen)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icons, app)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = settings.lookLine(app.packageName),
                style = MaterialTheme.typography.bodySmall,
                color = if (ownLook) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = settings.glowsFor(app.packageName), onCheckedChange = onSwitch)
    }
}

/** The look [packageName] glows with, under its name: "Default (Glow)", or its own look's name. */
@Composable
@ReadOnlyComposable
private fun GlowSettings.lookLine(packageName: String): String =
    appLook(packageName)?.name ?: stringResource(R.string.app_look_default, defaultLook.name)

private fun AppFilter.labelRes(onlyChosenApps: Boolean): Int = when (this) {
    AppFilter.ALL -> R.string.apps_filter_all
    AppFilter.OWN_LOOK -> R.string.apps_filter_own_look
    AppFilter.SWITCHED -> if (onlyChosenApps) R.string.apps_filter_on else R.string.apps_filter_off
}
