package app.rimlit.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.rimlit.R
import app.rimlit.model.BatterySaver
import app.rimlit.model.CustomCutout
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.Look
import app.rimlit.ui.components.RadioCard
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.SettingSlider
import app.rimlit.ui.components.SwitchCard
import app.rimlit.ui.glow.BATTERY_SAVER_FPS
import kotlin.math.roundToInt

/**
 * The Style screen's settings every look shares: the camera cutout's shape, corners, screen-off brightness (following
 * [look]'s until unlinked), duration and battery saver. [displayCornerDp] is the display's own corner radius;
 * [cutoutSeed] the detected cutout (`null` without one); [onDraggingScreenOff] reports the screen-off brightness
 * slider's drag.
 */
internal fun LazyListScope.everyLookSection(
    draft: GlowSettings,
    look: Look,
    displayCornerDp: Float,
    cutoutSeed: CustomCutout?,
    editor: SettingsDraft,
    onDraggingScreenOff: (Boolean) -> Unit,
) {
    item { SectionTitle(stringResource(R.string.every_look_section)) }
    // The cutout's shape matters only where a ring is drawn around it: in any look that rings it.
    if (cutoutSeed != null && draft.looks.any { it.region.ringsCutout }) cutoutItems(draft, cutoutSeed, editor)
    cornerRadiusItems(draft, displayCornerDp, editor)
    screenOffBrightnessItems(draft, look, editor, onDraggingScreenOff)
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
    batterySaverSection(draft, editor)
}

/**
 * The screen-edge outline's corners: the display's own until the user unlinks them, then a custom radius
 * starting from the display's own, [displayCornerDp].
 */
private fun LazyListScope.cornerRadiusItems(draft: GlowSettings, displayCornerDp: Float, editor: SettingsDraft) {
    item {
        SwitchCard(
            title = stringResource(R.string.corner_match_title),
            checked = draft.cornerRadiusDp == null,
            onCheckedChange = { match ->
                val custom = displayCornerDp.coerceIn(Ranges.cornerRadiusDp)
                editor.commit { it.copy(cornerRadiusDp = if (match) null else custom) }
            },
            subtitle = stringResource(R.string.corner_match_desc),
        )
    }
    val radius = draft.cornerRadiusDp ?: return
    item {
        SettingSlider(
            label = stringResource(R.string.corner_radius_label),
            value = radius,
            range = Ranges.cornerRadiusDp,
            valueText = { stringResource(R.string.value_dp, it.roundToInt()) },
            onPreview = { v -> editor.preview { it.copy(cornerRadiusDp = v) } },
            onCommit = { v -> editor.persist { it.copy(cornerRadiusDp = v) } },
        )
    }
}

/**
 * The lock-screen glow's own brightness: the same as on screen until the user unlinks it, then starting from
 * [look]'s. [onDragging] reports its slider's drag, so the live preview can show that brightness meanwhile.
 */
private fun LazyListScope.screenOffBrightnessItems(
    draft: GlowSettings,
    look: Look,
    editor: SettingsDraft,
    onDragging: (Boolean) -> Unit,
) {
    item {
        SwitchCard(
            title = stringResource(R.string.screen_off_brightness_same_title),
            checked = draft.screenOffBrightness == null,
            onCheckedChange = { same ->
                editor.commit { it.copy(screenOffBrightness = if (same) null else look.brightness) }
            },
            subtitle = stringResource(R.string.screen_off_brightness_same_desc),
        )
    }
    val screenOff = draft.screenOffBrightness ?: return
    item {
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
