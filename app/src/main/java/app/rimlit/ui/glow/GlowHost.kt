package app.rimlit.ui.glow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
    val frame by rememberSurfaceState(state, surface, FRAME)
    frame?.let { GlowFrameHost(it, lockScreen = surface == Surface.LOCK_SCREEN, modifier) }
}

/**
 * What [read] reads for [surface] from the machine's [state]: only a change of its result recomposes the caller, and it
 * stops collecting while its lifecycle is stopped. [read] must be a stable value (a constant), as it keys the result.
 */
@Composable
internal fun <T> rememberSurfaceState(
    state: StateFlow<GlowState?>,
    surface: Surface,
    read: (GlowState?, Surface) -> T,
): State<T> {
    val current = state.collectAsStateWithLifecycle()
    return remember(current, surface, read) { derivedStateOf { read(current.value, surface) } }
}

/** The surface's frame: what it draws now, or `null` for nothing. */
internal val FRAME = { state: GlowState?, surface: Surface -> state?.frameFor(surface) }

/**
 * The opacity [frame] is drawn at: it fades towards the frame's visibility with the machine's fade durations, or snaps
 * there when it is instant. Under Remove animations Compose already makes the fades instant. The edge and the pill
 * both follow it, so they fade together.
 */
@Composable
internal fun rememberFrameAlpha(frame: GlowFrame): State<Float> {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(frame.visible, frame.instant) {
        when {
            frame.instant -> alpha.snapTo(if (frame.visible) 1f else 0f)
            frame.visible -> alpha.animateTo(1f, tween(FADE_IN_MS.toInt(), easing = LinearOutSlowInEasing))
            else -> alpha.animateTo(0f, tween(FADE_OUT_MS.toInt(), easing = FastOutSlowInEasing))
        }
    }
    return alpha.asState()
}

@Composable
private fun GlowFrameHost(frame: GlowFrame, lockScreen: Boolean, modifier: Modifier = Modifier) {
    val alpha by rememberFrameAlpha(frame)
    EdgeGlow(
        spec = frame.spec,
        playback = frame.spec.playback(lockScreen, rememberDeviceConditions()),
        // Read in the draw phase: the fade redraws without recomposing.
        modifier = modifier.graphicsLayer { this.alpha = alpha },
    )
}
