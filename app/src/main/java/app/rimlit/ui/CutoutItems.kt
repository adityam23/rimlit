package app.rimlit.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import app.rimlit.R
import app.rimlit.model.CustomCutout
import app.rimlit.model.CutoutShape
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.ui.components.RadioCard
import app.rimlit.ui.components.SettingSlider
import app.rimlit.ui.components.SwitchCard
import app.rimlit.ui.glow.displayCutout
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where the cutout sliders start when the user stops matching the camera: a circle the size of the cutout this
 * window detects, or `null` when the display has none.
 */
@Composable
internal fun rememberCutoutSeed(): CustomCutout? {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        view.displayCutout()?.let { cutout ->
            val sizePx = min(cutout.bounds.width(), cutout.bounds.height())
            val sizeDp = with(density) { sizePx.toDp().value }.coerceIn(Ranges.cutoutSizeDp)
            CustomCutout(widthDp = sizeDp, heightDp = sizeDp, radiusDp = sizeDp / 2f)
        }
    }
}

/**
 * The camera cutout the ring hugs: the detected one until the user unlinks it, then a shape they draw, starting
 * from [seed]. Only the sliders the chosen shape reads are shown.
 */
internal fun LazyListScope.cutoutItems(draft: GlowSettings, seed: CustomCutout, editor: SettingsDraft) {
    item {
        SwitchCard(
            title = stringResource(R.string.cutout_match_title),
            checked = draft.customCutout == null,
            onCheckedChange = { match -> editor.commit { it.copy(customCutout = if (match) null else seed) } },
            subtitle = stringResource(R.string.cutout_match_desc),
        )
    }
    val cutout = draft.customCutout ?: return
    items(CutoutShape.entries, key = { "cutout_${it.name}" }) { shape ->
        RadioCard(
            title = stringResource(shape.titleRes),
            selected = cutout.shape == shape,
            onSelect = { editor.commit { it.editCutout { c -> c.copy(shape = shape) } } },
            subtitle = stringResource(shape.descRes),
        )
    }
    val shape = cutout.shape
    val widthLabel = if (shape.usesHeight) R.string.cutout_width_label else R.string.cutout_size_label
    cutoutSlider(widthLabel, cutout.widthDp, Ranges.cutoutSizeDp, editor) { c, v -> c.copy(widthDp = v) }
    if (shape.usesHeight) {
        cutoutSlider(R.string.cutout_height_label, cutout.heightDp, Ranges.cutoutSizeDp, editor) { c, v ->
            c.copy(heightDp = v)
        }
    }
    if (shape.usesRadius) {
        cutoutSlider(R.string.corner_radius_label, cutout.radiusDp, Ranges.cutoutRadiusDp, editor) { c, v ->
            c.copy(radiusDp = v)
        }
    }
    cutoutSlider(R.string.cutout_offset_x_label, cutout.offsetXDp, Ranges.cutoutOffsetDp, editor) { c, v ->
        c.copy(offsetXDp = v)
    }
    if (!shape.hangsFromEdge) {
        cutoutSlider(R.string.cutout_offset_y_label, cutout.offsetYDp, Ranges.cutoutOffsetDp, editor) { c, v ->
            c.copy(offsetYDp = v)
        }
    }
}

/** One dp slider of the hand-drawn cutout, setting its value with [set]. */
private fun LazyListScope.cutoutSlider(
    @StringRes label: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    editor: SettingsDraft,
    set: (CustomCutout, Float) -> CustomCutout,
) {
    // Keyed, so a slider's drag state never moves to another one when a shape change shows or hides sliders.
    item(key = "cutout_slider_$label") {
        SettingSlider(
            label = stringResource(label),
            value = value,
            range = range,
            valueText = { stringResource(R.string.value_dp, it.roundToInt()) },
            onPreview = { v -> editor.preview { it.editCutout { c -> set(c, v) } } },
            onCommit = { v -> editor.persist { it.editCutout { c -> set(c, v) } } },
        )
    }
}

/** These settings with their hand-drawn cutout changed by [edit]; unchanged while the cutout is matched. */
private fun GlowSettings.editCutout(edit: (CustomCutout) -> CustomCutout) = copy(customCutout = customCutout?.let(edit))

private val CutoutShape.titleRes
    get() = when (this) {
        CutoutShape.CIRCLE -> R.string.cutout_shape_circle_title
        CutoutShape.PILL -> R.string.cutout_shape_pill_title
        CutoutShape.TEARDROP -> R.string.cutout_shape_teardrop_title
        CutoutShape.NOTCH -> R.string.cutout_shape_notch_title
    }

private val CutoutShape.descRes
    get() = when (this) {
        CutoutShape.CIRCLE -> R.string.cutout_shape_circle_desc
        CutoutShape.PILL -> R.string.cutout_shape_pill_desc
        CutoutShape.TEARDROP -> R.string.cutout_shape_teardrop_desc
        CutoutShape.NOTCH -> R.string.cutout_shape_notch_desc
    }
