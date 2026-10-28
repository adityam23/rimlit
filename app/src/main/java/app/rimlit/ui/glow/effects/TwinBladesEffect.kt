package app.rimlit.ui.glow.effects

import android.graphics.Canvas
import android.graphics.Shader
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.withSign

/** How [TwinBladesEffect]'s two blades share the outline. */
internal enum class TwinMode {
    /** Half a lap apart, chasing each other the same way round. */
    LOOP,

    /** Leaving one point in opposite directions and meeting across the outline, again and again. */
    MIRROR,
}

/**
 * Two blades racing around a dim outline, each like [BladeEffect]'s and as fast, its tail trailing its own direction
 * of travel. In [TwinMode.MIRROR] they leave the outline's [BladeAnchor] (the bottom centre of the screen edge; the
 * side of the cutout ring away from the screen edge) and meet straight across it (the top; the side toward the screen
 * edge): each grows out of the start and drains into the meeting point. Both modes play on the shared lap, so
 * outlines stay in step. Only for blades that move: still ones are drawn as [GlowEffect].
 */
internal class TwinBladesEffect(private val brush: OutlineBrush, private val mode: TwinMode) : GlowEffectPainter {
    private val length = brush.geometry.length
    private val tail = BLADE_TAIL * length

    private val first = BladeComet(brush)
    private val second = BladeComet(brush)

    private val anchor by lazy(LazyThreadSafetyMode.NONE) { brush.outline.part(BladeAnchors) }

    /** Mirror's clockwise run from the anchor to the meeting point; its twin runs the rest of the lap, against it. */
    private val run by lazy(LazyThreadSafetyMode.NONE) { clockwiseArc(anchor.start, anchor.across, length) }

    override fun draw(canvas: Canvas, t: Float) {
        val palette = brush.drawBladeTrack(canvas, t)
        val laps = bladeLaps(t, brush.spec.speed)
        when (mode) {
            TwinMode.LOOP -> {
                first.draw(canvas, palette, head = loopHead(laps, blade = 0, length), clockwise = true)
                second.draw(canvas, palette, head = loopHead(laps, blade = 1, length), clockwise = true)
            }

            TwinMode.MIRROR -> {
                val progress = mirrorProgress(laps)
                first.drawMirror(canvas, palette, progress, run)
                second.drawMirror(canvas, palette, progress, run - length)
            }
        }
    }

    /** Draws this blade [progress] through its Mirror trip along [run] from the anchor, showing only [run] itself. */
    private fun BladeComet.drawMirror(canvas: Canvas, palette: Shader?, progress: Float, run: Float) {
        val start = anchor.start
        draw(
            canvas,
            palette,
            head = mirrorHead(progress, start, run, tail),
            clockwise = run > 0f,
            from = min(start, start + run),
            to = max(start, start + run),
        )
    }
}

/** Arc distance, in [0, [length]), of Loop's [blade] (0 or 1) after [laps]: the second runs half a lap ahead. */
internal fun loopHead(laps: Float, blade: Int, length: Float): Float = lapHead(laps + blade / 2f, length)

/**
 * How far, from 0 to 1, Mirror's blades are through their current trip after [laps]. A trip is half a lap to the
 * meeting point plus a tail to drain into it, at a blade's speed.
 */
internal fun mirrorProgress(laps: Float): Float {
    val trips = laps / (0.5f + BLADE_TAIL)
    return trips - floor(trips)
}

/**
 * Unwrapped arc distance of a Mirror blade's head [progress] (0 to 1) through its trip from [start] along [run] (arc
 * length to the meeting point, positive clockwise, negative against): it reaches the meeting point, then runs on by its
 * [tail], so the tail drains into it.
 */
internal fun mirrorHead(progress: Float, start: Float, run: Float, tail: Float): Float =
    start + progress * (run + tail.withSign(run))

/** Clockwise arc distance, in [0, [length]), from [from] to [to] on an outline [length] long. */
internal fun clockwiseArc(from: Float, to: Float, length: Float): Float = ((to - from) % length + length) % length
