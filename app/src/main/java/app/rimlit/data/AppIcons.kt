package app.rimlit.data

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val TAG = "AppIcons"

/**
 * [packageName]'s icon, rasterised to a [sizePx] square here so a broken one fails now rather than mid-draw; `null`
 * when the app is gone (uninstalled, or not visible to Rimlit) or its icon doesn't load. Its resources are untrusted
 * input, and no failure of theirs may take down the process (the listener with it). The notification colours load the
 * icon at its own size, under their own untrusted-load guard (`NotificationColorExtractor`).
 */
@Suppress("TooGenericExceptionCaught") // Untrusted input from any app: every failure falls back, none crashes.
fun PackageManager.appIcon(packageName: String, sizePx: Int): Bitmap? = try {
    getApplicationIcon(packageName).toBitmap(sizePx, sizePx)
} catch (e: PackageManager.NameNotFoundException) {
    Log.w(TAG, "No icon for $packageName", e)
    null
} catch (e: RuntimeException) {
    Log.w(TAG, "Skipping $packageName's broken icon", e)
    null
}

/**
 * Apps' icons for lists, each at the pixel size its caller draws it at: loaded on [ioDispatcher], and the most recent
 * kept in memory, up to [MAX_CACHED_BYTES] of bitmaps, so scrolling back draws them at once. An app with no icon that
 * loads is remembered as none until it falls out.
 */
class AppIcons(private val packageManager: PackageManager, private val ioDispatcher: CoroutineDispatcher) {
    private val cache = object : LruCache<Key, Loaded>(MAX_CACHED_BYTES) {
        // "No icon" costs a byte, so even those are bounded.
        override fun sizeOf(key: Key, value: Loaded): Int = value.icon?.allocationByteCount ?: 1
    }

    /** [packageName]'s icon at [sizePx] if already loaded: `null` while not yet, [Loaded.icon] `null` for none. */
    fun cached(packageName: String, sizePx: Int): Loaded? = cache[Key(packageName, sizePx)]

    /** [packageName]'s icon as a [sizePx] square, loaded once and then [cached]. */
    suspend fun load(packageName: String, sizePx: Int): Loaded =
        cached(packageName, sizePx) ?: withContext(ioDispatcher) {
            Loaded(packageManager.appIcon(packageName, sizePx)).also { cache.put(Key(packageName, sizePx), it) }
        }

    /** A load's result, so "no icon" is cached as well as an icon. */
    class Loaded(val icon: Bitmap?)

    private data class Key(val packageName: String, val sizePx: Int)

    private companion object {
        /** About two screens of rows each way even at the densest screens' 160 px icons (100 KB each). */
        const val MAX_CACHED_BYTES = 8 * 1024 * 1024
    }
}
