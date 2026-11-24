package app.rimlit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.rimlit.R
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.ui.components.SettingIcon
import app.rimlit.ui.components.SettingSlider
import app.rimlit.ui.components.SwitchCard
import app.rimlit.ui.glow.PreviewPill
import kotlinx.coroutines.Job
import kotlin.math.roundToInt

/** The position sliders move in steps of this many dp, one of them 0, the automatic position. */
private const val PILL_OFFSET_STEP_DP = 4

/**
 * The app pill's settings, as Behavior and setup show them: [screen] lists them where it puts `pillItems`, the
 * pill's switch then, while it is on, its position. While a position slider is dragged the pill shows where it
 * will sit, so this must fill a screen drawn edge to edge. Edits [saved] through its own draft, written by [save].
 */
@Composable
internal fun PillPosition(
    saved: GlowSettings,
    save: (SettingsEdit) -> Job,
    modifier: Modifier = Modifier,
    screen: @Composable (pillItems: LazyListScope.() -> Unit) -> Unit,
) {
    val editor = rememberSettingsDraft(saved, save)
    val draft = editor.value
    var dragging by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        screen { pillItems(draft, editor) { dragging = it } }
        if (dragging) PreviewPill(draft)
    }
}

/** The pill's switch and, while it is on, its two position sliders; [onDragging] says when either is dragged. */
private fun LazyListScope.pillItems(draft: GlowSettings, editor: SettingsDraft, onDragging: (Boolean) -> Unit) {
    item {
        SwitchCard(
            title = stringResource(R.string.show_pill_title),
            checked = draft.showPill,
            onCheckedChange = { show -> editor.commit { it.copy(showPill = show) } },
            subtitle = stringResource(R.string.show_pill_desc),
            leading = { SettingIcon(Icons.Filled.Apps) },
        )
    }
    if (!draft.showPill) return
    item {
        PillOffsetSlider(
            label = stringResource(R.string.pill_horizontal_label),
            value = draft.pillOffsetXDp,
            onDragging = onDragging,
            editor = editor,
        ) { settings, dp -> settings.copy(pillOffsetXDp = dp) }
    }
    item {
        PillOffsetSlider(
            label = stringResource(R.string.pill_vertical_label),
            value = draft.pillOffsetYDp,
            onDragging = onDragging,
            editor = editor,
        ) { settings, dp -> settings.copy(pillOffsetYDp = dp) }
    }
}

/** One of the pill's offsets, centred on 0, set by [set]; the preview ends with it, should it leave mid-drag. */
@Composable
private fun PillOffsetSlider(
    label: String,
    value: Float,
    onDragging: (Boolean) -> Unit,
    editor: SettingsDraft,
    set: (GlowSettings, Float) -> GlowSettings,
) {
    val dragging by rememberUpdatedState(onDragging)
    DisposableEffect(Unit) { onDispose { dragging(false) } }
    val range = Ranges.pillOffsetDp
    SettingSlider(
        label = label,
        value = value,
        range = range,
        valueText = { stringResource(R.string.value_dp, it.roundToInt()) },
        onPreview = { dp ->
            onDragging(true)
            editor.preview { set(it, dp) }
        },
        onCommit = { dp ->
            onDragging(false)
            editor.persist { set(it, dp) }
        },
        steps = ((range.endInclusive - range.start) / PILL_OFFSET_STEP_DP).roundToInt() - 1,
    )
}
