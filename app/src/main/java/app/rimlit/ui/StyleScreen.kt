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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.engine.PreviewTarget
import app.rimlit.model.AutoColorSource
import app.rimlit.model.ColorMode
import app.rimlit.model.EdgeRegion
import app.rimlit.model.GlowPalette
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.GlowSpec
import app.rimlit.model.GlowStyle
import app.rimlit.model.Look
import app.rimlit.permissions.OverlayFallback
import app.rimlit.ui.components.PaletteEditor
import app.rimlit.ui.components.PreviewButtons
import app.rimlit.ui.components.RadioCard
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.SettingSlider
import app.rimlit.ui.components.SettingsList
import app.rimlit.ui.components.SubScreenScaffold
import app.rimlit.ui.glow.DisplayShape
import app.rimlit.ui.glow.EdgeGlow
import app.rimlit.ui.glow.Playback
import app.rimlit.ui.glow.playback
import app.rimlit.ui.glow.rememberDeviceConditions
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
    // The look this screen edits: the default one until another is picked, and again once that one is deleted.
    var editingId by rememberSaveable { mutableStateOf(saved.defaultLookId) }
    val look = draft.look(editingId) ?: draft.defaultLook
    // The open look dialog, kept across configuration changes like the look being edited.
    var lookAction by rememberSaveable(stateSaver = LookAction.Saver) { mutableStateOf<LookAction?>(null) }

    val view = LocalView.current
    val density = LocalDensity.current
    // Where the corner slider starts when the user stops matching the display: the display's own radius.
    val displayCornerDp = remember(view, density) {
        with(density) { DisplayShape.of(view).cornerRadii.max().toDp().value }
    }
    val cutoutSeed = rememberCutoutSeed()
    val hasCutout = cutoutSeed != null
    // Preview what this device will really show: the renderer falls back to the full edge without a cutout.
    val spec = draft.previewSpec(look).let { if (hasCutout) it else it.copy(region = EdgeRegion.FULL_SCREEN) }
    // While the screen-off brightness slider is dragged, the preview plays as the lock screen would, to show it.
    var draggingScreenOff by remember { mutableStateOf(false) }
    val playback = spec.playback(lockScreen = draggingScreenOff, rememberDeviceConditions())

    SubScreenScaffold(title = stringResource(R.string.nav_style_title), onBack = onBack, modifier = modifier) {
        SettingsList(it) {
            lookPickerSection(draft, look, onEdit = { picked -> editingId = picked.id }) { lookAction = it }
            item {
                LivePreview(
                    spec = spec,
                    playback = playback,
                    onPreviewOnScreen = { viewModel.preview(PreviewTarget.ON_SCREEN, spec) },
                    onPreviewLockScreen = { viewModel.preview(PreviewTarget.LOCK_SCREEN, spec) },
                    fallback = fallback,
                )
            }
            // The picked look's own sections, then the settings every look shares.
            styleSection(look, spec, playback, editor)
            particlesSection(look, editor)
            regionSection(look, hasCutout, editor)
            colorSection(look, editor)
            tuningSection(look, editor)
            everyLookSection(draft, look, displayCornerDp, cutoutSeed, editor) { dragging ->
                draggingScreenOff = dragging
            }
            item {
                OutlinedButton(onClick = viewModel::sendTestNotification, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.test_notification_button))
                }
            }
        }
    }

    // Restored before the settings load, or for a look that no longer exists, the dialog waits unseen; the next action
    // replaces it. Clearing it here would lose a restored dialog to the placeholder settings shown before DataStore.
    val action = lookAction ?: return
    val target = draft.look(action.lookId) ?: return
    LookActionDialog(
        action = action,
        look = target,
        settings = draft,
        onDone = { edit, editNext ->
            editor.commit(edit)
            editingId = editNext
            lookAction = null
        },
        onDismiss = { lookAction = null },
    )
}

