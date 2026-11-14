package app.rimlit.ui.glow

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import app.rimlit.model.GlowSpec
import app.rimlit.model.GlowStyle
import app.rimlit.ui.glow.effects.OutlineBrush
import app.rimlit.ui.glow.effects.painter
import app.rimlit.ui.glow.particles.rememberParticleLayer
import kotlinx.coroutines.delay

/**
 * Pure edge-glow renderer shared by the overlay, the lock-screen activity and in-app previews.
 * No side effects and no settings access: everything comes from [spec], played as [playback] says.
 *
 * With [preview] the outline is a scaled stand-in for the display (proportional corners, simulated
 * camera hole) instead of the real window's corners and cutout. A preview has no [GlowFrameHost] fading it,
 * so its particles fade themselves in and out.
 */
@Composable
fun EdgeGlow(spec: GlowSpec, playback: Playback, modifier: Modifier = Modifier, preview: Boolean = false) {
    val view = LocalView.current
    // A still glow needs no clock at all: it draws once.
    val seconds = rememberFrameSeconds(running = !playback.still, maxFps = playback.maxFps)
    val layouts = remember { BuildCache(::GlowLayout) }
    val particles = rememberParticleLayer()
    // A preview's particles start over with each new duration: it sets their cycle, so on the running clock every
    // particle would jump (each tick of the duration slider). On a surface the duration never changes mid-glow.
    val particleStart = remember(if (preview) spec.durationMs else null) {
        Snapshot.withoutReadObservation { seconds.value }
    }
    Spacer(
        modifier.drawWithCache {
            val display = if (preview) {
                DisplayShape.preview(size.width, size.height)
            } else {
                DisplayShape.of(view)
            }
            // A preview is a scaled-down screen, so custom corners and cutouts scale with it to keep their proportion.
            val scale = if (preview) size.width / view.rootView.width.coerceAtLeast(1) else 1f
            val pxPerDp = 1.dp.toPx() * scale
            val shape = display
                .let { d -> spec.cornerRadiusDp?.let { d.withCornerRadius(it * pxPerDp) } ?: d }
                .let { d -> spec.customCutout?.let { d.withCustomCutout(it, pxPerDp, size.width, size.height) } ?: d }
            val layout = layouts.get(
                GlowLayoutKey(
                    width = size.width,
                    height = size.height,
                    shape = shape,
                    region = spec.region,
                    thicknessPx = spec.thicknessDp.dp.toPx(),
                    cutoutGapPx = 4.dp.toPx(),
                    glowPx = spec.glowWidthDp.dp.toPx(),
                ),
            )
            val particlePainter = particles.painter(spec, playback, size, pxPerDp, preview)
            // No edge draws the plain outline when no particles draw (none in the spec, or none under Remove
            // animations): a glow showing nothing would hide the notification.
            val style = if (spec.style == GlowStyle.NONE && particlePainter == null) GlowStyle.BASIC else spec.style
            // One painter per outline, each in that style; an array, so a frame walks it without an iterator.
            val outlines = layout.outlines
            val edges = Array(outlines.size) { style.painter(OutlineBrush(spec, playback, outlines[it])) }
            onDrawBehind {
                // Reading the clock here (draw phase) redraws every frame without recomposing.
                val t = seconds.value
                drawIntoCanvas { canvas ->
                    // Particles go beneath the edge.
                    particlePainter?.draw(canvas.nativeCanvas, t - particleStart)
                    for (i in edges.indices) edges[i].draw(canvas.nativeCanvas, t)
                }
            }
        },
    )
}

/**
 * Seconds since the first frame, while [running]; advanced at most [maxFps] times a second (`null`: every frame).
 * Read it only in draw to avoid recomposition. Under a cap the clock sleeps between frames, so the app isn't woken
 * on the vsyncs it skips.
 */
@Composable
private fun rememberFrameSeconds(running: Boolean, maxFps: Int?): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running, maxFps) {
        if (!running) return@LaunchedEffect
        // A little under the cap's interval, so vsync jitter doesn't push a frame a whole vsync late.
        val pauseMs = maxFps?.let { (MILLIS_PER_SECOND / it * (1 - FRAME_JITTER)).toLong() }
        // Carries on from where it was: a changed cap restarts this effect, not the animation.
        val start = withFrameNanos { it } - (seconds.floatValue * NANOS_PER_SECOND).toLong()
        while (true) {
            if (pauseMs != null) delay(pauseMs)
            withFrameNanos { now -> seconds.floatValue = (now - start) / NANOS_PER_SECOND }
        }
    }
    return seconds
}

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MILLIS_PER_SECOND = 1_000f

/** Share of a capped frame interval the clock wakes early by, so the frame after it isn't a vsync late. */
private const val FRAME_JITTER = 0.12f
