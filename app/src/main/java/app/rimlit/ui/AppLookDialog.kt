package app.rimlit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.rimlit.R
import app.rimlit.data.AppInfo
import app.rimlit.model.GlowSettings
import app.rimlit.model.Rule
import app.rimlit.ui.components.RadioCard

/** Picks what [app] glows with: the default look (no rule, [onSave] gets `null`) or another look. */
@Composable
internal fun AppLookDialog(app: AppInfo, settings: GlowSettings, onSave: (Rule?) -> Unit, onDismiss: () -> Unit) {
    var choice by remember(app.packageName) { mutableStateOf(settings.ruleFor(app.packageName)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_look_title, app.label)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioCard(
                    title = stringResource(R.string.app_look_default_title),
                    selected = choice == null,
                    onSelect = { choice = null },
                    subtitle = settings.defaultLook.name,
                )
                for (look in settings.looks.filter { it.id != settings.defaultLookId }) {
                    val rule = Rule.UseLook(look.id)
                    RadioCard(title = look.name, selected = choice == rule, onSelect = { choice = rule })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(choice) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
