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
     * The (bitmap source, window destination) rects to draw each frame. A screen-edge halo is four strips
     * around its empty middle, so the GPU never shades that middle (a clip-out would still shade it).
     */
    private val pieces: List<Pair<Rect, Rect>>

    init {
        // A blur fades out within about twice its radius (sigma ≈ 0.58 × radius; ~3 sigma reach).
        val reach = strokePx / 2f + 2f * blurPx
        val halo = RectF(geometry.bounds).apply { inset(-reach, -reach) }
        val area = Rect()
        if (halo.intersect(0f, 0f, width, height)) halo.roundOut(area)
        bitmap = area.takeIf { !it.isEmpty }?.let { render(geometry, it, strokePx, blurPx) }
        // Corners curve inward by at most their radius, so past reach + radius from the bounds there is no halo.
        val inset = reach + geometry.cornerRadius
        val hollow = RectF(geometry.bounds).apply { inset(inset, inset) }
        val destinations = if (hollow.width() > 0f && hollow.height() > 0f) {
            val left = ceil(hollow.left).toInt()
            val top = ceil(hollow.top).toInt()
            val right = floor(hollow.right).toInt()
            val bottom = floor(hollow.bottom).toInt()
            listOf(
                Rect(area.left, area.top, area.right, top),
                Rect(area.left, bottom, area.right, area.bottom),
                Rect(area.left, top, left, bottom),
                Rect(right, top, area.right, bottom),
            ).filter { it.intersect(area) }
        } else {
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
