package app.rimlit.ui.editor

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.res.stringResource
import app.rimlit.R
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.LookControl
import app.rimlit.ui.components.SettingSlider
import kotlin.math.roundToInt

/** The edge's thickness, brightness and speed; tinkering, its glow's width too. Each marked once changed. */
internal fun LazyListScope.tuningTab(editor: LookEditorState) {
    val look = editor.look
    val changed = editor.changed
    item(key = "thickness") {
        SettingSlider(
            label = stringResource(R.string.thickness_label),
            value = look.thicknessDp,
            range = Ranges.thicknessDp,
            valueText = { stringResource(R.string.value_dp, it.roundToInt()) },
            changed = LookControl.THICKNESS in changed,
            onPreview = editor.slide { copy(thicknessDp = it) },
        )
    }
    item(key = "brightness") {
        SettingSlider(
            label = stringResource(R.string.brightness_label),
            value = look.brightness,
            range = Ranges.brightness,
            valueText = { stringResource(R.string.value_percent, (it * 100).roundToInt()) },
            changed = LookControl.BRIGHTNESS in changed,
            onPreview = editor.slide { copy(brightness = it) },
        )
    }
    item(key = "speed") {
        SettingSlider(
            label = stringResource(R.string.speed_label),
            value = look.speed,
            range = Ranges.speed,
            valueText = { stringResource(R.string.value_multiplier, it) },
            changed = LookControl.SPEED in changed,
            onPreview = editor.slide { copy(speed = it) },
        )
    }
    if (!editor.tinkering) return
    item(key = "glow_width") {
        SettingSlider(
            label = stringResource(R.string.glow_width_label),
            value = look.glowWidthDp,
            range = Ranges.glowWidthDp,
            valueText = { stringResource(R.string.value_dp, it.roundToInt()) },
            changed = LookControl.GLOW_WIDTH in changed,
            onPreview = editor.slide { copy(glowWidthDp = it) },
        )
    }
}
