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

/** Blade tail at full speed, as a fraction of the outline length. */
internal const val BLADE_TAIL = 0.25f

/** Arc length over which the blade's core cuts off at its head: a crisp front edge. */
private const val CRISP_EDGE_PX = 0.5f

/** Opacity of the dim outline blades race along. */
private const val TRACK_ALPHA = 0.18f

/** Opacity of a blade's halo. */
private const val HALO_ALPHA = 0.8f

/** Laps a blade covers in [t] seconds at [speed]: the clock every blade style runs on. */
internal fun bladeLaps(t: Float, speed: Float): Float = t * BLADE_LAPS_PER_SECOND * speed

/** Arc distance, in [0, [length]), of a head that has run [laps] round an outline [length] long from its start. */
internal fun lapHead(laps: Float, length: Float): Float = (laps - floor(laps)) * length

/** Draws the dim outline blades race along at time [t]; returns the palette's shader, to color the blades with. */
internal fun OutlineBrush.drawBladeTrack(canvas: Canvas, t: Float): Shader? =
    palette(t).also { drawOutline(canvas, it, TRACK_ALPHA) }

/**
 * One blade on [brush]'s outline, for every style that moves one: a comet whose tail trails the way it travels, its
 * halo following it. Built once per spec and layout; a frame allocates only shaders.
 */
internal class BladeComet(private val brush: OutlineBrush) {
    private val fade = BladeFade(brush.geometry, color = brush.solidColor ?: Color.WHITE)

    /**
     * Draws the comet colored by [palette], its head at arc distance [head], travelling [clockwise] or against the
     * outline, its tail a fraction [tail] of the outline long; only the part between arc distances [from] and [to]
     * shows (see [BladeFade.moveTo]).
     */
    fun draw(
        canvas: Canvas,
        palette: Shader?,
        head: Float,
        clockwise: Boolean,
        tail: Float = BLADE_TAIL,
        from: Float = Float.NEGATIVE_INFINITY,
        to: Float = Float.POSITIVE_INFINITY,
    ) {
        fade.moveTo(head, tail * brush.geometry.length, clockwise, from, to)
        brush.drawBloom(canvas, comet(fade.shader(edge = brush.glowPx), palette), HALO_ALPHA)
        brush.drawOutline(canvas, comet(fade.shader(edge = CRISP_EDGE_PX), palette), 1f)
    }

    /** The blade's [fade] colored by [palette]; a solid color is already baked into the fade. */
    private fun comet(fade: Shader, palette: Shader?): Shader =
        // DST_IN keeps the palette's colors where the fade is opaque.
        if (palette == null) fade else ComposeShader(palette, fade, PorterDuff.Mode.DST_IN)
}
