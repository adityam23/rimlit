package app.rimlit.ui.glow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import app.rimlit.model.GlowSpec
import kotlinx.coroutines.delay

private const val FADE_IN_MS = 180
private const val FADE_OUT_MS = 450

/**
 * One request to show a glow. Deliberately not a data class: every request is distinct by identity,
 * so a new notification restarts the timeline even when its spec equals the current one.
 */
class GlowRequest(val spec: GlowSpec)

/**
 * The single owner of a glow's timeline: fade in, hold for `spec.durationMs`, fade out, then [onFinish].
 *
 * A new [request] while showing re-targets without flicker (already-visible glows stay visible and the
 * hold restarts). [dismissed] cuts the hold short with a fade-out (e.g. the notification was removed).
 */
@Composable
fun GlowHost(request: GlowRequest, dismissed: Boolean, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    val finish by rememberUpdatedState(onFinish)
    LaunchedEffect(request, dismissed) {
        if (!dismissed) {
            alpha.animateTo(1f, tween(FADE_IN_MS, easing = LinearOutSlowInEasing))
            delay(request.spec.durationMs)
        }
        alpha.animateTo(0f, tween(FADE_OUT_MS, easing = FastOutSlowInEasing))
        finish()
    }
    EdgeGlow(spec = request.spec, modifier = modifier.graphicsLayer { this.alpha = alpha.value })
}
