package app.rimlit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.engine.PreviewTarget
import app.rimlit.model.AutoColorSource
import app.rimlit.model.AutoColors
import app.rimlit.model.BatterySaver
import app.rimlit.model.ColorMode
import app.rimlit.model.EdgeRegion
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.GlowSpec
import app.rimlit.model.GlowStyle
import app.rimlit.permissions.OverlayFallback
import app.rimlit.ui.components.PaletteEditor
import app.rimlit.ui.components.PreviewButtons
import app.rimlit.ui.components.RadioCard
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.SettingSlider
import app.rimlit.ui.components.SettingsList
import app.rimlit.ui.components.SubScreenScaffold
import app.rimlit.ui.components.SwitchCard
import app.rimlit.ui.glow.BATTERY_SAVER_FPS
import app.rimlit.ui.glow.DisplayShape
import app.rimlit.ui.glow.EdgeGlow
import app.rimlit.ui.glow.Playback
import app.rimlit.ui.glow.cutoutBounds
import app.rimlit.ui.glow.playback
import app.rimlit.ui.glow.rememberDeviceConditions
import kotlinx.coroutines.Job
import kotlin.math.roundToInt

private val PreviewBackground = Color.Black

@Composable
fun StyleScreen(viewModel: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val saved by viewModel.settings.collectAsStateWithLifecycle()
    val fallback by viewModel.overlayFallback.collectAsStateWithLifecycle()

    // Everything on this screen renders from the in-memory draft so previews react instantly;
    // DataStore is written once per finished edit (tap, or slider release).
    val editor = remember { SettingsDraft(saved, viewModel::update) }
    LaunchedEffect(saved) { editor.follow(saved) }
    val draft = editor.value

    val view = LocalView.current
    val hasCutout = remember(view) { view.cutoutBounds() != null }
    val density = LocalDensity.current
    // Where the corner slider starts when the user stops matching the display: the display's own radius.
    val displayCornerDp = remember(view, density) {
        with(density) { DisplayShape.of(view).cornerRadii.max().toDp().value }
    }
    // Preview what this device will really show: the renderer falls back to the full edge without a cutout.
    val spec = draft.previewSpec().let { if (hasCutout) it else it.copy(region = EdgeRegion.FULL_SCREEN) }
    // While the screen-off brightness slider is dragged, the preview plays as the lock screen would, to show it.
    var draggingScreenOff by remember { mutableStateOf(false) }
    val playback = spec.playback(lockScreen = draggingScreenOff, rememberDeviceConditions())

    SubScreenScaffold(title = stringResource(R.string.nav_style_title), onBack = onBack, modifier = modifier) {
        SettingsList(it) {
            item {
                LivePreview(
                    spec = spec,
                    playback = playback,
                    onPreviewOnScreen = { viewModel.preview(PreviewTarget.ON_SCREEN, spec) },
                    onPreviewLockScreen = { viewModel.preview(PreviewTarget.LOCK_SCREEN, spec) },
                    fallback = fallback,
                )
            }
            styleSection(draft, spec, playback, editor)
            regionSection(draft, hasCutout, editor)
            cornerRadiusItems(draft, displayCornerDp, editor)
            colorSection(draft, editor)
            tuningSection(draft, editor) { dragging -> draggingScreenOff = dragging }
            batterySaverSection(draft, editor)
            item {
                OutlinedButton(onClick = viewModel::sendTestNotification, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.test_notification_button))
                }
            }
        }
    }
}

private typealias SettingsEdit = (GlowSettings) -> GlowSettings

/**
 * The screen's in-memory settings, changed by [preview] (re-renders only, mid-drag), [persist] (writes only, once the
 * drag ends) or [commit] (both, for a tap). They follow the saved settings only while none of the screen's own edits
 * is unsaved (a write in flight, or a drag not committed yet): the emission of an earlier write would otherwise
 * revert newer edits. Skipping an emission is safe, since once the writes land the draft already holds what they
 * saved, and any later emission is followed again. Main thread only.
 */
private class SettingsDraft(initial: GlowSettings, private val save: (SettingsEdit) -> Job) {
    var value by mutableStateOf(initial)
        private set

