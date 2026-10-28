package app.rimlit.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.rimlit.R

/**
 * Asks for one line of text, starting from [initial]. The text is trimmed; [errorFor] says what is wrong with it
 * (`null` when nothing is), shown under the field and blocking [onConfirm], labelled [confirmLabel], until fixed.
 */
@Composable
fun TextFieldDialog(
    title: String,
    label: String,
    initial: String,
    errorFor: (String) -> String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = stringResource(R.string.save),
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val error = errorFor(text.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = error == null) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
