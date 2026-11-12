package app.rimlit.ui.glow.effects

import android.graphics.Canvas

/**
 * A comet of light racing around a dim outline, its halo following it. Every outline's blade sits at the same
 * fraction of a lap, so blades race in step. Only for a blade that moves: a still one is drawn as [GlowEffect].
 */
internal class BladeEffect(private val brush: OutlineBrush) : GlowEffectPainter {
    private val blade = BladeComet(brush)

    override fun draw(canvas: Canvas, t: Float) {
        val palette = brush.drawBladeTrack(canvas, t)
        val head = lapHead(bladeLaps(t, brush.spec.speed), brush.geometry.length)
        blade.draw(canvas, palette, head, clockwise = true)
    }
}
