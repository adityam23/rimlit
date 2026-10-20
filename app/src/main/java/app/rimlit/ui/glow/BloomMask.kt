package app.rimlit.ui.glow

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave

/**
 * The soft halo around [geometry], blurred once into an alpha-only bitmap that covers only the halo, within
 * the [width]×[height] window. Drawing an alpha-only bitmap takes its color (or shader) from the paint, so
 * each frame just tints this mask instead of re-blurring the outline. [blurPx] must be positive.
 */
internal class BloomMask(geometry: GlowGeometry, width: Float, height: Float, strokePx: Float, blurPx: Float) {

    /** Where the bitmap sits in the window. */
    private val area = Rect()

    /** `null` when the halo misses the window entirely. */
    private val bitmap: Bitmap?

    /**
     * The halo-free middle of the outline, clipped out when drawing so a screen-edge halo doesn't shade the
     * whole empty screen every frame; `null` when the outline is too small to have one.
     */
    private val hollow: RectF?

    init {
        // A blur fades out within about twice its radius (sigma ≈ 0.58 × radius; ~3 sigma reach).
        val reach = strokePx / 2f + 2f * blurPx
        val bounds = RectF().also { geometry.path.computeBounds(it, true) }
        val halo = RectF(bounds).apply { inset(-reach, -reach) }
        bitmap = if (halo.intersect(0f, 0f, width, height)) {
            halo.roundOut(area)
            area.takeIf { it.width() > 0 && it.height() > 0 }?.let { render(geometry, it, strokePx, blurPx) }
        } else {
            null
        }
        // Corners curve inward by at most their radius, so past reach + radius from the bounds there is no halo.
        val inset = reach + geometry.cornerRadius
        hollow = RectF(bounds).apply { inset(inset, inset) }.takeIf { it.width() > 0f && it.height() > 0f }
    }

    fun draw(canvas: Canvas, paint: Paint) {
        val bitmap = bitmap ?: return
        canvas.withSave {
            hollow?.let(::clipOutRect)
            drawBitmap(bitmap, area.left.toFloat(), area.top.toFloat(), paint)
        }
    }

    private fun render(geometry: GlowGeometry, area: Rect, strokePx: Float, blurPx: Float): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeJoin = Paint.Join.ROUND
            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        return createBitmap(area.width(), area.height(), Bitmap.Config.ALPHA_8).also {
            Canvas(it).withSave {
                translate(-area.left.toFloat(), -area.top.toFloat())
                drawPath(geometry.path, paint)
            }
        }
    }
}
