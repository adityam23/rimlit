package app.rimlit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.rimlit.R
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.Look
import app.rimlit.model.Premade
import app.rimlit.model.appsUsing
import app.rimlit.model.canName
import app.rimlit.model.deleteLook
import app.rimlit.model.isValidLookName
import app.rimlit.model.renameLook
import app.rimlit.ui.components.ConfirmDialog
import app.rimlit.ui.components.TextFieldDialog

/** The dialog for [action] on [look], its look in [settings]; [onDone] gets the settings edit to make. */
@Composable
internal fun LookActionDialog(
    action: LookAction,
    look: Look,
    settings: GlowSettings,
    onDone: (edit: SettingsEdit) -> Unit,
    onDismiss: () -> Unit,
) {
    when (action) {
        is LookAction.Rename -> LookNameDialog(
            title = stringResource(R.string.look_rename_title),
            initial = look.name,
            settings = settings,
            exceptId = look.id,
            onConfirm = { name -> onDone { it.renameLook(look.id, name) } },
            onDismiss = onDismiss,
        )

        is LookAction.Delete -> {
            val apps = settings.appsUsing(look.id)
            ConfirmDialog(
                title = stringResource(R.string.look_delete_title, look.name),
                text = if (apps == 0) {
                    stringResource(R.string.look_delete_desc)
                } else {
                    pluralStringResource(R.plurals.look_delete_used_desc, apps, apps)
                },
                confirmLabel = stringResource(R.string.look_delete),
                onConfirm = { onDone { it.deleteLook(look.id) } },
                onDismiss = onDismiss,
            )
        }
    }
}

/** A look name, checked as the model checks it: valid, and unused by any look but [exceptId]. */
@Composable
internal fun LookNameDialog(
    title: String,
    initial: String,
    settings: GlowSettings,
    exceptId: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val maxLength = Ranges.lookNameLength.last
    val invalid = pluralStringResource(R.plurals.look_name_invalid, maxLength, maxLength)
    val taken = stringResource(R.string.look_name_taken)
    TextFieldDialog(
        title = title,
        label = stringResource(R.string.look_name_label),
        initial = initial,
        errorFor = { name ->
            when {
                !isValidLookName(name) -> invalid
                !settings.canName(name, exceptId) -> taken
                else -> null
            }
        },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** The premades a new look starts from, each playing its edge; [onPick] gets the one picked. */
@Composable
internal fun PremadePickerDialog(settings: GlowSettings, onPick: (Premade) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.premade_pick_title)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 72.dp),
                modifier = Modifier.heightIn(max = 480.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(Premade.entries, key = { it.name }) { premade ->
                    Column(
                        modifier = Modifier.clickable { onPick(premade) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PremadeGlow(premade, settings)
                        Text(premade.title(), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
