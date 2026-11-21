package app.rimlit.ui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.rimlit.R
import app.rimlit.model.AutoColorSource
import app.rimlit.model.ColorMode
import app.rimlit.model.GlowPalette
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.LookControl
import app.rimlit.ui.components.PaletteEditor
import app.rimlit.ui.components.RadioCard
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.SettingSlider
import kotlin.math.roundToInt

/**
 * How the look is coloured: from the notification, a palette of the user's, or a rainbow. A palette is painted here;
 * tinkering, auto colours also say where they come from and how many to take.
 */
internal fun LazyListScope.colourTab(editor: LookEditorState) {
    val look = editor.look
    val changed = editor.changed
    item(key = "modes") {
        SectionTitle(stringResource(R.string.color_section), changed = LookControl.COLOR_MODE in changed)
    }
    items(ColorMode.entries, key = { "mode_${it.name}" }) { mode ->
        RadioCard(
            title = stringResource(mode.titleRes),
            selected = look.colorMode == mode,
            onSelect = { editor.edit(look.copy(colorMode = mode)) },
            subtitle = stringResource(mode.descRes),
        )
    }
    when (look.colorMode) {
        ColorMode.CUSTOM -> {
            item(key = "palette_title") {
                SectionTitle(
                    stringResource(R.string.editor_palette_section),
                    changed = LookControl.CUSTOM_COLORS in changed,
                )
            }
            item(key = "palette") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    PaletteEditor(
                        colors = look.customColors.argb,
                        onPreview = { colors -> editor.edit(look.copy(customColors = GlowPalette.Colors(colors))) },
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        ColorMode.AUTO -> if (editor.tinkering) autoColourItems(editor, changed)

        ColorMode.RAINBOW -> Unit
    }
}

/** Where auto colours come from, and how many are taken. */
private fun LazyListScope.autoColourItems(editor: LookEditorState, changed: Set<LookControl>) {
    val look = editor.look
    item(key = "auto_title") {
        SectionTitle(stringResource(R.string.auto_colors_section), changed = LookControl.AUTO_SOURCE in changed)
    }
    items(AutoColorSource.entries, key = { "source_${it.name}" }) { source ->
        RadioCard(
            title = stringResource(source.titleRes),
            selected = look.autoColors.source == source,
            onSelect = { editor.edit(look.copy(autoColors = look.autoColors.copy(source = source))) },
            subtitle = stringResource(source.descRes),
        )
    }
    item(key = "auto_count") {
        val range = Ranges.autoColorCount
        SettingSlider(
            label = stringResource(R.string.auto_colors_count_label),
            value = look.autoColors.count.toFloat(),
            range = range.first.toFloat()..range.last.toFloat(),
            valueText = { stringResource(R.string.value_count, it.roundToInt()) },
            changed = LookControl.AUTO_COUNT in changed,
            onPreview = editor.slide { copy(autoColors = autoColors.copy(count = it.roundToInt())) },
            steps = range.last - range.first - 1,
        )
    }
}

private val ColorMode.titleRes
    get() = when (this) {
        ColorMode.AUTO -> R.string.color_auto_title
        ColorMode.CUSTOM -> R.string.color_custom_title
        ColorMode.RAINBOW -> R.string.color_rainbow_title
    }

private val ColorMode.descRes
    get() = when (this) {
        ColorMode.AUTO -> R.string.color_auto_desc
        ColorMode.CUSTOM -> R.string.color_custom_desc
        ColorMode.RAINBOW -> R.string.color_rainbow_desc
    }

private val AutoColorSource.titleRes
    get() = when (this) {
        AutoColorSource.APP_ICON -> R.string.auto_source_app_icon_title
        AutoColorSource.NOTIFICATION_IMAGE -> R.string.auto_source_image_title
    }

private val AutoColorSource.descRes
    get() = when (this) {
        AutoColorSource.APP_ICON -> R.string.auto_source_app_icon_desc
        AutoColorSource.NOTIFICATION_IMAGE -> R.string.auto_source_image_desc
    }
