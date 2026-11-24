package app.rimlit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.rimlit.R
import app.rimlit.data.AppInfo
import app.rimlit.model.GlowSettings
import app.rimlit.model.Look
import app.rimlit.model.canAddLook
import app.rimlit.model.otherAppsSharingLook
import app.rimlit.ui.components.LookLimitNote
import app.rimlit.ui.editor.EditorTarget

/**
 * [app]'s look: every look as a tile, the one it glows with outlined; a tap gives it that look ([onPick]). Then edit
 * that look, or, while another fits, pick a premade for a new one made for the app: either opens the editor
 * ([onOpenEditor]), which closes the sheet first ([onClose]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppLookSheet(
    app: AppInfo,
    settings: GlowSettings,
    onPick: (Look) -> Unit,
    onOpenEditor: (EditorTarget) -> Unit,
    onClose: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val open = { target: EditorTarget ->
        onClose()
        onOpenEditor(target)
    }
    val current = settings.lookFor(app.packageName)
    // Short enough to show whole: half open, its buttons would be out of sight.
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppHeader(app, Modifier.padding(horizontal = 24.dp))
            Text(
                stringResource(R.string.app_look_glows_with),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            LookTiles(settings, current, onPick)
            AppLookActions(
                app = app,
                current = current,
                sharedWith = settings.otherAppsSharingLook(app.packageName),
                canAdd = settings.canAddLook,
                onEdit = { open(EditorTarget.Edit(it.id)) },
                onNew = { picking = true },
            )
        }
    }
    // Above the sheet, which stays open beneath it: cancelling goes back to the sheet.
    if (picking) {
        PremadePickerDialog(
            settings = settings,
            onPick = { premade -> open(EditorTarget.New(premade, app)) },
            onDismiss = { picking = false },
        )
    }
}

/**
 * Every look as a tile, [current] outlined and the default look labelled; a tap picks one ([onPick]). One choice of
 * many, so each tile is a radio button to accessibility services.
 */
@Composable
private fun LookTiles(settings: GlowSettings, current: Look, onPick: (Look) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.selectableGroup(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(settings.looks, key = { it.id }) { look ->
            val selected = look.id == current.id
            LookCard(
                settings = settings,
                look = look,
                modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) { onPick(look) },
                selected = selected,
            ) {
                if (look.id == settings.defaultLookId) {
                    Text(
                        stringResource(R.string.look_default),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppHeader(app: AppInfo, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(app.label, style = MaterialTheme.typography.headlineSmall)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Edit [current], saying when [sharedWith] other apps glow with it too (editing it changes them as well); then a new
 * look for [app] while [canAdd], or why not.
 */
@Composable
private fun AppLookActions(
    app: AppInfo,
    current: Look,
    sharedWith: Int,
    canAdd: Boolean,
    onEdit: (Look) -> Unit,
    onNew: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { onEdit(current) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.app_look_edit, current.name))
        }
        if (sharedWith > 0) {
            Text(
                text = pluralStringResource(R.plurals.app_look_shared, sharedWith, sharedWith),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onNew, enabled = canAdd, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.app_look_new, app.label))
        }
        if (!canAdd) LookLimitNote()
    }
}
