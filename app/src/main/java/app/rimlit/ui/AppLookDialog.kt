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
    val initial = settings.ruleFor(app.packageName)
    var choice by remember(app.packageName) { mutableStateOf(Choice.of(initial)) }
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
                    selected = choice == Choice.Default,
                    onSelect = { choice = Choice.Default },
                    subtitle = settings.defaultLook.name,
                )
                for (look in settings.looks.filter { it.id != settings.defaultLookId }) {
                    RadioCard(
                        title = look.name,
                        selected = choice == Choice.Named(look.id),
                        onSelect = { choice = Choice.Named(look.id) },
                    )
                }
                RadioCard(
                    title = stringResource(R.string.app_look_palette_title),
                    selected = choice == Choice.Palette,
                    onSelect = { choice = Choice.Palette },
                    subtitle = stringResource(R.string.app_look_palette_desc),
                )
                if (choice == Choice.Palette) {
                    PaletteEditor(
                        colors = palette.argb,
                        onPreview = { palette = GlowPalette.Colors(it) },
                        onCommit = { palette = GlowPalette.Colors(it) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(choice.rule(palette)) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** Which of the dialog's options is selected; the palette being edited is kept apart until saved. */
private sealed interface Choice {
    data object Default : Choice

    data class Named(val lookId: String) : Choice

    data object Palette : Choice

    /** The rule this choice saves, painting with [palette] when it is [Palette]; `null` for the default look. */
    fun rule(palette: GlowPalette.Colors): Rule? = when (this) {
        Default -> null
        is Named -> Rule.UseLook(lookId)
        Palette -> Rule.UsePalette(palette)
    }

    companion object {
        fun of(rule: Rule?): Choice = when (rule) {
            null -> Default
            is Rule.UseLook -> Named(rule.lookId)
            is Rule.UsePalette -> Palette
        }
    }
}
