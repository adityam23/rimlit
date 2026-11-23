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
 * Apps' icons at one small [sizePx], for lists: loaded on [ioDispatcher], and the last [MAX_CACHED] kept in memory
 * so scrolling back draws them at once. An app with no icon that loads is remembered as none until it falls out.
 */
class AppIcons(
    private val packageManager: PackageManager,
    private val sizePx: Int,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val cache = LruCache<String, Loaded>(MAX_CACHED)

    /** [packageName]'s icon if already loaded: `null` while not yet, [Loaded.icon] `null` for none. */
    fun cached(packageName: String): Loaded? = cache[packageName]

    /** [packageName]'s icon, loaded once and then [cached]. */
    suspend fun load(packageName: String): Loaded = cached(packageName) ?: withContext(ioDispatcher) {
        Loaded(packageManager.appIcon(packageName, sizePx)).also { cache.put(packageName, it) }
    }

    /** A load's result, so "no icon" is cached as well as an icon. */
    class Loaded(val icon: Bitmap?)

    private companion object {
        /** About two screens of rows each way: small icons, a few MB at most. */
        const val MAX_CACHED = 200
    }
}
