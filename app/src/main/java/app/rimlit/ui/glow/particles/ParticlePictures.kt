package app.rimlit.ui.glow.particles

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import app.rimlit.R
import app.rimlit.data.ParticleImageStore
import app.rimlit.model.ParticleImage
import app.rimlit.model.ParticlePicture

private const val TAG = "ParticlePictures"

/** An app's icon is rasterised once at this size, about the largest particle on a dense screen. */
private const val ICON_PX = 192

/** A particle picture as a glow draws it: the app whose icon [ParticlePicture.AppIcon] is (`null`: Rimlit). */
internal data class PictureKey(val picture: ParticlePicture, val iconPackage: String?)

/**
 * The images [key] draws, loaded once per picture so retuning the particles (their size, their tint) reuses them;
 * empty when none of its custom images is stored any more. Main thread: built-in vectors, an icon, or a few small
 * stored PNGs (see [ParticleImageStore]).
 */
internal fun loadPicture(context: Context, store: ParticleImageStore, key: PictureKey): List<Drawable> =
    when (val picture = key.picture) {
        is ParticlePicture.Builtin ->
            listOf(checkNotNull(ContextCompat.getDrawable(context, picture.image.drawableRes)).mutate())

        is ParticlePicture.Custom -> picture.ids.mapNotNull(store::load).map { it.toDrawable(context.resources) }

        ParticlePicture.AppIcon -> listOf(appIcon(context, key.iconPackage ?: context.packageName))
    }

/**
 * [packageName]'s icon, rasterised here so a broken one fails now rather than mid-frame; the system's generic icon
 * when the app is gone (uninstalled since it posted, or not visible to Rimlit) or its icon doesn't load. Another
 * app's resources are untrusted input: no failure of theirs may take down the process, and the listener with it.
 */
@Suppress("TooGenericExceptionCaught") // Untrusted input from any app: every failure falls back, none crashes.
private fun appIcon(context: Context, packageName: String): Drawable {
    val packages = context.packageManager
    val icon = try {
        packages.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX)
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(TAG, "No icon for $packageName", e)
        null
    } catch (e: RuntimeException) {
        Log.w(TAG, "Skipping $packageName's broken icon", e)
        null
    }
    return icon?.toDrawable(context.resources) ?: packages.defaultActivityIcon.mutate()
}

@get:DrawableRes
private val ParticleImage.drawableRes: Int
    get() = when (this) {
        ParticleImage.SNOWFLAKE -> R.drawable.particle_snowflake
        ParticleImage.PETAL -> R.drawable.particle_petal
        ParticleImage.RAINDROP -> R.drawable.particle_raindrop
        ParticleImage.STAR -> R.drawable.particle_star
        ParticleImage.HEART -> R.drawable.particle_heart
        ParticleImage.BUBBLE -> R.drawable.particle_bubble
        ParticleImage.CONFETTI -> R.drawable.particle_confetti
        ParticleImage.SPARK -> R.drawable.particle_spark
    }
