package app.rimlit.ui.glow.effects

import android.graphics.Canvas

/** A crisp outline, breathing. */
internal class BasicEffect(private val brush: OutlineBrush) : GlowEffectPainter {
    override fun draw(canvas: Canvas, t: Float) {
        brush.drawOutline(canvas, brush.palette(t), brush.pulse(t))
    }
}
