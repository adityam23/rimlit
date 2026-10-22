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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.engine.PreviewTarget
import app.rimlit.model.ColorMode
import app.rimlit.model.EdgeRegion
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.GlowSpec
import app.rimlit.model.GlowStyle
import app.rimlit.previewSpec
import app.rimlit.ui.components.PaletteEditor
import app.rimlit.ui.components.PreviewButtons
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.SettingCard
import app.rimlit.ui.components.SettingSlider
import app.rimlit.ui.components.SettingsList
import app.rimlit.ui.components.SubScreenScaffold
import app.rimlit.ui.glow.EdgeGlow
import app.rimlit.ui.glow.cutoutBounds
import kotlin.math.roundToInt

private val PreviewBackground = Color(0xFF000000)

@Composable
fun StyleScreen(viewModel: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val saved by viewModel.settings.collectAsStateWithLifecycle()

    // Everything on this screen renders from the in-memory draft so previews react instantly;
    // DataStore is written once per finished edit (tap, or slider release).
    var draft by remember { mutableStateOf(saved) }
    LaunchedEffect(saved) { draft = saved }
    val editor = DraftEditor(preview = { draft = it(draft) }, persist = viewModel::update)

    val view = LocalView.current
    val hasCutout = remember(view) { view.cutoutBounds() != null }
    // Preview what this device will really show: the renderer falls back to the full edge without a cutout.
    val spec = draft.previewSpec().let { if (hasCutout) it else it.copy(region = EdgeRegion.FULL_SCREEN) }

    SubScreenScaffold(title = stringResource(R.string.nav_style_title), onBack = onBack, modifier = modifier) {
        SettingsList(it) {
            item {
                LivePreview(
                    spec = spec,
                    onPreviewOnScreen = { viewModel.preview(PreviewTarget.ON_SCREEN, spec) },
                    onPreviewLockScreen = { viewModel.preview(PreviewTarget.LOCK_SCREEN, spec) },
                )
            }
            styleSection(draft, spec, editor)
            regionSection(draft, hasCutout, editor)
            colorSection(draft, editor)
            tuningSection(draft, editor)
            item {
                OutlinedButton(onClick = viewModel::sendTestNotification, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.preview_button))
                }
            }
        }
    }
}

private typealias SettingsEdit = (GlowSettings) -> GlowSettings

/** Changes to the screen's draft: [preview] only re-renders, [persist] only writes, [commit] does both. */
private class DraftEditor(val preview: (SettingsEdit) -> Unit, val persist: (SettingsEdit) -> Unit) {
    fun commit(edit: SettingsEdit) {
        preview(edit)
        persist(edit)
    }
}

private fun LazyListScope.styleSection(draft: GlowSettings, spec: GlowSpec, editor: DraftEditor) {
    item { SectionTitle(stringResource(R.string.style_section)) }
    items(GlowStyle.entries, key = { it.name }) { style ->
        val select = { editor.commit { it.copy(glowStyle = style) } }
        SettingCard(
            title = stringResource(style.titleRes),
            subtitle = stringResource(style.descRes),
            onClick = select,
            leading = {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 64.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(PreviewBackground),
                ) {
                    EdgeGlow(
                        spec = spec.copy(style = style, region = EdgeRegion.FULL_SCREEN, thicknessDp = 2f),
                        preview = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
            trailing = { RadioButton(selected = draft.glowStyle == style, onClick = select) },
        )
    }
}

private fun LazyListScope.regionSection(draft: GlowSettings, hasCutout: Boolean, editor: DraftEditor) {
    item { SectionTitle(stringResource(R.string.region_section)) }
    items(EdgeRegion.entries, key = { it.name }) { region ->
        val available = region != EdgeRegion.CUTOUT || hasCutout
        val select = { editor.commit { it.copy(region = region) } }
        SettingCard(
            title = stringResource(region.titleRes),
            subtitle = stringResource(if (available) region.descRes else R.string.region_cutout_unavailable),
            enabled = available,
            onClick = select,
            trailing = { RadioButton(selected = draft.region == region, enabled = available, onClick = select) },
        )
    }
}

private fun LazyListScope.colorSection(draft: GlowSettings, editor: DraftEditor) {
    item { SectionTitle(stringResource(R.string.color_section)) }
    items(ColorMode.entries, key = { it.name }) { mode ->
        val select = { editor.commit { it.copy(colorMode = mode) } }
        SettingCard(
            title = stringResource(mode.titleRes),
            subtitle = stringResource(mode.descRes),
            onClick = select,
            trailing = { RadioButton(selected = draft.colorMode == mode, onClick = select) },
        )
    }
    if (draft.colorMode == ColorMode.CUSTOM) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                PaletteEditor(
                    colors = draft.customColors,
                    onPreview = { colors -> editor.preview { it.copy(customColors = colors) } },
                    onCommit = { colors -> editor.persist { it.copy(customColors = colors) } },
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

private fun LazyListScope.tuningSection(draft: GlowSettings, editor: DraftEditor) {
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
    item {
        SettingSlider(
            label = stringResource(R.string.duration_section),
            value = draft.durationMs.toFloat(),
            range = Ranges.durationMs.first.toFloat()..Ranges.durationMs.last.toFloat(),
            valueText = { stringResource(R.string.value_seconds, it / 1000f) },
            onPreview = { v -> editor.preview { it.copy(durationMs = v.toLong()) } },
            onCommit = { v -> editor.persist { it.copy(durationMs = v.toLong()) } },
        )
    }
}

/** Phone-shaped live preview, plus buttons to see it full-size. */
@Composable
private fun LivePreview(spec: GlowSpec, onPreviewOnScreen: () -> Unit, onPreviewLockScreen: () -> Unit) {
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
                EdgeGlow(spec = spec, preview = true, modifier = Modifier.fillMaxSize())
            }
            PreviewButtons(onPreviewOnScreen, onPreviewLockScreen, modifier = Modifier.weight(1f))
        }
    }
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

private val ColorMode.descRes
    get() = when (this) {
        ColorMode.AUTO -> R.string.color_auto_desc
        ColorMode.CUSTOM -> R.string.color_custom_desc
        ColorMode.RAINBOW -> R.string.color_rainbow_desc
    }
