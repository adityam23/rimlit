package app.rimlit.ui.glow.effects

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import kotlin.math.ceil

/**
 * A four-pointed glint [radius] px across each way from its centre, as an alpha-only mask blurred once: a soft glow,
 * two crossed rays and a bright [core].
 */
internal fun sparkleSprite(radius: Float, core: Float): Bitmap {
    val size = ceil(2f * radius).toInt().coerceAtLeast(2)
    val c = size / 2f
    return createBitmap(size, size, Bitmap.Config.ALPHA_8).also {
        val canvas = Canvas(it)
        // A blur reaches about twice its radius, so each blurred piece stays inside the sprite.
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = 150
            maskFilter = BlurMaskFilter(radius * 0.25f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(c, c, radius * 0.35f, glow)
        val rayBlur = (core * 0.3f).coerceAtLeast(0.5f)
        val ray = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = (core * 0.5f).coerceAtLeast(1f)
            strokeCap = Paint.Cap.ROUND
            maskFilter = BlurMaskFilter(rayBlur, BlurMaskFilter.Blur.NORMAL)
        }
        val arm = c - 2f * rayBlur - ray.strokeWidth / 2f
        canvas.drawLine(c - arm, c, c + arm, c, ray)
        canvas.drawLine(c, c - arm, c, c + arm, ray)
        canvas.drawCircle(c, c, core, Paint(Paint.ANTI_ALIAS_FLAG))
    }
}
