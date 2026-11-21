package app.rimlit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.rimlit.model.EdgeRegion
import app.rimlit.model.GlowSpec
import app.rimlit.ui.glow.EdgeGlow
import app.rimlit.ui.glow.playback
import app.rimlit.ui.glow.rememberDeviceConditions

/** A phone's height for its width, near enough to every screen for a small preview. */
private const val PHONE_ASPECT = 2f

/**
 * A small phone-shaped screen playing [spec]'s edge, always moving so each style shows what it is (a still Blade
 * would look like Glow). Particles are left out: a field in every small tile would cost much and show little. Drawn
 * on the full screen edge, as a device without a cutout would show it.
 */
@Composable
internal fun PhoneGlow(spec: GlowSpec, modifier: Modifier = Modifier, width: Dp = 64.dp) {
    val edge = spec.copy(region = EdgeRegion.FULL_SCREEN, particles = null)
    Box(
        modifier = modifier
            .size(width = width, height = width * PHONE_ASPECT)
            .clip(RoundedCornerShape(width / 8))
            .background(Color.Black),
    ) {
        EdgeGlow(
            spec = edge,
            playback = edge.playback(lockScreen = false, rememberDeviceConditions()).copy(still = false),
            preview = true,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
