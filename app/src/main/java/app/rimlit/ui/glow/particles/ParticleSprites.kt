package app.rimlit.ui.glow.particles

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import app.rimlit.model.GlowPalette
import app.rimlit.model.GlowSpec
import app.rimlit.model.ParticleSpec
import app.rimlit.model.ParticleTint
import app.rimlit.ui.glow.colorAt

/** A rainbow tints its particles with this many evenly spaced hues. */
private const val RAINBOW_TINTS = 8

/** Everything the sprites depend on: speed, amount, rotation and the clock don't, so editing those reuses them. */
internal data class SpriteKey(
    val picture: PictureKey,
    val sizePx: Int,
    /** One sprite per colour; `null` for one sprite in the images' own colours. */
    val tints: List<Int>?,
) {
    companion object {
        fun of(spec: GlowSpec, particles: ParticleSpec, sizePx: Int) = SpriteKey(
            picture = PictureKey(particles.picture, spec.iconPackage),
            sizePx = sizePx,
            tints = when (particles.drawnTint) {
                ParticleTint.IMAGE -> null
                ParticleTint.PALETTE -> spec.palette.tints()
            },
        )

        private fun GlowPalette.tints(): List<Int> = when (this) {
            is GlowPalette.Colors -> argb
            GlowPalette.Rainbow -> List(RAINBOW_TINTS) { GlowPalette.Rainbow.colorAt(it / RAINBOW_TINTS.toFloat()) }
        }
    }
}

/**
 * Each of [images] (at least one) rasterised once per tint of [key], at its full size, so a frame only draws bitmaps:
 * smaller (farther) particles scale them down, never up.
 */
internal class ParticleSprites(images: List<Drawable>, key: SpriteKey) {
    val sizePx = key.sizePx

    private val bitmaps: List<List<Bitmap>> =
        images.map { image -> (key.tints ?: listOf(null)).map { tint -> render(image, tint) } }

    /** How many images particles spread across. */
    val images get() = bitmaps.size

    /** How many tints each image has to pick from. */
    val tints get() = bitmaps.first().size

    operator fun get(image: Int, tint: Int): Bitmap = bitmaps[image][tint]

    private fun render(image: Drawable, tint: Int?): Bitmap {
        image.setBounds(0, 0, sizePx, sizePx)
        // A tint keeps only each pixel's alpha (SRC_IN), so an image with a detail (a shine, a centre, a vein) draws it
        // more opaque than its translucent body: the detail survives the palette tint as a brighter shade of it.
        // Confetti's fold is the reverse, a dimmer shade on an opaque body; star and snowflake have no detail.
        image.setTintList(tint?.let(ColorStateList::valueOf))
        return createBitmap(sizePx, sizePx).also {
            image.draw(Canvas(it))
            it.prepareToDraw()
        }
    }
}
