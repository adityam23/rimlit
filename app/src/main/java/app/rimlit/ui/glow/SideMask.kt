package app.rimlit.ui.glow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import app.rimlit.model.ALL_SIDES
import app.rimlit.model.ScreenSide
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Window pixels per mask pixel each way. The light fades smoothly everywhere (see [SideLight]), so a coarse mask drawn
 * filtered loses nothing, at a sixteenth of the memory and the work.
 */
private const val MASK_SCALE = 4f

/**
 * Mask pixels kept round the lit ones. Drawn filtered, a mask pixel reaches half a pixel past its square; two keep a
 * ring of 0 inside the cut's edge, so the cut is exactly what the whole mask's would be.
 */
private const val MASK_MARGIN = 2

/** A side's fade toward an unlit neighbour is at least this many line thicknesses long, however narrow its halo. */
private const val MIN_TAPER_THICKNESSES = 8f

/**
 * Everything a [SideMask] depends on. It is cached apart from the [GlowLayout], so lighting a side rebuilds only the
 * mask, not the halos. [edge] is compared by identity: the cached layout keeps its edge as long as its geometry holds.
 */
internal data class SideMaskKey(
    val edge: RoundRect,
    val width: Float,
    val height: Float,
    val sides: Set<ScreenSide>,
    val taperPx: Float,
) {
    companion object {
        /**
         * What keeps the drawing of the outline [shape] in [layout], halo and all, to the lit [sides]: `null` when
         * every side is lit, and for the cutout ring, which sides don't touch. A side fades out toward an unlit one
         * over its halo's reach, so the halo's end is as soft as its edges, and at least [MIN_TAPER_THICKNESSES]
         * lines, so a line with no halo still fades rather than stops (a line is never thinner than
         * [app.rimlit.model.GlowSettings.Ranges]' least, so neither is a fade).
         */
        fun of(shape: OutlineShape, layout: GlowLayoutKey, sides: Set<ScreenSide>): SideMaskKey? {
            if (shape !is RoundRect || sides.containsAll(ALL_SIDES)) return null
            val taperPx = max(haloReach(layout.haloStrokePx, layout.glowPx), layout.thicknessPx * MIN_TAPER_THICKNESSES)
            return SideMaskKey(shape, layout.width, layout.height, sides, taperPx)
        }
    }
}

/**
 * The [SideLight] of [key] over its window, rendered once into an alpha-only bitmap, which keeps a screen-edge
 * outline's drawing, halo and all, to its lit sides: a painter draws into the layer [begin] opens, and [end] cuts it
 * to the mask. The layer and the cut cover only the lit part of the window (everything else the mask clears anyway).
 * A frame allocates nothing.
 */
internal class SideMask(key: SideMaskKey) {
    private val bitmap: Bitmap

    /** The bitmap's pixels round the lit ones. */
    private val source: Rect

    /** Where [source] lies in the window: the layer's bounds. */
    private val bounds: RectF

    /** Keeps what is drawn beneath only where the mask is opaque. */
    private val cut = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    init {
        val columns = ceil(key.width / MASK_SCALE).toInt().coerceAtLeast(1)
        val rows = ceil(key.height / MASK_SCALE).toInt().coerceAtLeast(1)
        bitmap = createBitmap(columns, rows, Bitmap.Config.ALPHA_8)
        // Rows may be padded: one byte per pixel, rowBytes per row.
        val pixels = SideLight(key.edge, key.sides, key.taperPx)
            .render(key.width, key.height, columns, rows, bitmap.rowBytes)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels.alphas))
        source = if (pixels.right > pixels.left) {
            Rect(
                max(pixels.left - MASK_MARGIN, 0),
                max(pixels.top - MASK_MARGIN, 0),
                min(pixels.right + MASK_MARGIN, columns),
                min(pixels.bottom + MASK_MARGIN, rows),
            )
        } else {
            Rect()
        }
        // The same scale as the whole bitmap drawn over the whole window.
        val scaleX = key.width / columns
        val scaleY = key.height / rows
        bounds = RectF(source.left * scaleX, source.top * scaleY, source.right * scaleX, source.bottom * scaleY)
    }

    /** Opens the layer a painter draws into; returns it, for [end]. */
    fun begin(canvas: Canvas): Int = canvas.saveLayer(bounds, null)

    /** Cuts what was drawn since [begin] opened [layer] to the lit sides, and closes it. */
    fun end(canvas: Canvas, layer: Int) {
        canvas.drawBitmap(bitmap, source, bounds, cut)
        canvas.restoreToCount(layer)
    }
}
