package app.rimlit.ui.glow.effects

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import app.rimlit.model.GlowPalette
import app.rimlit.model.GlowSpec
import app.rimlit.ui.glow.GlowGeometry
import app.rimlit.ui.glow.GlowOutline
import app.rimlit.ui.glow.PaletteFlow
import app.rimlit.ui.glow.Playback
import app.rimlit.ui.glow.SideMask
import kotlin.math.PI
import kotlin.math.cos

/** Breathing rate of the pulse at speed 1, in cycles per second. */
private const val PULSE_HZ = 0.7f

/** Gradient/rainbow flow around the outline at speed 1, in loops per second. */
private const val FLOW_LOOPS_PER_SECOND = 0.12f

/**
 * What every effect paints one [outline] with: its palette (a solid color, or a gradient flowing around the outline),
 * the shared breathing pulse, and the stroke and halo paints, tinted at an alpha × the playback's brightness. Under a
 * [sideMask] (a screen edge lit on only some sides), every paint's color is cut to it, so whatever an effect draws,
 * halos, sprites and all, is kept to the lit sides while the effect knows nothing of them. Built once per spec, layout
 * and mask; a frame allocates only the palette's shader and, under a mask, its cut.
 */
internal class OutlineBrush(
    val spec: GlowSpec,
    private val playback: Playback,
    val outline: GlowOutline,
    sideMask: SideMask?,
) {
    val geometry: GlowGeometry get() = outline.geometry

    /** Line thickness, from the outline's layout. */
    val thicknessPx: Float get() = outline.key.thicknessPx

    /** Glow width (the halo's blur radius), from the outline's layout. */
    val glowPx: Float get() = outline.key.glowPx

    /** Holds still (Remove animations): no pulse, flow or motion. */
    val still: Boolean get() = playback.still

    /** The single color of a solid palette; `null` for multicolor palettes, which use a shader. */
    val solidColor: Int? = (spec.palette as? GlowPalette.Colors)?.argb?.singleOrNull()

    // Filtered, so a side mask's coarse bitmap is sampled smoothly.
    private val core = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = thicknessPx
        strokeJoin = Paint.Join.ROUND
        solidColor?.let { color = it }
    }

    /** Tints alpha-only masks: the halo, and sprites. */
    private val mask = Paint(Paint.FILTER_BITMAP_FLAG).apply { solidColor?.let { color = it } }

    /** Multicolor palettes flow around the outline over time; `null` for a solid color. */
    private val flow = if (solidColor == null) PaletteFlow(outline.geometry, spec.palette) else null

    private val sideCut = sideMask?.let { SideCut(it.shader, solidColor) }

    /** The palette's shader at time [t]; `null` for a solid color, which the paints already hold. */
    fun palette(t: Float): Shader? = flow?.shader(phase = t * FLOW_LOOPS_PER_SECOND * spec.speed)

    /** Smooth breathing in [0.6, 1]; full and steady when still. */
    fun pulse(t: Float): Float = if (still) {
        1f
    } else {
        0.6f + 0.4f * (0.5f - 0.5f * cos(2f * PI.toFloat() * PULSE_HZ * spec.speed * t))
    }

    /** Strokes the outline itself. */
    fun drawOutline(canvas: Canvas, shader: Shader?, alpha: Float) = drawPath(canvas, geometry.path, shader, alpha)

    /** Strokes [path] (the outline, or a shape derived from it) at the glow's thickness. */
    fun drawPath(canvas: Canvas, path: Path, shader: Shader?, alpha: Float) {
        canvas.drawPath(path, core.tint(shader, alpha))
    }

    /** Draws the outline's pre-blurred halo, if it has one. */
    fun drawBloom(canvas: Canvas, shader: Shader?, alpha: Float) {
        outline.bloom?.draw(canvas, mask.tint(shader, alpha))
    }

    /** Draws the alpha-only [sprite] stretched over [destination], tinted like the halo. */
    fun drawSprite(canvas: Canvas, sprite: Bitmap, destination: RectF, shader: Shader?, alpha: Float) {
        canvas.drawBitmap(sprite, null, destination, mask.tint(shader, alpha))
    }

    /**
     * Colors this paint with [shader] (`null` keeps the solid color set at construction), cut to the side mask if any,
     * at [alpha] × brightness.
     */
    private fun Paint.tint(shader: Shader?, alpha: Float): Paint = apply {
        this.shader = sideCut?.cut(shader) ?: shader
        this.alpha = (255 * alpha * playback.brightness).toInt().coerceIn(0, 255)
    }
}

/**
 * A side mask's [mask] (see [SideMask]) applied to a brush's colors: a color's shader keeps only where the mask is
 * opaque. A paint's alpha still applies on top, and an alpha-only bitmap drawn with the paint (a halo, a sprite) takes
 * its color from the cut shader, so every kind of draw is masked alike. [solidColor] is a shader too under a mask
 * (a paint holds one shader), cut once; a frame's shader is cut once however many draws share it.
 */
private class SideCut(private val mask: Shader, solidColor: Int?) {
    /** The solid color, opaque (a paint's alpha replaces its color's), cut; `null` for a multicolor palette. */
    private val solid = solidColor?.let {
        val opaque = ColorUtils.setAlphaComponent(it, OPAQUE)
        compose(LinearGradient(0f, 0f, 1f, 0f, opaque, opaque, Shader.TileMode.CLAMP))
    }

    private var lastColor: Shader? = null
    private var lastCut: Shader? = null

    /** [color] cut to the mask; `null` stands for the solid color, which only a solid palette draws with. */
    fun cut(color: Shader?): Shader {
        if (color == null) return checkNotNull(solid) { "Only a solid palette draws without a shader" }
        if (color !== lastColor) {
            lastColor = color
            lastCut = compose(color)
        }
        return checkNotNull(lastCut)
    }

    /** DST_IN keeps [color] where the mask is opaque, as the blade's comet keeps its palette. */
    private fun compose(color: Shader): Shader = ComposeShader(color, mask, PorterDuff.Mode.DST_IN)
}

private const val OPAQUE = 255
