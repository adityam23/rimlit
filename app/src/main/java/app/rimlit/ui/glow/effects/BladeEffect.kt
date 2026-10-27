package app.rimlit.ui.glow.effects

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.PorterDuff
import android.graphics.Shader
import app.rimlit.ui.glow.BladeFade
import kotlin.math.floor

/** Blade laps per second at speed 1. */
private const val BLADE_LAPS_PER_SECOND = 0.45f

/** Blade tail as a fraction of the outline length. */
private const val BLADE_TAIL = 0.25f

/** Arc length over which the blade's core cuts off at its head: a crisp front edge. */
private const val CRISP_EDGE_PX = 0.5f

/**
 * A comet of light racing around a dim outline, its halo following it. Every outline's blade sits at the same
 * fraction of a lap, so blades race in step. Only for a blade that moves: a still one is drawn as [GlowEffect].
 */
internal class BladeEffect(private val brush: OutlineBrush) : GlowEffectPainter {
    private val geometry = brush.geometry

    private val blade =
        BladeFade(geometry, tail = BLADE_TAIL * geometry.length, color = brush.solidColor ?: Color.WHITE)

    override fun draw(canvas: Canvas, t: Float) {
        val palette = brush.palette(t)
        brush.drawOutline(canvas, palette, 0.18f)
        val lap = t * BLADE_LAPS_PER_SECOND * brush.spec.speed
        blade.moveTo(head = (lap - floor(lap)) * geometry.length)
        brush.drawBloom(canvas, comet(blade.shader(edge = brush.glowPx), palette), 0.8f)
        brush.drawOutline(canvas, comet(blade.shader(edge = CRISP_EDGE_PX), palette), 1f)
    }

    /** The blade's [fade] colored by [palette]; a solid color is already baked into the fade. */
    private fun comet(fade: Shader, palette: Shader?): Shader =
        // DST_IN keeps the palette's colors where the fade is opaque.
        if (palette == null) fade else ComposeShader(palette, fade, PorterDuff.Mode.DST_IN)
}
