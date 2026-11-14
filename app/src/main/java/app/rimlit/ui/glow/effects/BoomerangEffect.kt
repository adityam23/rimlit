package app.rimlit.ui.glow.effects

import android.graphics.Canvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The blade sweeping one lap round the outline from its [BladeAnchor] and then back along the same path, slowing into
 * each turn and speeding out of it, its tail shrinking away into the turn and growing back to trail it the other way.
 * It runs on the blade's clock, so every outline's boomerang turns in step. Only for a blade that moves: a still one
 * is drawn as [GlowEffect].
 */
internal class BoomerangEffect(private val brush: OutlineBrush) : GlowEffectPainter {
    private val blade = BladeComet(brush)
    private val start = brush.outline.part(BladeAnchors).start

    override fun draw(canvas: Canvas, t: Float) {
        val palette = brush.drawBladeTrack(canvas, t)
        val laps = bladeLaps(t, brush.spec.speed)
        val head = start + boomerangLap(laps) * brush.geometry.length
        blade.draw(canvas, palette, head, clockwise = boomerangClockwise(laps), tail = boomerangTail(laps))
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

/**
 * How long the boomerang's tail is, as a fraction of the outline, after [laps] laps of the blade's clock: as long as
 * the head is fast, so it shrinks to nothing into each turn and grows back out of it on the other side, rather than
 * swapping sides at full length.
 */
internal fun boomerangTail(laps: Float): Float = BLADE_TAIL * easeSpeed(boomerangLeg(laps).mod(1f))

/** Eases [x] in [0, 1] from rest to rest: slow at both ends, fastest in the middle. */
private fun easeInOut(x: Float): Float = 0.5f - 0.5f * cos(PI.toFloat() * x)

/** How fast [easeInOut] moves at [x], from 0 at rest to 1 at its fastest: its slope over its steepest. */
private fun easeSpeed(x: Float): Float = sin(PI.toFloat() * x)
