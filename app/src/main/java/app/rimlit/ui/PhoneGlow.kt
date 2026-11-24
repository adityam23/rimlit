package app.rimlit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.rimlit.model.EdgeRegion
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSpec
import app.rimlit.model.GlowStyle
import app.rimlit.model.Premade
import app.rimlit.ui.glow.EdgeGlow
import app.rimlit.ui.glow.LocalDeviceConditions
import app.rimlit.ui.glow.playback

/** A phone's height for its width, near enough to every screen for a small preview. */
private const val PHONE_ASPECT = 2f

/**
 * A small phone-shaped screen playing [spec] on its full edge, as a device without a cutout would show it: [moving]
 * (always, so each style shows what it is, as a still Blade would look like Glow) or still. Particles only show for a
 * look with no edge, which would otherwise show nothing: a field in every small tile costs much and shows little.
 */
@Composable
internal fun PhoneGlow(spec: GlowSpec, modifier: Modifier = Modifier, width: Dp = 64.dp, moving: Boolean = true) {
    val shown = remember(spec) {
        spec.copy(region = EdgeRegion.FULL_SCREEN, particles = spec.particles.takeIf { spec.style == GlowStyle.NONE })
    }
    Box(
        modifier = modifier
            .size(width = width, height = width * PHONE_ASPECT)
            .clip(RoundedCornerShape(width / 8))
            .background(Color.Black),
    ) {
        EdgeGlow(
            spec = shown,
            playback = shown.playback(lockScreen = false, LocalDeviceConditions.current).copy(still = !moving),
            preview = true,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** [premade] untouched, in a [PhoneGlow], as these [settings] would play it. */
@Composable
internal fun PremadeGlow(
    premade: Premade,
    settings: GlowSettings,
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
    moving: Boolean = true,
) {
    val spec = remember(premade, settings) { settings.previewSpec(premade) }
    PhoneGlow(spec, modifier, width, moving)
}
