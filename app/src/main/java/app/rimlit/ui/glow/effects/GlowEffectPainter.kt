package app.rimlit.ui.glow.effects

import android.graphics.Canvas
import app.rimlit.model.GlowStyle

/**
 * Paints one outline in one edge style, frame after frame. Built once per spec and layout from the outline's
 * [OutlineBrush]; a frame allocates at most shaders. Every outline of a glow plays on the same clock [t], in seconds,
 * so effects that move stay in step across outlines.
 */
internal fun interface GlowEffectPainter {
    fun draw(canvas: Canvas, t: Float)
}

/** The painter for this style on [brush]'s outline. */
internal fun GlowStyle.painter(brush: OutlineBrush): GlowEffectPainter = when (this) {
    GlowStyle.BASIC -> BasicEffect(brush)

    GlowStyle.GLOW -> GlowEffect(brush)

    // A still blade can't race, so it glows: the whole outline lit, with its halo.
    GlowStyle.BLADE -> if (brush.still) GlowEffect(brush) else BladeEffect(brush)

    GlowStyle.WAVE -> WaveEffect(brush)

    GlowStyle.GLITTER -> GlitterEffect(brush)

    GlowStyle.ECHO -> EchoEffect(brush)
}
