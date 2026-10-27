package app.rimlit.ui.glow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.engine.FADE_IN_MS
import app.rimlit.engine.FADE_OUT_MS
import app.rimlit.engine.GlowFrame
import app.rimlit.engine.GlowState
import app.rimlit.engine.Surface
import kotlinx.coroutines.flow.StateFlow

/**
 * Draws what the machine shows on [surface], and nothing while it shows nothing there. The machine owns the
 * timeline; this only animates towards each frame. Only a change of this surface's frame recomposes it, and it
 * stops collecting while its lifecycle is stopped.
 */
@Composable
fun GlowHost(state: StateFlow<GlowState?>, surface: Surface, modifier: Modifier = Modifier) {
    val current = state.collectAsStateWithLifecycle()
    val frame by remember(current, surface) { derivedStateOf { current.value?.frameFor(surface) } }
    frame?.let { GlowFrameHost(it, lockScreen = surface == Surface.LOCK_SCREEN, modifier) }
}

/**
 * Fades towards [frame]'s visibility with the machine's fade durations, or snaps there when it is instant. Under
 * Remove animations Compose already makes the fades instant.
 */
@Composable
private fun GlowFrameHost(frame: GlowFrame, lockScreen: Boolean, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(frame) {
        when {
            frame.instant -> alpha.snapTo(if (frame.visible) 1f else 0f)
            frame.visible -> alpha.animateTo(1f, tween(FADE_IN_MS.toInt(), easing = LinearOutSlowInEasing))
            else -> alpha.animateTo(0f, tween(FADE_OUT_MS.toInt(), easing = FastOutSlowInEasing))
        }
    }
    EdgeGlow(
        spec = frame.spec,
        playback = frame.spec.playback(lockScreen, rememberDeviceConditions()),
        modifier = modifier.graphicsLayer { this.alpha = alpha.value },
    )
}
