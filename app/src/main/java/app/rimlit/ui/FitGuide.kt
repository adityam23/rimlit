package app.rimlit.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import app.rimlit.model.ColorMode
import app.rimlit.model.EdgeRegion
import app.rimlit.model.GlowPalette
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowStyle
import app.rimlit.model.Look
import app.rimlit.ui.glow.EdgeGlow
import app.rimlit.ui.glow.Playback

/** The guide's line, in dp: as thin as an outline goes. */
private const val GUIDE_THICKNESS_DP = 1f

/**
 * Where a glow runs on this screen with these [settings]' corners and cutout, round the [region]: a thin, still,
 * neutral outline round the real screen edge, no look's glow. Drawn above a screen's content, it takes no touches.
 */
@Composable
internal fun FitGuide(settings: GlowSettings, region: EdgeRegion, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline.toArgb()
    val guide = Look(
        style = GlowStyle.BASIC,
        colorMode = ColorMode.CUSTOM,
        customColors = GlowPalette.Colors(listOf(outline)),
        region = region,
        thicknessDp = GUIDE_THICKNESS_DP,
        glowWidthDp = 0f,
        particles = null,
    )
    val spec = settings.previewSpec(guide)
    EdgeGlow(
        spec = spec,
        playback = Playback(brightness = 1f, still = true, maxFps = null, particleShare = 0f),
        preview = true,
        scaled = false,
        modifier = modifier.fillMaxSize(),
    )
}
