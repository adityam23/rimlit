package app.rimlit.ui.glow.effects

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Shader
import app.rimlit.ui.glow.OutlineShape
import kotlin.math.floor

/** Journeys each ring completes per second at speed 1. */
private const val ECHOES_PER_SECOND = 0.5f

/** Rings travelling at once, evenly spaced. */
private const val RINGS = 3

/** How far a ring travels: line thicknesses plus glow widths. */
private const val REACH_THICKNESSES = 4f
private const val REACH_GLOWS = 2f

/** Farthest reach, as a share of the outline's shorter side, so a preview's or the cutout ring's rings stay close. */
private const val MAX_REACH_SHARE = 0.2f

/** Opacity of a ring as it leaves the outline. */
private const val RING_ALPHA = 0.8f

/** Where the one ring of a still echo stands, as a share of its journey. */
private const val STILL_PROGRESS = 0.4f

/**
 * Rings echoing off a steady outline toward the open side (inward from the screen edge, outward from the cutout
 * ring), fading as they go. Each ring is the outline's shape moved exactly that far (see [OutlineShape.trace]), the
 * same distance all round, so a ring round a wide notch keeps its gap along the sides and the end alike. Set into one
 * reused path. Still, one ring stands part-way out.
 */
internal class EchoEffect(private val brush: OutlineBrush) : GlowEffectPainter {
    private val shape = brush.geometry.shape
    private val reach =
        shape.fit(brush.thicknessPx * REACH_THICKNESSES + brush.glowPx * REACH_GLOWS, MAX_REACH_SHARE)
    private val ring = Path()

    override fun draw(canvas: Canvas, t: Float) {
        val palette = brush.palette(t)
        brush.drawOutline(canvas, palette, 1f)
        if (brush.still) {
            drawRing(canvas, STILL_PROGRESS, palette)
        } else {
            val cycle = t * ECHOES_PER_SECOND * brush.spec.speed
            for (i in 0 until RINGS) {
                val progress = cycle + i.toFloat() / RINGS
                drawRing(canvas, progress - floor(progress), palette)
            }
        }
    }

    private fun drawRing(canvas: Canvas, progress: Float, palette: Shader?) {
        if (shape.trace(ring, by = echoDistance(progress, reach))) {
            brush.drawPath(canvas, ring, palette, echoAlpha(progress))
        }
    }
}

/** How far a ring [progress] (0 to 1) through its journey has travelled from the outline, out of [reach]. */
internal fun echoDistance(progress: Float, reach: Float): Float = progress * reach

/** A ring's opacity [progress] (0 to 1) through its journey: strongest as it leaves, gone as it arrives. */
internal fun echoAlpha(progress: Float): Float = RING_ALPHA * (1f - progress) * (1f - progress)
