package app.rimlit.ui.glow.effects

import android.graphics.Canvas
import android.graphics.RectF
import app.rimlit.ui.glow.GlowOutline
import app.rimlit.ui.glow.OutlinePart
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Twinkles per second of an average sparkle at speed 1. */
private const val TWINKLES_PER_SECOND = 0.9f

/** Sparkle radius: line thicknesses plus a share of the glow width. */
private const val RADIUS_THICKNESSES = 2.5f
private const val RADIUS_GLOW_SHARE = 0.25f

/** Largest sparkle radius, as a share of the outline's shorter side, so a preview or the cutout ring isn't swamped. */
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

/**
 * Radius of the sparkles around an outline [length] long: [wanted], shrunk so the fewest sparkles still fit around
 * it spaced apart rather than overlapping; at least 1 px.
 */
internal fun sparkleRadius(wanted: Float, length: Float): Float =
    min(wanted, length / (MIN_SPARKLES * SPACING_RADII)).coerceAtLeast(1f)

/** How many sparkles of [radius] fit around an outline [length] long, spaced apart. */
internal fun sparkleCount(radius: Float, length: Float): Int =
    (length / (radius * SPACING_RADII)).roundToInt().coerceIn(MIN_SPARKLES, MAX_SPARKLES)

/** An outline's sparkles: where they sit, how they twinkle, and the one sprite they all draw. Built once per layout. */
private class SparkleField(outline: GlowOutline) {
    val radius = outline.key.let {
        val wanted = it.thicknessPx * RADIUS_THICKNESSES + it.glowPx * RADIUS_GLOW_SHARE
        sparkleRadius(outline.geometry.shape.fit(wanted, MAX_RADIUS_SHARE), outline.geometry.length)
    }
    val count = sparkleCount(radius, outline.geometry.length)
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
