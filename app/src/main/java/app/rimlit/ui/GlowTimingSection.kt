package app.rimlit.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.rimlit.R
import app.rimlit.model.BatterySaver
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.ui.components.RadioCard
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.SettingSlider
import app.rimlit.ui.components.SwitchCard
import app.rimlit.ui.glow.BATTERY_SAVER_FPS
import kotlin.math.roundToInt

/**
 * Behavior's settings every glow shares, whatever its look: how long it holds, its screen-off brightness, battery.
 * [onDraggingScreenOff] says when the screen-off brightness slider starts and stops being dragged, to preview it.
 */
internal fun LazyListScope.glowTimingSection(
    draft: GlowSettings,
    editor: SettingsDraft,
    onDraggingScreenOff: (Boolean) -> Unit,
) {
    item { SectionTitle(stringResource(R.string.every_glow_section)) }
    item {
        SettingSlider(
            label = stringResource(R.string.duration_label),
            value = draft.durationMs.toFloat(),
            range = Ranges.durationMs.first.toFloat()..Ranges.durationMs.last.toFloat(),
            valueText = { stringResource(R.string.value_seconds, it / 1000f) },
            onPreview = { v -> editor.preview { it.copy(durationMs = v.toLong()) } },
            onCommit = { v -> editor.persist { it.copy(durationMs = v.toLong()) } },
        )
    }
    screenOffBrightnessItems(draft, editor, onDraggingScreenOff)
    batterySaverSection(draft, editor)
}

/** The lock-screen glow's own brightness: each look's own until the user unlinks it, then starting from the default. */
private fun LazyListScope.screenOffBrightnessItems(
    draft: GlowSettings,
    editor: SettingsDraft,
    onDragging: (Boolean) -> Unit,
) {
    item {
        SwitchCard(
            title = stringResource(R.string.screen_off_brightness_same_title),
            checked = draft.screenOffBrightness == null,
            onCheckedChange = { same ->
                editor.commit { it.copy(screenOffBrightness = if (same) null else it.defaultLook.brightness) }
            },
            subtitle = stringResource(R.string.screen_off_brightness_same_desc),
        )
    }
    val screenOff = draft.screenOffBrightness ?: return
    item {
        // The preview ends with the slider too, should it leave mid-drag (relinked, scrolled away): never left on.
        val dragging by rememberUpdatedState(onDragging)
        DisposableEffect(Unit) { onDispose { dragging(false) } }
        SettingSlider(
            label = stringResource(R.string.screen_off_brightness_label),
            value = screenOff,
            range = Ranges.brightness,
            valueText = { stringResource(R.string.value_percent, (it * 100).roundToInt()) },
            onPreview = { v ->
                onDragging(true)
                editor.preview { it.copy(screenOffBrightness = v) }
            },
            onCommit = { v ->
                onDragging(false)
                editor.persist { it.copy(screenOffBrightness = v) }
            },
        )
    }
}

private fun LazyListScope.batterySaverSection(draft: GlowSettings, editor: SettingsDraft) {
    item { SectionTitle(stringResource(R.string.battery_saver_section)) }
    items(BatterySaver.entries, key = { it.name }) { saver ->
        RadioCard(
            title = stringResource(saver.titleRes),
            selected = draft.batterySaver == saver,
            onSelect = { editor.commit { it.copy(batterySaver = saver) } },
            subtitle = saver.description(),
        )
    }
}

private val BatterySaver.titleRes
    get() = when (this) {
        BatterySaver.OFF -> R.string.battery_saver_off_title
        BatterySaver.WITH_SYSTEM -> R.string.battery_saver_system_title
        BatterySaver.ALWAYS -> R.string.battery_saver_always_title
    }

@Composable
@ReadOnlyComposable
private fun BatterySaver.description(): String = when (this) {
    BatterySaver.OFF -> stringResource(R.string.battery_saver_off_desc)

    BatterySaver.WITH_SYSTEM ->
        pluralStringResource(R.plurals.battery_saver_system_desc, BATTERY_SAVER_FPS, BATTERY_SAVER_FPS)

    BatterySaver.ALWAYS ->
        pluralStringResource(R.plurals.battery_saver_always_desc, BATTERY_SAVER_FPS, BATTERY_SAVER_FPS)
}