    private var writes = 0
    private var dragging = false

    fun preview(edit: SettingsEdit) {
        dragging = true
        value = edit(value)
    }

    fun persist(edit: SettingsEdit) {
        dragging = false
        writes++
        save(edit).invokeOnCompletion { writes-- }
    }

    fun commit(edit: SettingsEdit) {
        value = edit(value)
        persist(edit)
    }

    fun follow(saved: GlowSettings) {
        if (writes == 0 && !dragging) value = saved
    }
}

private fun LazyListScope.styleSection(
    draft: GlowSettings,
    spec: GlowSpec,
    playback: Playback,
    editor: SettingsDraft,
) {
    item { SectionTitle(stringResource(R.string.style_section)) }
    items(GlowStyle.entries, key = { it.name }) { style ->
        RadioCard(
            title = stringResource(style.titleRes),
            selected = draft.glowStyle == style,
            onSelect = { editor.commit { it.copy(glowStyle = style) } },
            subtitle = stringResource(style.descRes),
            leading = {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 64.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(PreviewBackground),
                ) {
                    EdgeGlow(
                        spec = spec.copy(style = style, region = EdgeRegion.FULL_SCREEN, thicknessDp = 2f),
                        // Always moving, so each style shows what it is; a still Blade would look like Glow.
                        playback = playback.copy(still = false),
                        preview = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
        )
    }
}

private fun LazyListScope.regionSection(draft: GlowSettings, hasCutout: Boolean, editor: SettingsDraft) {
    item { SectionTitle(stringResource(R.string.region_section)) }
    items(EdgeRegion.entries, key = { it.name }) { region ->
        val available = region != EdgeRegion.CUTOUT || hasCutout
        RadioCard(
            title = stringResource(region.titleRes),
            selected = draft.region == region,
            onSelect = { editor.commit { it.copy(region = region) } },
            subtitle = stringResource(if (available) region.descRes else R.string.region_cutout_unavailable),
            enabled = available,
        )
    }
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

private fun LazyListScope.colorSection(draft: GlowSettings, editor: SettingsDraft) {
    item { SectionTitle(stringResource(R.string.color_section)) }
    items(ColorMode.entries, key = { it.name }) { mode ->
        RadioCard(
            title = stringResource(mode.titleRes),
            selected = draft.colorMode == mode,
            onSelect = { editor.commit { it.copy(colorMode = mode) } },
            subtitle = stringResource(mode.descRes),
        )
    }
    when (draft.colorMode) {
        ColorMode.AUTO -> autoColorsSection(draft.autoColors, editor)

        ColorMode.CUSTOM -> item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                PaletteEditor(
                    colors = draft.customColors,
                    onPreview = { colors -> editor.preview { it.copy(customColors = colors) } },
                    onCommit = { colors -> editor.persist { it.copy(customColors = colors) } },
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        ColorMode.RAINBOW -> Unit
    }
}

private fun LazyListScope.autoColorsSection(auto: AutoColors, editor: SettingsDraft) {
    item { SectionTitle(stringResource(R.string.auto_colors_section)) }
    items(AutoColorSource.entries, key = { it.name }) { source ->
        RadioCard(
            title = stringResource(source.titleRes),
            selected = auto.source == source,
            onSelect = { editor.commit { it.copy(autoColors = it.autoColors.copy(source = source)) } },
            subtitle = stringResource(source.descRes),
        )
    }
    item {
        val range = Ranges.autoColorCount
        SettingSlider(
            label = stringResource(R.string.auto_colors_count_label),
            value = auto.count.toFloat(),
            range = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            valueText = { stringResource(R.string.value_count, it.roundToInt()) },
            onPreview = { v -> editor.preview { it.copy(autoColors = it.autoColors.copy(count = v.roundToInt())) } },
            onCommit = { v -> editor.persist { it.copy(autoColors = it.autoColors.copy(count = v.roundToInt())) } },
        )
    }
}

private fun LazyListScope.tuningSection(
    draft: GlowSettings,
    editor: SettingsDraft,
    onDraggingScreenOff: (Boolean) -> Unit,
) {
    item { SectionTitle(stringResource(R.string.tuning_section)) }
    item {
        SettingSlider(
            label = stringResource(R.string.thickness_label),
            value = draft.thicknessDp,
            range = Ranges.thicknessDp,
            valueText = { stringResource(R.string.value_dp, it.roundToInt()) },
            onPreview = { v -> editor.preview { it.copy(thicknessDp = v) } },
            onCommit = { v -> editor.persist { it.copy(thicknessDp = v) } },
        )
    }
    item {
        SettingSlider(
            label = stringResource(R.string.glow_width_label),
            value = draft.glowWidthDp,
            range = Ranges.glowWidthDp,
            valueText = { stringResource(R.string.value_dp, it.roundToInt()) },
            onPreview = { v -> editor.preview { it.copy(glowWidthDp = v) } },
            onCommit = { v -> editor.persist { it.copy(glowWidthDp = v) } },
        )
    }
    item {
        SettingSlider(
            label = stringResource(R.string.speed_label),
            value = draft.speed,
            range = Ranges.speed,
            valueText = { stringResource(R.string.value_multiplier, it) },
            onPreview = { v -> editor.preview { it.copy(speed = v) } },
            onCommit = { v -> editor.persist { it.copy(speed = v) } },
        )
    }
    item {
        SettingSlider(
            label = stringResource(R.string.brightness_label),
            value = draft.brightness,
            range = Ranges.brightness,
            valueText = { stringResource(R.string.value_percent, (it * 100).roundToInt()) },
            onPreview = { v -> editor.preview { it.copy(brightness = v) } },
            onCommit = { v -> editor.persist { it.copy(brightness = v) } },
        )
    }
    screenOffBrightnessItems(draft, editor, onDraggingScreenOff)
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
}

/**
 * The lock-screen glow's own brightness: the same as on screen until the user unlinks it. [onDragging] reports its
 * slider's drag, so the live preview can show that brightness meanwhile.
 */
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
                editor.commit { it.copy(screenOffBrightness = if (same) null else it.brightness) }
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

/** Phone-shaped live preview, plus buttons to see it full-size. */
@Composable
private fun LivePreview(
    spec: GlowSpec,
    playback: Playback,
    onPreviewOnScreen: () -> Unit,
    onPreviewLockScreen: () -> Unit,
    fallback: OverlayFallback?,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PreviewBackground),
            ) {
                EdgeGlow(spec = spec, playback = playback, preview = true, modifier = Modifier.fillMaxSize())
            }
            PreviewButtons(onPreviewOnScreen, onPreviewLockScreen, Modifier.weight(1f), fallback)
        }
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

private val GlowStyle.titleRes
    get() = when (this) {
        GlowStyle.BASIC -> R.string.style_basic_title
        GlowStyle.GLOW -> R.string.style_glow_title
        GlowStyle.BLADE -> R.string.style_blade_title
    }

private val GlowStyle.descRes
    get() = when (this) {
        GlowStyle.BASIC -> R.string.style_basic_desc
        GlowStyle.GLOW -> R.string.style_glow_desc
        GlowStyle.BLADE -> R.string.style_blade_desc
    }

private val EdgeRegion.titleRes
    get() = when (this) {
        EdgeRegion.FULL_SCREEN -> R.string.region_full_title
        EdgeRegion.CUTOUT -> R.string.region_cutout_title
    }

private val EdgeRegion.descRes
    get() = when (this) {
        EdgeRegion.FULL_SCREEN -> R.string.region_full_desc
        EdgeRegion.CUTOUT -> R.string.region_cutout_desc
    }

private val ColorMode.titleRes
    get() = when (this) {
        ColorMode.AUTO -> R.string.color_auto_title
        ColorMode.CUSTOM -> R.string.color_custom_title
        ColorMode.RAINBOW -> R.string.color_rainbow_title
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

private val ColorMode.descRes
    get() = when (this) {
        ColorMode.AUTO -> R.string.color_auto_desc
        ColorMode.CUSTOM -> R.string.color_custom_desc
        ColorMode.RAINBOW -> R.string.color_rainbow_desc
    }
