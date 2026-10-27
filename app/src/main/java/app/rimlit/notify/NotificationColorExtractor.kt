package app.rimlit.notify

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.BundleCompat
import androidx.palette.graphics.Palette
import app.rimlit.model.DEFAULT_GLOW_ARGB
import app.rimlit.model.hueOf
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

/** Derives a 1–3 color palette from a notification's imagery, falling back to the app icon. */
object NotificationColorExtractor {

    private const val TAG = "NotificationColorExtractor"

    private const val MAX_COLORS = 3

    /** Minimum hue distance (degrees) for a swatch to count as a distinct palette color. */
    private const val MIN_HUE_DISTANCE = 25f

    /** How long a notification's image may take to load, waiting for [extract]'s lane included. */
    const val IMAGE_LOAD_TIMEOUT_MS = 1_000L

    /**
     * The palette of the imagery of the notification [loadNotification] returns, else of [packageName]'s icon: also
     * when the notification is gone or its image doesn't load within [IMAGE_LOAD_TIMEOUT_MS]. The notification and
     * its image load on [imageLane] (built with that timeout), since another app's URI can block indefinitely; the
     * app icon and the palette run on the caller's dispatcher, which must allow blocking. A cancelled caller skips
     * the palette.
     */
    suspend fun extract(
        context: Context,
        packageName: String,
        imageLane: AbandoningLane,
        loadNotification: () -> Notification?,
    ): List<Int> {
        val image = imageLane.load { loadNotification()?.let { notificationImage(context, it) } }
        val bitmap = image
            ?: untrusted("app icon") { appIcon(context, packageName) }
            ?: return listOf(DEFAULT_GLOW_ARGB)
        currentCoroutineContext().ensureActive()
        return paletteOf(bitmap)
    }

    /**
     * The notification's own imagery: its big picture, then its large icon. Both are read as any [Icon] type
     * (bitmap, resource, URI, adaptive), not only plain bitmaps. A source that fails to load is skipped.
     */
    private fun notificationImage(context: Context, notification: Notification): Bitmap? {
        val extras = notification.extras
        return untrusted("big picture") {
            extras?.let { BundleCompat.getParcelable(it, Notification.EXTRA_PICTURE, Bitmap::class.java) }
        } ?: untrusted("picture icon") {
            if (extras != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BundleCompat.getParcelable(extras, Notification.EXTRA_PICTURE_ICON, Icon::class.java)
                    ?.loadDrawable(context)
                    ?.toBitmap()
            } else {
                null
            }
        } ?: untrusted("large icon") { notification.getLargeIcon()?.loadDrawable(context)?.toBitmap() }
    }

    /**
     * [load]s one image of another app's (its notification's imagery or its icon) as a software bitmap `Palette` can
     * read. That is untrusted input (an undecodable URI, a missing permission, a broken resource, a hardware bitmap),
     * so any failure only skips this source: it must never take the process, and the listener with it, down.
     */
    @Suppress("TooGenericExceptionCaught") // Untrusted input from any app: every failure falls back, none crashes.
    private inline fun untrusted(source: String, load: () -> Bitmap?): Bitmap? = try {
        load()?.let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
    } catch (e: RuntimeException) {
        Log.w(TAG, "Skipping the posting app's $source", e)
        null
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
        val diff = abs(hueOf(a) - hueOf(b))
        return minOf(diff, 360f - diff)
    }

    /** The posting app's icon; `null` if the app was uninstalled since it posted. */
    private fun appIcon(context: Context, packageName: String): Bitmap? = try {
        context.packageManager.getApplicationIcon(packageName).toBitmap()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
