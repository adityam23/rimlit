package app.rimlit.ui.glow.effects

import android.graphics.Canvas
import kotlin.math.PI
import kotlin.math.cos

/**
 * The blade sweeping one lap round the outline from its [BladeAnchor] and then back along the same path, slowing into
 * each turn and speeding out of it, its tail flipping to trail it each way. It runs on the blade's clock, so every
 * outline's boomerang turns in step. Only for a blade that moves: a still one is drawn as [GlowEffect].
 */
internal class BoomerangEffect(private val brush: OutlineBrush) : GlowEffectPainter {
    private val blade = BladeComet(brush)
    private val start = brush.outline.part(BladeAnchors).start

    override fun draw(canvas: Canvas, t: Float) {
        val palette = brush.drawBladeTrack(canvas, t)
        val laps = bladeLaps(t, brush.spec.speed)
        val head = start + boomerangLap(laps) * brush.geometry.length
        blade.draw(canvas, palette, head, clockwise = boomerangClockwise(laps))
    }
}

/**
 * How far the boomerang is through its round trip after [laps] laps of the blade's clock, in [0, 2): out clockwise
 * over the first lap, back over the second.
 */
internal fun boomerangLeg(laps: Float): Float = laps.mod(2f)

/** Whether the boomerang travels clockwise after [laps] laps: on the way out, not on the way back. */
internal fun boomerangClockwise(laps: Float): Boolean = boomerangLeg(laps) < 1f

/**
 * Where the boomerang's head sits, as a fraction of the lap past the anchor in [0, 1], after [laps] laps of the
 * blade's clock: out over one lap and back over the next, eased so it comes to rest at each turn.
 */
internal fun boomerangLap(laps: Float): Float {
    val leg = boomerangLeg(laps)
    return if (boomerangClockwise(laps)) easeInOut(leg) else 1f - easeInOut(leg - 1f)
}

/** Eases [x] in [0, 1] from rest to rest: slow at both ends, fastest in the middle. */
private fun easeInOut(x: Float): Float = 0.5f - 0.5f * cos(PI.toFloat() * x)
