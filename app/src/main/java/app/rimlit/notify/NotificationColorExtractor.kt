package app.rimlit.notify

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.BundleCompat
import androidx.palette.graphics.Palette
import app.rimlit.model.DEFAULT_GLOW_ARGB
import app.rimlit.model.hueOf

/** Derives a 1–3 color palette from a notification's imagery, falling back to the app icon. */
object NotificationColorExtractor {

    private const val MAX_COLORS = 3

    /** Minimum hue distance (degrees) for a swatch to count as a distinct palette color. */
    private const val MIN_HUE_DISTANCE = 25f

    fun extract(context: Context, sbn: StatusBarNotification): List<Int> {
        val bitmap = notificationImage(context, sbn.notification)
            ?: appIcon(context, sbn.packageName)
            ?: return listOf(DEFAULT_GLOW_ARGB)
        return paletteOf(bitmap)
    }

    /**
     * The notification's own imagery: its big picture, then its large icon. Both are read as any [Icon] type
     * (bitmap, resource, URI, adaptive), not only plain bitmaps.
     */
    private fun notificationImage(context: Context, notification: Notification): Bitmap? {
        val extras = notification.extras
        val picture = extras?.let { BundleCompat.getParcelable(it, Notification.EXTRA_PICTURE, Bitmap::class.java) }
        val pictureIcon = if (extras != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BundleCompat.getParcelable(extras, Notification.EXTRA_PICTURE_ICON, Icon::class.java)
        } else {
            null
        }
        return picture
            ?: pictureIcon?.loadDrawable(context)?.toBitmap()
            ?: notification.getLargeIcon()?.loadDrawable(context)?.toBitmap()
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
        val diff = kotlin.math.abs(hueOf(a) - hueOf(b))
        return minOf(diff, 360f - diff)
    }

    /** The posting app's icon; `null` if the app was uninstalled since it posted. */
    private fun appIcon(context: Context, packageName: String): Bitmap? = try {
        context.packageManager.getApplicationIcon(packageName).toBitmap()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
