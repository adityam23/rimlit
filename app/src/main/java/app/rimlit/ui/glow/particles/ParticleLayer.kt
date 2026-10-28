package app.rimlit.ui.glow.particles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
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
    private val sprites = BuildCache<SpriteKey, ParticleSprites> { ParticleSprites(context, it) }

    /**
     * What [spec]'s particles draw as [playback] plays them over a canvas of [size], with [pxPerDp] pixels per dp;
     * `null` when none play.
     */
    fun painter(spec: GlowSpec, playback: Playback, size: Size, pxPerDp: Float): ParticlePainter? {
        val particles = spec.particles?.takeIf { playback.particleShare > 0f } ?: return null
        val sizePx = (particles.sizeDp * pxPerDp).roundToInt().coerceAtLeast(1)
        val sprites = sprites.get(SpriteKey.of(particles.image, sizePx, particles.tint, spec.palette))
        val field = ParticleField(
            spec = particles,
            count = particleCount(particles.motion, particles.amount, playback.particleShare),
            tints = sprites.count,
            holdMs = spec.durationMs,
            area = ParticleArea(size.width, size.height, sizePx.toFloat()),
            seed = seed,
        )
        return ParticlePainter(field, sprites, playback.brightness)
    }
}

/**
 * Draws every live particle of [field] as a sprite, at [brightness]. A frame allocates nothing: one particle, matrix
 * and paint are reused for every draw.
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
        for (index in 0 until field.count) {
            if (!field.at(index, t, particle)) continue
            matrix.setTranslate(-half, -half)
            matrix.postScale(particle.scale, particle.scale)
            matrix.postRotate(particle.degrees)
            matrix.postTranslate(particle.x, particle.y)
            paint.alpha = (255 * particle.alpha * brightness).toInt().coerceIn(0, 255)
            canvas.drawBitmap(sprites[particle.tint], matrix, paint)
        }
    }
}