/** The edge styles; no edge ([GlowStyle.NONE]) only with particles, so a look always shows something. */
private fun LazyListScope.styleSection(look: Look, spec: GlowSpec, playback: Playback, editor: SettingsDraft) {
    item { SectionTitle(stringResource(R.string.style_section)) }
    items(GlowStyle.entries, key = { it.name }) { style ->
        val available = style != GlowStyle.NONE || look.particles != null
        RadioCard(
            title = stringResource(style.titleRes),
            selected = look.style == style,
            onSelect = { editor.commit(look.edit { it.copy(style = style) }) },
            subtitle = stringResource(if (available) style.descRes else R.string.style_none_unavailable),
            enabled = available,
            leading = {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 64.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(PreviewBackground),
                ) {
                    EdgeGlow(
                        // The edge alone: a particle field in every 40 × 64 dp row would cost much, show little.
                        spec = spec.copy(
                            style = style,
                            region = EdgeRegion.FULL_SCREEN,
                            thicknessDp = 2f,
                            particles = null,
                        ),
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

private fun LazyListScope.regionSection(look: Look, hasCutout: Boolean, editor: SettingsDraft) {
    item { SectionTitle(stringResource(R.string.region_section)) }
    items(EdgeRegion.entries, key = { it.name }) { region ->
        val available = !region.ringsCutout || hasCutout
        RadioCard(
            title = stringResource(region.titleRes),
            selected = look.region == region,
            onSelect = { editor.commit(look.edit { it.copy(region = region) }) },
            subtitle = stringResource(if (available) region.descRes else R.string.region_cutout_unavailable),
            enabled = available,
        )
    }
}

private fun LazyListScope.colorSection(look: Look, editor: SettingsDraft) {
    item { SectionTitle(stringResource(R.string.color_section)) }
    items(ColorMode.entries, key = { it.name }) { mode ->
        RadioCard(
            title = stringResource(mode.titleRes),
            selected = look.colorMode == mode,
            onSelect = { editor.commit(look.edit { it.copy(colorMode = mode) }) },
            subtitle = stringResource(mode.descRes),
        )
    }
    when (look.colorMode) {
        ColorMode.AUTO -> autoColorsSection(look, editor)

        ColorMode.CUSTOM -> item {
            val painted = { colors: List<Int> -> look.edit { it.copy(customColors = GlowPalette.Colors(colors)) } }
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                PaletteEditor(
                    colors = look.customColors.argb,
                    onPreview = { colors -> editor.preview(painted(colors)) },
                    onCommit = { colors -> editor.persist(painted(colors)) },
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        ColorMode.RAINBOW -> Unit
    }
}

private fun LazyListScope.autoColorsSection(look: Look, editor: SettingsDraft) {
    val auto = look.autoColors
    fun count(v: Float) = look.edit { it.copy(autoColors = it.autoColors.copy(count = v.roundToInt())) }
    item { SectionTitle(stringResource(R.string.auto_colors_section)) }
    items(AutoColorSource.entries, key = { it.name }) { source ->
        RadioCard(
            title = stringResource(source.titleRes),
            selected = auto.source == source,
            onSelect = { editor.commit(look.edit { it.copy(autoColors = it.autoColors.copy(source = source)) }) },
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
            onPreview = { v -> editor.preview(count(v)) },
            onCommit = { v -> editor.persist(count(v)) },
        )
    }
}

private fun LazyListScope.tuningSection(look: Look, editor: SettingsDraft) {
    item { SectionTitle(stringResource(R.string.tuning_section)) }
    item {
        SettingSlider(
            label = stringResource(R.string.thickness_label),
            value = look.thicknessDp,
            range = Ranges.thicknessDp,
            valueText = { stringResource(R.string.value_dp, it.roundToInt()) },
            onPreview = { v -> editor.preview(look.edit { it.copy(thicknessDp = v) }) },
            onCommit = { v -> editor.persist(look.edit { it.copy(thicknessDp = v) }) },
        )
    }
    item {
        SettingSlider(
            label = stringResource(R.string.glow_width_label),
            value = look.glowWidthDp,
            range = Ranges.glowWidthDp,
            valueText = { stringResource(R.string.value_dp, it.roundToInt()) },
            onPreview = { v -> editor.preview(look.edit { it.copy(glowWidthDp = v) }) },
            onCommit = { v -> editor.persist(look.edit { it.copy(glowWidthDp = v) }) },
        )
    }
    item {
        SettingSlider(
            label = stringResource(R.string.speed_label),
            value = look.speed,
            range = Ranges.speed,
            valueText = { stringResource(R.string.value_multiplier, it) },
            onPreview = { v -> editor.preview(look.edit { it.copy(speed = v) }) },
            onCommit = { v -> editor.persist(look.edit { it.copy(speed = v) }) },
        )
    }
    item {
        SettingSlider(
            label = stringResource(R.string.brightness_label),
            value = look.brightness,
            range = Ranges.brightness,
            valueText = { stringResource(R.string.value_percent, (it * 100).roundToInt()) },
            onPreview = { v -> editor.preview(look.edit { it.copy(brightness = v) }) },
            onCommit = { v -> editor.persist(look.edit { it.copy(brightness = v) }) },
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

private val GlowStyle.titleRes
    get() = when (this) {
        GlowStyle.BASIC -> R.string.style_basic_title
        GlowStyle.GLOW -> R.string.style_glow_title
        GlowStyle.BLADE -> R.string.style_blade_title
        GlowStyle.WAVE -> R.string.style_wave_title
        GlowStyle.GLITTER -> R.string.style_glitter_title
        GlowStyle.ECHO -> R.string.style_echo_title
        GlowStyle.LOOP -> R.string.style_loop_title
        GlowStyle.MIRROR -> R.string.style_mirror_title
        GlowStyle.BOOMERANG -> R.string.style_boomerang_title
        GlowStyle.NONE -> R.string.style_none_title
    }

private val GlowStyle.descRes
    get() = when (this) {
        GlowStyle.BASIC -> R.string.style_basic_desc
        GlowStyle.GLOW -> R.string.style_glow_desc
        GlowStyle.BLADE -> R.string.style_blade_desc
        GlowStyle.WAVE -> R.string.style_wave_desc
        GlowStyle.GLITTER -> R.string.style_glitter_desc
        GlowStyle.ECHO -> R.string.style_echo_desc
        GlowStyle.LOOP -> R.string.style_loop_desc
        GlowStyle.MIRROR -> R.string.style_mirror_desc
        GlowStyle.BOOMERANG -> R.string.style_boomerang_desc
        GlowStyle.NONE -> R.string.style_none_desc
    }

private val EdgeRegion.titleRes
    get() = when (this) {
        EdgeRegion.FULL_SCREEN -> R.string.region_full_title
        EdgeRegion.CUTOUT -> R.string.region_cutout_title
        EdgeRegion.CUTOUT_AND_EDGE -> R.string.region_both_title
    }

private val EdgeRegion.descRes
    get() = when (this) {
        EdgeRegion.FULL_SCREEN -> R.string.region_full_desc
        EdgeRegion.CUTOUT -> R.string.region_cutout_desc
        EdgeRegion.CUTOUT_AND_EDGE -> R.string.region_both_desc
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
