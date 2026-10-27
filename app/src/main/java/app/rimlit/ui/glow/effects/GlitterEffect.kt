package app.rimlit.ui.glow.effects

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import app.rimlit.ui.glow.GlowOutline
import app.rimlit.ui.glow.OutlinePart
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Twinkles per second of an average sparkle at speed 1. */
private const val TWINKLES_PER_SECOND = 0.9f

/** Sparkle radius: line thicknesses plus a share of the glow width. */
private const val RADIUS_THICKNESSES = 2.5f
private const val RADIUS_GLOW_SHARE = 0.25f

/** Largest sparkle radius, as a share of the window's shorter side, so a preview isn't swamped. */
private const val MAX_RADIUS_SHARE = 0.06f

/** Arc length per sparkle, in sparkle radii. */
private const val SPACING_RADII = 2.5f

/** Fewest and most sparkles around one outline. */
private const val MIN_SPARKLES = 8
private const val MAX_SPARKLES = 200

/** Opacity of the outline the sparkles sit on. */
private const val OUTLINE_ALPHA = 0.15f

/** Still sparkles keep at least this brightness, so none disappears for good. */
private const val STILL_FLOOR = 0.35f

/** Below this a sparkle isn't drawn at all. */
private const val INVISIBLE = 0.02f

/** Seed of the sparkles around the screen edge; the cutout ring's is the next one. */
private const val SEED = 0x5EED

/**
 * Sparkles twinkling along a faint outline, each a pre-blurred sprite tinted by the palette where it sits, so
 * nothing is blurred per frame. Every sparkle twinkles at its own phase and rate; still, each holds its own fixed
 * brightness.
 */
internal class GlitterEffect(private val brush: OutlineBrush) : GlowEffectPainter {
    private val field = brush.outline.part(Sparkles)
    private val seeds = field.seeds
    private val destination = RectF()

    override fun draw(canvas: Canvas, t: Float) {
        val palette = brush.palette(t)
        brush.drawOutline(canvas, palette, OUTLINE_ALPHA)
        val time = t * TWINKLES_PER_SECOND * brush.spec.speed
        for (i in 0 until field.count) {
            val level = if (brush.still) {
                STILL_FLOOR + (1f - STILL_FLOOR) * twinkle(seeds.phases[i])
            } else {
                twinkle(time * seeds.rates[i] + seeds.phases[i])
            }
            if (level < INVISIBLE) continue
            // Sparkles swell as they brighten.
            val half = field.radius * (0.5f + 0.5f * level)
            destination.set(field.x[i] - half, field.y[i] - half, field.x[i] + half, field.y[i] + half)
            brush.drawSprite(canvas, field.sprite, destination, palette, level)
        }
    }
}

/** A sparkle's brightness in [0, 1] at twinkle [cycle] (any real; one flash per cycle): mostly dark, briefly bright. */
internal fun twinkle(cycle: Float): Float {
    val wave = 0.5f - 0.5f * cos(2f * PI.toFloat() * cycle)
    return wave * wave * wave
}

/**
 * Where [count] sparkles sit around an outline and how they twinkle, the same for the same [seed]: one sparkle in
 * each even share of the lap, at a random place within it, with a random twinkle phase and rate.
 */
internal class SparkleSeeds(val count: Int, seed: Int) {
    /** Arc position of each sparkle, as a fraction of the lap in [0, 1), in increasing order. */
    val fractions = FloatArray(count)

    /** Twinkle phase of each sparkle, in cycles in [0, 1). */
    val phases = FloatArray(count)

    /** Twinkle rate of each sparkle, as a multiple of the average in [0.7, 1.3). */
    val rates = FloatArray(count)

    init {
        val random = Random(seed)
        for (i in 0 until count) {
            fractions[i] = (i + random.nextFloat()) / count
            phases[i] = random.nextFloat()
            rates[i] = 0.7f + 0.6f * random.nextFloat()
        }
    }
}

/** An outline's sparkles: where they sit, how they twinkle, and the one sprite they all draw. Built once per layout. */
private class SparkleField(outline: GlowOutline) {
    val radius = outline.key.let {
        min(
            it.thicknessPx * RADIUS_THICKNESSES + it.glowPx * RADIUS_GLOW_SHARE,
            min(it.width, it.height) * MAX_RADIUS_SHARE,
        ).coerceAtLeast(1f)
    }
    val count = (outline.geometry.length / (radius * SPACING_RADII)).roundToInt().coerceIn(MIN_SPARKLES, MAX_SPARKLES)
    val seeds = SparkleSeeds(count, if (outline.opensInward) SEED else SEED + 1)
    val x = FloatArray(count)
    val y = FloatArray(count)
    val sprite = sparkleSprite(radius, core = min(outline.key.thicknessPx * 0.6f, radius * 0.3f))

    init {
        val position = FloatArray(2)
        for (i in 0 until count) {
            outline.geometry.pointAt(outline.geometry.length * seeds.fractions[i], position)
            x[i] = position[0]
            y[i] = position[1]
        }
    }
}

private val Sparkles = OutlinePart(::SparkleField)

/**
 * A four-pointed glint [radius] px across each way from its centre, as an alpha-only mask blurred once: a soft glow,
 * two crossed rays and a bright [core].
 */
private fun sparkleSprite(radius: Float, core: Float): Bitmap {
    val size = ceil(2f * radius).toInt().coerceAtLeast(2)
    val c = size / 2f
    return createBitmap(size, size, Bitmap.Config.ALPHA_8).also {
        val canvas = Canvas(it)
        // A blur reaches about twice its radius, so each blurred piece stays inside the sprite.
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = 150
            maskFilter = BlurMaskFilter(radius * 0.25f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(c, c, radius * 0.35f, glow)
        val rayBlur = (core * 0.3f).coerceAtLeast(0.5f)
        val ray = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = (core * 0.5f).coerceAtLeast(1f)
            strokeCap = Paint.Cap.ROUND
            maskFilter = BlurMaskFilter(rayBlur, BlurMaskFilter.Blur.NORMAL)
        }
        val arm = c - 2f * rayBlur - ray.strokeWidth / 2f
        canvas.drawLine(c - arm, c, c + arm, c, ray)
        canvas.drawLine(c, c - arm, c, c + arm, ray)
        canvas.drawCircle(c, c, core, Paint(Paint.ANTI_ALIAS_FLAG))
    }
}
