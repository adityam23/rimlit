package app.rimlit.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.rimlit.model.GlowSpec
import app.rimlit.ui.glow.EdgeGlow
import app.rimlit.ui.glow.LocalDeviceConditions
import app.rimlit.ui.glow.playback

/**
 * A preview of [spec] round this screen's real edge, unscaled, as it plays on the lock screen ([lockScreen]) or over
 * an app. Drawn above a screen's content, it takes no touches, so what is under it stays usable.
 */
@Composable
internal fun ScreenGlow(spec: GlowSpec, lockScreen: Boolean, modifier: Modifier = Modifier) {
    EdgeGlow(
        spec = spec,
        playback = spec.playback(lockScreen, LocalDeviceConditions.current),
        preview = true,
        scaled = false,
        modifier = modifier.fillMaxSize(),
    )
}
