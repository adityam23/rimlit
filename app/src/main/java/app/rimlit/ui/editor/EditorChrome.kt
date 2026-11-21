package app.rimlit.ui.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.rimlit.R
import app.rimlit.model.Premade
import app.rimlit.model.canAddLook
import app.rimlit.ui.components.ConfirmDialog
import app.rimlit.ui.title

/**
 * The editor's top bar, light on the dark screen behind it: back, the look's name (tap to rename), screen on or off,
 * Save, and a menu to play the draft for real ("Send test") or carry on from a copy of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorTopBar(editor: LookEditorState, onBack: () -> Unit, onSave: () -> Unit, onSendTest: () -> Unit) {
    val name = editor.look.name
    val offScreen = editor.offScreen
    val canSave = editor.unsaved && !editor.saving
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        },
        title = {
            TextButton(onClick = { editor.dialog = EditorDialog.Rename }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.look_rename),
                        tint = Color.White,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        actions = {
            IconToggleButton(checked = offScreen, onCheckedChange = { editor.offScreen = it }) {
                Icon(
                    if (offScreen) Icons.Filled.Bedtime else Icons.Filled.LightMode,
                    contentDescription = stringResource(
                        if (offScreen) R.string.editor_screen_off else R.string.editor_screen_on,
                    ),
                )
            }
            TextButton(onClick = onSave, enabled = canSave) {
                Text(
                    stringResource(R.string.save),
                    color = if (canSave) Color.White else Color.White.copy(alpha = 0.5f),
                )
            }
            EditorMenu(editor, onSendTest)
        },
    )
}

/** The editor's menu: play the draft for real ("Send test"), or carry on from a copy of it while another look fits. */
@Composable
private fun EditorMenu(editor: LookEditorState, onSendTest: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val copyName = stringResource(R.string.look_copy_name, editor.look.name)
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.editor_menu)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.editor_send_test)) },
            onClick = {
                open = false
                onSendTest()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.look_duplicate)) },
            onClick = {
                open = false
                editor.duplicate(copyName)
            },
            enabled = editor.settings.canAddLook,
        )
    }
}

/** Leaving with unsaved changes: save them ([canSave]: there is room for a new look), discard them, or stay. */
@Composable
internal fun LeaveDialog(canSave: Boolean, onSave: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_leave_title)) },
        text = { Text(stringResource(R.string.editor_leave_desc)) },
        confirmButton = {
            TextButton(onClick = onSave, enabled = canSave) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_keep_editing)) }
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.editor_discard)) }
            }
        },
    )
}

/** Switching a customised look to another premade, which loses what it changed. */
@Composable
internal fun SwitchPremadeDialog(from: Premade, to: Premade, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.editor_switch_title, to.title()),
        text = stringResource(R.string.editor_switch_desc, from.title()),
        confirmLabel = stringResource(R.string.editor_switch),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
