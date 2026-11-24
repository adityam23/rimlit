package app.rimlit.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.model.GlowSettings
import app.rimlit.model.UiPrefs
import app.rimlit.model.canAddLook
import app.rimlit.ui.components.LookLimitNote
import app.rimlit.ui.components.NavCard
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.SettingsList
import app.rimlit.ui.components.SubScreenScaffold
import app.rimlit.ui.components.SwitchCard
import app.rimlit.ui.editor.EditorTarget

/**
 * Home: the master switch, anything that needs the user, the user's looks (open one to edit it, long press for more,
 * "+ New" to start one from a premade), then the app and behaviour settings.
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenApps: () -> Unit,
    onOpenBehavior: () -> Unit,
    onOpenEditor: (EditorTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val fallback by viewModel.glowFallback.collectAsStateWithLifecycle()
    val uiPrefs by viewModel.uiPrefs.collectAsStateWithLifecycle()
    val sharing = rememberLookSharing(viewModel)
    var opened by rememberSaveable(stateSaver = Attention.Saver) { mutableStateOf<Attention?>(null) }
    // A recommended permission put off shows in Behavior instead.
    val attentions = attentions(permissions, fallback)
        .filterNot { it.dismissible && it.kind.name in uiPrefs.dismissedPermissions }

    SubScreenScaffold(
        title = stringResource(R.string.app_name),
        onBack = null,
        modifier = modifier,
        actions = { GalleryMenu(sharing, canImport = settings.canAddLook) },
    ) { innerPadding ->
        SettingsList(innerPadding) {
            item {
                SwitchCard(
                    title = stringResource(R.string.master_toggle_title),
                    checked = settings.enabled,
                    onCheckedChange = { enabled -> viewModel.update { it.copy(enabled = enabled) } },
                    subtitle = stringResource(R.string.master_toggle_subtitle),
                    prominent = true,
                )
            }
            attentionItems(R.string.section_attention, attentions) { opened = it }
            item { SectionTitle(stringResource(R.string.looks_section)) }
            item {
                HomeLooks(
                    settings = settings,
                    onEdit = viewModel::update,
                    onShare = sharing::shareLook,
                    onOpenEditor = onOpenEditor,
                )
            }
            if (!settings.canAddLook) item { LookLimitNote() }
            settingsItems(onOpenApps, onOpenBehavior)
        }
    }
    LookSharingDialogs(sharing, settings)
    AttentionSheet(
        attention = opened,
        onOpenSettings = viewModel::openPermission,
        onNotNow = { put -> viewModel.updateUi { it.putOff(put.kind.name) } },
        onClose = { opened = null },
    )
}

/**
 * The look gallery and what its tiles ask: the editor on a look, on a copy of one, or on a new one from the premade
 * picked ([onOpenEditor]); a look's rename or delete ([onEdit] makes the settings edit each asks for); sharing it.
 */
@Composable
private fun HomeLooks(
    settings: GlowSettings,
    onEdit: (SettingsEdit) -> Unit,
    onShare: (lookId: String) -> Unit,
    onOpenEditor: (EditorTarget) -> Unit,
) {
    var lookAction by rememberSaveable(stateSaver = LookAction.Saver) { mutableStateOf<LookAction?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    LookGallery(
        settings = settings,
        onOpen = { onOpenEditor(EditorTarget.Edit(it.id)) },
        onAction = { look, action ->
            when (action) {
                TileAction.DUPLICATE -> onOpenEditor(EditorTarget.Copy(look.id))
                TileAction.RENAME -> lookAction = LookAction.Rename(look.id)
                TileAction.DELETE -> lookAction = LookAction.Delete(look.id)
                TileAction.SHARE -> onShare(look.id)
            }
        },
        onNew = { picking = true },
    )
    if (picking) {
        PremadePickerDialog(
            settings = settings,
            onPick = { premade ->
                picking = false
                onOpenEditor(EditorTarget.New(premade))
            },
            onDismiss = { picking = false },
        )
    }
    // Restored for a look that no longer exists, the dialog waits unseen; the next action replaces it.
    val action = lookAction ?: return
    val look = settings.look(action.lookId) ?: return
    LookActionDialog(
        action = action,
        look = look,
        settings = settings,
        onDone = { edit ->
            onEdit(edit)
            lookAction = null
        },
        onDismiss = { lookAction = null },
    )
}

/** The settings beyond the looks: which apps glow, and how glows behave; then what Rimlit does. */
private fun LazyListScope.settingsItems(onOpenApps: () -> Unit, onOpenBehavior: () -> Unit) {
    item { SectionTitle(stringResource(R.string.section_settings)) }
    item {
        NavCard(
            icon = Icons.Filled.Apps,
            title = stringResource(R.string.nav_apps_title),
            subtitle = stringResource(R.string.nav_apps_subtitle),
            onClick = onOpenApps,
        )
    }
    item {
        NavCard(
            icon = Icons.Filled.Tune,
            title = stringResource(R.string.nav_behavior_title),
            subtitle = stringResource(R.string.nav_behavior_subtitle),
            onClick = onOpenBehavior,
        )
    }
    item {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.info_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** These preferences with the recommended permission [name] put off: it shows in Behavior instead of on Home. */
private fun UiPrefs.putOff(name: String) = copy(dismissedPermissions = dismissedPermissions + name)

/** The gallery's menu: import a look file (while another look fits), and export or share several looks. */
@Composable
private fun GalleryMenu(sharing: LookSharing, canImport: Boolean) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, stringResource(R.string.looks_menu))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.looks_import)) },
            onClick = {
                open = false
                sharing.import()
            },
            enabled = canImport,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.looks_export)) },
            onClick = {
                open = false
                sharing.export()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.looks_share)) },
            onClick = {
                open = false
                sharing.share()
            },
        )
    }
}
