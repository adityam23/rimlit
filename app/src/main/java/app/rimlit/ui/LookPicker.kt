package app.rimlit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.rimlit.R
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.Look
import app.rimlit.model.MAX_LOOKS
import app.rimlit.model.Rule
import app.rimlit.model.isValidLookName
import app.rimlit.ui.components.RadioCard
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.TextFieldDialog
import java.util.UUID

/** The looks to pick the one [editing] from, and what can be done to it. */
internal fun LazyListScope.lookPickerSection(
    settings: GlowSettings,
    editing: Look,
    onEdit: (Look) -> Unit,
    onAction: (LookAction) -> Unit,
) {
    item { SectionTitle(stringResource(R.string.looks_section)) }
    items(settings.looks, key = { "look:${it.id}" }) { look ->
        RadioCard(
            title = look.name,
            selected = look.id == editing.id,
            onSelect = { onEdit(look) },
            subtitle = settings.usage(look),
        )
    }
    item {
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val canAdd = settings.canAddLook
            OutlinedButton(onClick = { onAction(LookAction.Add(editing, duplicate = false)) }, enabled = canAdd) {
                Text(stringResource(R.string.look_new))
            }
            OutlinedButton(onClick = { onAction(LookAction.Add(editing, duplicate = true)) }, enabled = canAdd) {
                Text(stringResource(R.string.look_duplicate))
            }
            OutlinedButton(onClick = { onAction(LookAction.Rename(editing)) }) {
                Text(stringResource(R.string.look_rename))
            }
            val canDelete = editing.id != settings.defaultLookId
            OutlinedButton(onClick = { onAction(LookAction.Delete(editing)) }, enabled = canDelete) {
                Text(stringResource(R.string.look_delete))
            }
        }
    }
    if (!settings.canAddLook) {
        item {
            Text(
                text = pluralStringResource(R.plurals.look_limit, MAX_LOOKS, MAX_LOOKS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Who glows with [look]: every app without a look of its own for the default look, else the apps that picked it. */
@Composable
@ReadOnlyComposable
private fun GlowSettings.usage(look: Look): String {
    if (look.id == defaultLookId) return stringResource(R.string.look_default_desc)
    val apps = appRules.values.count { it == Rule.UseLook(look.id) }
    return if (apps == 0) {
        stringResource(R.string.look_unused_desc)
    } else {
        pluralStringResource(R.plurals.look_used_desc, apps, apps)
    }
}

/**
 * The dialog for [action] on [settings]. [onDone] gets the settings edit to make and the look to edit next; for a new
 * look it is the new one, after a delete the default look.
 */
@Composable
internal fun LookActionDialog(
    action: LookAction,
    settings: GlowSettings,
    onDone: (edit: SettingsEdit, editNext: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val look = action.look
    when (action) {
        is LookAction.Add -> {
            val id = rememberSaveable { UUID.randomUUID().toString() }
            val resources = LocalResources.current
            val numbered = generateSequence(settings.looks.size + 1) { it + 1 }
                .map { resources.getString(R.string.look_new_name, it) }
            val copy = stringResource(R.string.look_copy_name, look.name).take(Ranges.lookNameLength.last).trim()
            LookNameDialog(
                title = stringResource(
                    if (action.duplicate) R.string.look_duplicate_title else R.string.look_new_title,
                ),
                initial = settings.firstFreeName(if (action.duplicate) sequenceOf(copy) + numbered else numbered)
                    .orEmpty(),
                settings = settings,
                exceptId = null,
                onConfirm = { name -> onDone({ it.addLook(look, name, id) }, id) },
                onDismiss = onDismiss,
            )
        }

        is LookAction.Rename -> LookNameDialog(
            title = stringResource(R.string.look_rename_title),
            initial = look.name,
            settings = settings,
            exceptId = look.id,
            onConfirm = { name -> onDone({ it.renameLook(look.id, name) }, look.id) },
            onDismiss = onDismiss,
        )

        is LookAction.Delete -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.look_delete_title, look.name)) },
            text = { Text(stringResource(R.string.look_delete_desc)) },
            confirmButton = {
                TextButton(onClick = { onDone({ it.deleteLook(look.id) }, settings.defaultLookId) }) {
                    Text(stringResource(R.string.look_delete))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A look name, checked as the model checks it: valid, and unused by any look but [exceptId]. */
@Composable
private fun LookNameDialog(
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
