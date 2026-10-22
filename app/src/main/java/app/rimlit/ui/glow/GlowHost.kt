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
import app.rimlit.engine.FADE_IN_MS
import app.rimlit.engine.FADE_OUT_MS
import kotlinx.coroutines.delay

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
            alpha.animateTo(1f, tween(FADE_IN_MS.toInt(), easing = LinearOutSlowInEasing))
            delay(request.spec.durationMs)
        }
        alpha.animateTo(0f, tween(FADE_OUT_MS.toInt(), easing = FastOutSlowInEasing))
        finish()
    }
    EdgeGlow(spec = request.spec, modifier = modifier.graphicsLayer { this.alpha = alpha.value })
}
