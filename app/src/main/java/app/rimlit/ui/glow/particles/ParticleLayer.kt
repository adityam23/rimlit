package app.rimlit.ui.glow.particles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import app.rimlit.data.ParticleImageStore
import app.rimlit.model.GlowSpec
import app.rimlit.ui.glow.BuildCache
import app.rimlit.ui.glow.Playback
import kotlin.math.roundToInt
import kotlin.random.Random

/** The particle layer of one composed glow: its seed and its sprites, kept while only the clock moves. */
@Composable
internal fun rememberParticleLayer(): ParticleLayer {
    val context = LocalContext.current
    return remember(context) { ParticleLayer(context) }
}

/**
 * Builds the painter for a glow's particles. Each composed glow gets its own random seed, so no two glows scatter
 * their particles alike, while one glow's picture stays a pure function of the clock.
 */
internal class ParticleLayer(context: Context) {
    private val seed = Random.nextInt()
    private val store = ParticleImageStore(context)
    private val pictures = BuildCache<PictureKey, List<Drawable>> { loadPicture(context, store, it) }
    private val sprites = BuildCache<SpriteKey, ParticleSprites> { ParticleSprites(pictures.get(it.picture), it) }

    /**
     * What [spec]'s particles draw as [playback] plays them over a canvas of [size], with [pxPerDp] pixels per dp, in
     * an in-app [preview] or on a surface; `null` when none play, or none of their images is stored any more.
     */
    fun painter(spec: GlowSpec, playback: Playback, size: Size, pxPerDp: Float, preview: Boolean): ParticlePainter? {
        val particles = spec.particles?.takeIf { playback.particleShare > 0f } ?: return null
        val sizePx = (particles.sizeDp * pxPerDp).roundToInt().coerceAtLeast(1)
        val key = SpriteKey.of(spec, particles, sizePx)
        if (pictures.get(key.picture).isEmpty()) return null
        val sprites = sprites.get(key)
        val field = ParticleField(
            spec = particles,
            count = particleCount(particles.motion, particles.amount, playback.particleShare),
            tints = sprites.tints,
            timeline = ParticleTimeline(spec.durationMs, preview),
            area = ParticleArea(size.width, size.height, sizePx.toFloat()),
            seed = seed,
        )
        return ParticlePainter(field, sprites, playback.brightness)
    }
}

/**
 * Draws every live particle of [field] as a sprite, at [brightness], spreading them across the sprites' images by
 * index. A frame allocates nothing: one particle, matrix and paint are reused for every draw.
 */
internal class ParticlePainter(
    private val field: ParticleField,
    private val sprites: ParticleSprites,
    private val brightness: Float,
) {
    private val particle = Particle()
    private val matrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val half = sprites.sizePx / 2f

    /** Draws the particles at [t] seconds into the glow. */
    fun draw(canvas: Canvas, t: Float) {
        val local = field.timeline.local(t)
        val opacity = 255 * field.timeline.opacity(t) * brightness
        for (index in 0 until field.count) {
            if (!field.at(index, local, particle)) continue
            matrix.setTranslate(-half, -half)
            matrix.postScale(particle.scale, particle.scale)
            matrix.postRotate(particle.degrees)
            matrix.postTranslate(particle.x, particle.y)
            paint.alpha = (opacity * particle.alpha).toInt().coerceIn(0, 255)
            canvas.drawBitmap(sprites[index % sprites.images, particle.tint], matrix, paint)
        }
    }
}
