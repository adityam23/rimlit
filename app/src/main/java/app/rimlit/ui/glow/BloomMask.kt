package app.rimlit.ui.glow

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap

/**
 * The soft halo around [geometry], blurred once into a [width]×[height] alpha-only bitmap. Drawing an
 * alpha-only bitmap takes its color (or shader) from the paint, so each frame just tints this mask
 * instead of re-blurring the outline. [blurPx] must be positive.
 */
internal fun bloomMask(geometry: GlowGeometry, width: Int, height: Int, strokePx: Float, blurPx: Float): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeJoin = Paint.Join.ROUND
        maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
    }
    return createBitmap(width, height, Bitmap.Config.ALPHA_8).also { Canvas(it).drawPath(geometry.path, paint) }
}
