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

/**
 * Picks what [app] glows with: the default look (no rule, [onSave] gets `null`) or another look. A rule naming the
 * default look shows, and saves, as the default look.
 */
@Composable
internal fun AppLookDialog(app: AppInfo, settings: GlowSettings, onSave: (Rule?) -> Unit, onDismiss: () -> Unit) {
    // The id of the look chosen; `null` for the default look.
    var choice by remember(app.packageName) {
        mutableStateOf(settings.appLook(app.packageName)?.id?.takeUnless { it == settings.defaultLookId })
    }
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
                    RadioCard(title = look.name, selected = choice == look.id, onSelect = { choice = look.id })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(choice?.let(::Rule)) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
