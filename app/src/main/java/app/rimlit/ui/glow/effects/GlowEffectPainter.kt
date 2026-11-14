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

/** The styles whose blade races round the outline. */
private val BLADES = setOf(GlowStyle.BLADE, GlowStyle.LOOP, GlowStyle.MIRROR, GlowStyle.BOOMERANG)

/**
 * The painter for this style on [brush]'s outline. A still blade can't race, so it glows: the whole outline lit, with
 * its halo.
 */
internal fun GlowStyle.painter(brush: OutlineBrush): GlowEffectPainter =
    if (brush.still && this in BLADES) GlowEffect(brush) else movingPainter(brush)

/** The painter for this style on [brush]'s outline, when the glow may move. */
private fun GlowStyle.movingPainter(brush: OutlineBrush): GlowEffectPainter = when (this) {
    GlowStyle.BASIC -> BasicEffect(brush)

    GlowStyle.GLOW -> GlowEffect(brush)

    GlowStyle.BLADE -> BladeEffect(brush)

    GlowStyle.WAVE -> WaveEffect(brush)

    GlowStyle.GLITTER -> GlitterEffect(brush)

    GlowStyle.ECHO -> EchoEffect(brush)

    GlowStyle.LOOP -> TwinBladesEffect(brush, TwinMode.LOOP)

    GlowStyle.MIRROR -> TwinBladesEffect(brush, TwinMode.MIRROR)

    GlowStyle.BOOMERANG -> BoomerangEffect(brush)

    // No edge: the look shows its particles alone.
    GlowStyle.NONE -> GlowEffectPainter { _, _ -> }
}
