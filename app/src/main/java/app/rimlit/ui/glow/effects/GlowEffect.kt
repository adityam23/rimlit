package app.rimlit.ui.glow.effects

import android.graphics.Canvas

/** A soft halo with a bright core, breathing together. */
internal class GlowEffect(private val brush: OutlineBrush) : GlowEffectPainter {
    override fun draw(canvas: Canvas, t: Float) {
        val palette = brush.palette(t)
        val pulse = brush.pulse(t)
        brush.drawBloom(canvas, palette, 0.8f * pulse)
        brush.drawOutline(canvas, palette, pulse)
    }
}
