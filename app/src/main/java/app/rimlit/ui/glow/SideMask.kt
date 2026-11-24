package app.rimlit.ui.glow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import java.nio.ByteBuffer
import kotlin.math.ceil

/**
 * Window pixels per mask pixel each way. The light fades smoothly everywhere (see [SideLight]), so a coarse mask drawn
 * filtered loses nothing, at a sixteenth of the memory and the work.
 */
private const val MASK_SCALE = 4f

/** A mask pixel's alpha where the glow shows fully. */
private const val OPAQUE = 255

/**
 * [light] over the [width]×[height] window, rendered once into an alpha-only bitmap, which keeps a screen-edge
 * outline's drawing, halo and all, to its lit sides: a painter draws into the layer [begin] opens, and [end] cuts it
 * to the mask. A frame allocates nothing.
 */
internal class SideMask(light: SideLight, width: Float, height: Float) {
    private val bounds = RectF(0f, 0f, width, height)

    private val bitmap: Bitmap

    /** Keeps what is drawn beneath only where the mask is opaque. */
    private val cut = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    init {
        val columns = ceil(width / MASK_SCALE).toInt().coerceAtLeast(1)
        val rows = ceil(height / MASK_SCALE).toInt().coerceAtLeast(1)
        bitmap = createBitmap(columns, rows, Bitmap.Config.ALPHA_8)
        // Rows may be padded: one byte per pixel, rowBytes per row.
        val rowBytes = bitmap.rowBytes
        val alphas = ByteArray(rowBytes * rows)
        for (row in 0 until rows) {
            val y = (row + 0.5f) * height / rows
            for (column in 0 until columns) {
                val alpha = light.at((column + 0.5f) * width / columns, y)
                alphas[row * rowBytes + column] = (OPAQUE * alpha + 0.5f).toInt().toByte()
            }
        }
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(alphas))
    }

    /** Opens the layer a painter draws into; returns it, for [end]. */
    fun begin(canvas: Canvas): Int = canvas.saveLayer(bounds, null)

    /** Cuts what was drawn since [begin] opened [layer] to the lit sides, and closes it. */
    fun end(canvas: Canvas, layer: Int) {
        canvas.drawBitmap(bitmap, null, bounds, cut)
        canvas.restoreToCount(layer)
    }
}
