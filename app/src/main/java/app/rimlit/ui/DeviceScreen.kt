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
import app.rimlit.model.GlowStyle
import app.rimlit.ui.components.SettingSlider
import app.rimlit.ui.components.SettingsList
import app.rimlit.ui.components.SubScreenScaffold
import app.rimlit.ui.components.SwitchCard
import app.rimlit.ui.glow.DisplayShape
import kotlin.math.roundToInt

/**
 * How the glow fits this device: the screen's corners and the camera cutout, each the display's own until the user
 * draws their own. The default look glows round the real screen edge above it all, so each change shows where it
 * matters; around the cutout too, when there is one.
 */
@Composable
fun DeviceScreen(viewModel: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val saved by viewModel.settings.collectAsStateWithLifecycle()
    val editor = rememberSettingsDraft(saved, viewModel::update)
    val draft = editor.value
    val displayCornerDp = rememberDisplayCornerDp()
    val cutoutSeed = rememberCutoutSeed()
    val region = if (cutoutSeed != null) EdgeRegion.CUTOUT_AND_EDGE else EdgeRegion.FULL_SCREEN
    // The edge alone, always one: a particles-only default look shows its premade's, so the fit can be seen.
    val look = draft.defaultLook
    val edge = if (look.style == GlowStyle.NONE) look.copy(style = look.basedOn.style) else look
    val spec = draft.previewSpec(edge).copy(region = region, particles = null)

    Box(modifier = modifier.fillMaxSize()) {
        SubScreenScaffold(title = stringResource(R.string.nav_device_title), onBack = onBack) { innerPadding ->
            SettingsList(innerPadding) {
                cornerRadiusItems(draft, displayCornerDp, editor)
                if (cutoutSeed != null) cutoutItems(draft, cutoutSeed, editor)
            }
        }
        ScreenGlow(spec, lockScreen = false)
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
