package app.rimlit.ui.glow.particles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import app.rimlit.R
import app.rimlit.model.GlowPalette
import app.rimlit.model.ParticleImage
import app.rimlit.model.ParticleTint
import app.rimlit.ui.glow.colorAt

/** A rainbow tints its particles with this many evenly spaced hues. */
private const val RAINBOW_TINTS = 8

/** Everything the sprites depend on: speed, amount, rotation and the clock don't, so editing those reuses them. */
internal data class SpriteKey(
    val image: ParticleImage,
    val sizePx: Int,
    /** One sprite per colour; `null` for one sprite in the image's own colours. */
    val tints: List<Int>?,
) {
    companion object {
        fun of(image: ParticleImage, sizePx: Int, tint: ParticleTint, palette: GlowPalette) = SpriteKey(
            image = image,
            sizePx = sizePx,
            tints = when (tint) {
                ParticleTint.IMAGE -> null
                ParticleTint.PALETTE -> palette.tints()
            },
        )

        private fun GlowPalette.tints(): List<Int> = when (this) {
            is GlowPalette.Colors -> argb
            GlowPalette.Rainbow -> List(RAINBOW_TINTS) { GlowPalette.Rainbow.colorAt(it / RAINBOW_TINTS.toFloat()) }
        }
    }
}

/**
 * [key]'s image rasterised once per tint at its full size, so a frame only draws bitmaps: smaller (farther) particles
 * scale them down, never up.
 */
internal class ParticleSprites(context: Context, key: SpriteKey) {
    val sizePx = key.sizePx

    private val bitmaps: List<Bitmap> = (key.tints ?: listOf(null)).map { render(context, key.image, it) }

    /** How many tints there are to pick from. */
    val count get() = bitmaps.size

    operator fun get(tint: Int): Bitmap = bitmaps[tint]

    private fun render(context: Context, image: ParticleImage, tint: Int?): Bitmap {
        val drawable = checkNotNull(ContextCompat.getDrawable(context, image.drawableRes)).mutate()
        drawable.setBounds(0, 0, sizePx, sizePx)
        // A tint keeps only each pixel's alpha (SRC_IN), so an image with a detail (a shine, a centre, a vein) draws it
        // more opaque than its translucent body: the detail survives the palette tint as a brighter shade of it.
        // Confetti's fold is the reverse, a dimmer shade on an opaque body; star and snowflake have no detail.
        tint?.let(drawable::setTint)
        return createBitmap(sizePx, sizePx).also {
            drawable.draw(Canvas(it))
            it.prepareToDraw()
        }
    }
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
