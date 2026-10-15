package app.rimlit.notify

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import app.rimlit.model.DEFAULT_GLOW_ARGB

/** Derives a 1–3 color palette from a notification's imagery, falling back to the app icon. */
object NotificationColorExtractor {

    private const val MAX_COLORS = 3

    /** Minimum hue distance (degrees) for a swatch to count as a distinct palette color. */
    private const val MIN_HUE_DISTANCE = 25f

    @Suppress("DEPRECATION")
    fun extract(context: Context, sbn: StatusBarNotification): List<Int> {
        val notification = sbn.notification
        val bitmap = notification.extras?.get(Notification.EXTRA_PICTURE) as? Bitmap
            ?: notification.largeIcon
            ?: loadAppIcon(context, sbn.packageName)
            ?: return listOf(DEFAULT_GLOW_ARGB)
        return paletteOf(bitmap)
    }

    /** Vivid swatches first (they glow best), keeping only hues distinct from those already picked. */
    private fun paletteOf(bitmap: Bitmap): List<Int> {
        val palette = Palette.from(bitmap).generate()
        val candidates = listOfNotNull(
            palette.vibrantSwatch,
            palette.lightVibrantSwatch,
            palette.darkVibrantSwatch,
            palette.dominantSwatch,
            palette.mutedSwatch,
        ).map { it.rgb }
        val picked = mutableListOf<Int>()
        for (color in candidates) {
            if (picked.size == MAX_COLORS) break
            if (picked.none { hueDistance(it, color) < MIN_HUE_DISTANCE }) picked += color
        }
        return picked.ifEmpty { listOf(DEFAULT_GLOW_ARGB) }
    }

    private fun hueDistance(a: Int, b: Int): Float {
        val diff = kotlin.math.abs(hue(a) - hue(b))
        return minOf(diff, 360f - diff)
    }

    private fun hue(color: Int): Float = FloatArray(3).also { Color.colorToHSV(color, it) }[0]

    private fun loadAppIcon(context: Context, packageName: String): Bitmap? = runCatching {
        context.packageManager.getApplicationIcon(packageName).toBitmap()
    }.getOrNull()
}
