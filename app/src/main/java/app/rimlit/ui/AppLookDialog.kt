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
import app.rimlit.model.DEFAULT_GLOW_ARGB
import app.rimlit.model.GlowPalette
import app.rimlit.model.GlowSettings
import app.rimlit.model.Rule
import app.rimlit.ui.components.PaletteEditor
import app.rimlit.ui.components.RadioCard

/**
 * Picks what [app] glows with: the default look (no rule, [onSave] gets `null`), another look, or the default look in
 * a custom palette.
 */
@Composable
internal fun AppLookDialog(app: AppInfo, settings: GlowSettings, onSave: (Rule?) -> Unit, onDismiss: () -> Unit) {
    // A rule naming a deleted look reads as the default look, as it glows.
    val saved = settings.appRules[app.packageName]
    val initial = saved.takeUnless { it is Rule.UseLook && settings.look(it.lookId) == null }
    var choice by remember(app.packageName) { mutableStateOf(initial) }
    var palette by remember(app.packageName) {
        mutableStateOf((initial as? Rule.UsePalette)?.palette ?: GlowPalette.Colors(listOf(DEFAULT_GLOW_ARGB)))
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
                    RadioCard(
                        title = look.name,
                        selected = choice == Rule.UseLook(look.id),
                        onSelect = { choice = Rule.UseLook(look.id) },
                    )
                }
                RadioCard(
                    title = stringResource(R.string.app_look_palette_title),
                    selected = choice is Rule.UsePalette,
                    onSelect = { choice = Rule.UsePalette(palette) },
                    subtitle = stringResource(R.string.app_look_palette_desc),
                )
                if (choice is Rule.UsePalette) {
                    PaletteEditor(
                        colors = palette.argb,
                        onPreview = { palette = GlowPalette.Colors(it) },
                        onCommit = { palette = GlowPalette.Colors(it) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(if (choice is Rule.UsePalette) Rule.UsePalette(palette) else choice) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
