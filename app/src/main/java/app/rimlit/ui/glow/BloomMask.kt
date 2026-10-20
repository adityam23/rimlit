package app.rimlit.ui.glow

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The soft halo around [geometry], blurred once into an alpha-only bitmap that covers only the halo, within
 * the [width]×[height] window. Drawing an alpha-only bitmap takes its color (or shader) from the paint, so
 * each frame just tints this mask instead of re-blurring the outline. [blurPx] must be positive.
 */
internal class BloomMask(geometry: GlowGeometry, width: Float, height: Float, strokePx: Float, blurPx: Float) {

    /** `null` when the halo misses the window entirely. */
    private val bitmap: Bitmap?

    /**
     * The (bitmap source, window destination) rects to draw each frame. A screen-edge halo is a band around
     * an empty middle, drawn as four corner squares and four thin edge strips, so the GPU shades only the
     * band (a clip-out would still shade the middle).
     */
    private val pieces: List<Pair<Rect, Rect>>

    init {
        // A blur fades out within about twice its radius (sigma ≈ 0.58 × radius; ~3 sigma reach).
        val reach = strokePx / 2f + 2f * blurPx
        val halo = RectF(geometry.bounds).apply { inset(-reach, -reach) }
        val area = Rect()
        if (halo.intersect(0f, 0f, width, height)) halo.roundOut(area)
        bitmap = area.takeIf { !it.isEmpty }?.let { render(geometry, it, strokePx, blurPx) }
        // Along the straight edges the halo ends `reach` in from the bounds. Corners curve inward by at most
        // their radius, so near them it ends within reach + radius; each corner square is that size.
        val corner = reach + geometry.cornerRadius
        val bounds = geometry.bounds
        val destinations = if (bounds.width() > 2f * corner && bounds.height() > 2f * corner) {
            // Edge strip inner sides and corner square inner sides, rounded outward so no halo pixel is lost.
            val stripLeft = ceil(bounds.left + reach).toInt()
            val stripTop = ceil(bounds.top + reach).toInt()
            val stripRight = floor(bounds.right - reach).toInt()
            val stripBottom = floor(bounds.bottom - reach).toInt()
            val cornerLeft = ceil(bounds.left + corner).toInt()
            val cornerTop = ceil(bounds.top + corner).toInt()
            val cornerRight = floor(bounds.right - corner).toInt()
            val cornerBottom = floor(bounds.bottom - corner).toInt()
            listOf(
                Rect(area.left, area.top, cornerLeft, cornerTop),
                Rect(cornerRight, area.top, area.right, cornerTop),
                Rect(area.left, cornerBottom, cornerLeft, area.bottom),
                Rect(cornerRight, cornerBottom, area.right, area.bottom),
                Rect(cornerLeft, area.top, cornerRight, stripTop),
                Rect(cornerLeft, stripBottom, cornerRight, area.bottom),
                Rect(area.left, cornerTop, stripLeft, cornerBottom),
                Rect(stripRight, cornerTop, area.right, cornerBottom),
            ).filter { it.intersect(area) } // Clips each piece to the bitmap in place; drops the empty ones.
        } else {
            // A cutout ring (or tiny outline) is all corner: no empty middle to skip.
            listOf(area)
        }
        pieces = destinations.map { Rect(it).apply { offset(-area.left, -area.top) } to it }
    }

    fun draw(canvas: Canvas, paint: Paint) {
        val bitmap = bitmap ?: return
        for ((source, destination) in pieces) canvas.drawBitmap(bitmap, source, destination, paint)
    }

    private fun render(geometry: GlowGeometry, area: Rect, strokePx: Float, blurPx: Float): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeJoin = Paint.Join.ROUND
            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        return createBitmap(area.width(), area.height(), Bitmap.Config.ALPHA_8).also {
            Canvas(it).withTranslation(-area.left.toFloat(), -area.top.toFloat()) { drawPath(geometry.path, paint) }
        }
    }
}
