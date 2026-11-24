package app.rimlit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.model.EdgeRegion
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.ui.components.SettingSlider
import app.rimlit.ui.components.SettingsList
import app.rimlit.ui.components.SubScreenScaffold
import app.rimlit.ui.components.SwitchCard
import app.rimlit.ui.glow.DisplayShape
import kotlinx.coroutines.Job
import kotlin.math.roundToInt

/** Behavior's screen fit: [DeviceFit] on a screen of its own. */
@Composable
fun DeviceScreen(viewModel: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val saved by viewModel.settings.collectAsStateWithLifecycle()
    DeviceFit(saved, viewModel::update, modifier) { fitItems ->
        SubScreenScaffold(title = stringResource(R.string.nav_device_title), onBack = onBack) { innerPadding ->
            SettingsList(innerPadding) { fitItems() }
        }
    }
}

/**
 * How the glow fits this device: the screen's corners and the camera cutout, each the display's own until the user
 * draws their own, edited from the [saved] settings and persisted by [save]. [screen] lays out the fit's settings, the
 * items it is given; the [FitGuide] runs round the real screen edge above it all, so each change shows where it
 * matters, around the cutout too when there is one. It is a plain outline rather than a look's glow: in setup no look
 * has been picked yet, and a thin line shows the fit more exactly than a glow's halo. Shared by Behavior's screen fit
 * and the guided first launch.
 */
@Composable
internal fun DeviceFit(
    saved: GlowSettings,
    save: (SettingsEdit) -> Job,
    modifier: Modifier = Modifier,
    screen: @Composable (fitItems: LazyListScope.() -> Unit) -> Unit,
) {
    val editor = rememberSettingsDraft(saved, save)
    val draft = editor.value
    val displayCornerDp = rememberDisplayCornerDp()
    val cutoutSeed = rememberCutoutSeed()
    val region = if (cutoutSeed != null) EdgeRegion.CUTOUT_AND_EDGE else EdgeRegion.FULL_SCREEN

    Box(modifier = modifier.fillMaxSize()) {
        screen {
            cornerRadiusItems(draft, displayCornerDp, editor)
            if (cutoutSeed != null) cutoutItems(draft, cutoutSeed, editor)
        }
        FitGuide(draft, region)
    }
}

/** Where the corner slider starts when the user stops matching the display: the display's own radius, in dp. */
@Composable
private fun rememberDisplayCornerDp(): Float {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        with(density) { DisplayShape.of(view).cornerRadii.max().toDp().value }
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
