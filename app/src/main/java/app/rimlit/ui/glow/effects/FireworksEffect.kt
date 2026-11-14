package app.rimlit.ui.glow.effects

import android.graphics.Canvas
import android.graphics.RectF
import app.rimlit.ui.glow.GlowOutline
import app.rimlit.ui.glow.OutlinePart
import app.rimlit.ui.glow.OutlineShape
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Times each burst site goes off per second at speed 1. */
private const val BURSTS_PER_SECOND = 0.5f

/** Share of a site's cycle its burst is in the air; it rests for the rest. */
private const val LIFE_SHARE = 0.55f

/** Sparks in one burst, and the most drawn around one outline in a frame: this caps the frame's cost. */
private const val SPARKS_PER_BURST = 10
private const val MAX_SPARKS = 200

/** Fewest and most burst sites around one outline. */
private const val MIN_BURSTS = 4
private const val MAX_BURSTS = MAX_SPARKS / SPARKS_PER_BURST

/** How far the fastest spark flies: line thicknesses plus glow widths. */
private const val REACH_THICKNESSES = 6f
private const val REACH_GLOWS = 1.5f

/**
 * Farthest reach, as a share of the outline's shorter side, so a preview's or the cutout ring's bursts stay close.
 * Raised (lead's call, 2026-10-31) so bursts still read on a small punch-hole ring: at the old 0.12 a 40 px ring's
 * reach was only ~5 px; at 0.55 it is about the ring's own size. The screen edge is unaffected: even at the sliders'
 * highest thickness and glow, its absolute reach stays well under this share of its own (far larger) shorter side.
 */
private const val MAX_REACH_SHARE = 0.55f

/** Arc length per burst site, in reaches, so neighbouring bursts barely overlap. */
private const val SPACING_REACHES = 1.5f

/** Spark radius: line thicknesses plus a share of the glow width, capped to a share of the outline's shorter side. */
private const val RADIUS_THICKNESSES = 1.5f
private const val RADIUS_GLOW_SHARE = 0.15f

/**
 * Largest spark radius, as a share of the outline's shorter side. Raised (lead's call, 2026-10-31) alongside
 * [MAX_REACH_SHARE]: at the old 0.03 a 40 px ring's sparks were only ~1 px, too faint to read; at 0.1 they are at
 * least ~4 px. The screen edge is unaffected for the same reason as the reach share.
 */
private const val MAX_RADIUS_SHARE = 0.1f

/** Angle a burst's sparks spread over, centred on the open side, in radians (about 150°): none skims the edge. */
private const val FAN = 2.6f

/** Slowest spark, as a share of the fastest's reach. */
private const val MIN_RATE = 0.6f

/** Golden-ratio step between neighbouring sites' phases, so each goes off well apart from the last. */
private const val PHASE_STEP = 0.618034f

/** Opacity of the outline the bursts go off from. */
private const val OUTLINE_ALPHA = 0.15f

/** Opacity of a still burst's sparks. */
private const val STILL_ALPHA = 0.7f

/** Below this a spark isn't drawn at all. */
private const val INVISIBLE = 0.02f

/** Seed of the bursts around the screen edge; the cutout ring's is the next one. */
private const val SEED = 0xF12E

/** Where a burst is widest in its life, and where a still one stands. */
internal const val WIDEST_PROGRESS = 1f

/**
 * Bursts of sparks going off from seeded sites along a faint outline, toward the open side (inward from the screen
 * edge, outward from the cutout ring). Each burst fans its sparks out from its site, slowing and fading; neighbouring
 * sites go off at staggered phases, so bursts keep going off. Every spark is the shared pre-blurred glint, tinted by
 * the palette where it is, and its place is a pure function of the layout's seed and [t], so nothing is simulated,
 * blurred or allocated per frame. Still, every burst stands frozen at its widest.
 */
internal class FireworksEffect(private val brush: OutlineBrush) : GlowEffectPainter {
    private val field = brush.outline.part(Bursts)
    private val destination = RectF()

    override fun draw(canvas: Canvas, t: Float) {
        val palette = brush.palette(t)
        brush.drawOutline(canvas, palette, OUTLINE_ALPHA)
        val cycle = t * BURSTS_PER_SECOND * brush.spec.speed
        for (b in 0 until field.count) {
            val progress = if (brush.still) WIDEST_PROGRESS else burstProgress(cycle + field.seeds.phases[b])
            val alpha = if (brush.still) STILL_ALPHA else sparkAlpha(progress)
            if (progress > 1f || alpha < INVISIBLE) continue
            val distance = sparkDistance(progress, field.reach)
            // Sparks dwindle as they fly.
            val half = field.radius * (1f - 0.5f * progress)
            for (s in b * SPARKS_PER_BURST until (b + 1) * SPARKS_PER_BURST) {
                val x = field.x[b] + field.vx[s] * distance
                val y = field.y[b] + field.vy[s] * distance
                destination.set(x - half, y - half, x + half, y + half)
                brush.drawSprite(canvas, field.sprite, destination, palette, alpha)
            }
        }
    }
}

/** How far a burst [cycle] cycles along (any real) is through its life: in [0, 1] while it flies, above 1 at rest. */
internal fun burstProgress(cycle: Float): Float = (cycle - floor(cycle)) / LIFE_SHARE

/** How far the fastest spark has flown [progress] (0 to 1) through its burst, out of [reach]: fast, then slowing. */
internal fun sparkDistance(progress: Float, reach: Float): Float {
    val left = 1f - progress
    return reach * (1f - left * left * left)
}

/** A spark's opacity [progress] (0 to 1) through its burst: brightest as it erupts, gone as it lands. */
internal fun sparkAlpha(progress: Float): Float = (1f - progress) * (1f - progress)

/**
 * Writes into [direction] the unit heading of a spark leaving an outline point whose unit normal toward the open side
 * is ([normalX], [normalY]) (see [OutlineShape.pointAt]), turned [angle] radians from it.
 */
internal fun sparkDirection(normalX: Float, normalY: Float, angle: Float, direction: FloatArray) {
    val cos = cos(angle)
    val sin = sin(angle)
    direction[0] = normalX * cos - normalY * sin
    direction[1] = normalY * cos + normalX * sin
}

/**
 * Where [count] burst sites sit around an outline, when each goes off and how its [sparks] fly, the same for the same
 * [seed]: one site in each even share of the lap, at a random place within it; phases a golden-ratio step apart from a
 * random start; each spark at a random angle within its even share of the fan and a random speed.
 */
internal class BurstSeeds(val count: Int, val sparks: Int, seed: Int) {
    /** Arc position of each site, as a fraction of the lap in [0, 1), in increasing order. */
    val fractions = FloatArray(count)

    /** Burst phase of each site, in cycles in [0, 1). */
    val phases = FloatArray(count)

    /** Heading of each spark (site-major), in radians from the open side's normal, within ±[FAN] / 2. */
    val angles = FloatArray(count * sparks)

    /** Speed of each spark (site-major), as a share of the fastest in [[MIN_RATE], 1). */
    val rates = FloatArray(count * sparks)

    init {
        val random = Random(seed)
        val start = random.nextFloat()
        for (i in 0 until count) {
            fractions[i] = (i + random.nextFloat()) / count
            val phase = start + i * PHASE_STEP
            phases[i] = phase - floor(phase)
            for (s in 0 until sparks) {
                angles[i * sparks + s] = FAN * ((s + random.nextFloat()) / sparks - 0.5f)
                rates[i * sparks + s] = MIN_RATE + (1f - MIN_RATE) * random.nextFloat()
            }
        }
    }
}

/**
 * Farthest reach of a burst's fastest spark on [shape]: thicknesses plus glow widths, capped to [MAX_REACH_SHARE] of
 * the outline's shorter side ([OutlineShape.fit]) so a small outline (the cutout ring, a preview) isn't swamped; at
 * least 1 px.
 */
internal fun burstReach(shape: OutlineShape, thicknessPx: Float, glowPx: Float): Float =
    shape.fit(thicknessPx * REACH_THICKNESSES + glowPx * REACH_GLOWS, MAX_REACH_SHARE).coerceAtLeast(1f)

/** A burst's spark radius on [shape]: the same capping as [burstReach], to [MAX_RADIUS_SHARE]. */
internal fun burstRadius(shape: OutlineShape, thicknessPx: Float, glowPx: Float): Float =
    shape.fit(thicknessPx * RADIUS_THICKNESSES + glowPx * RADIUS_GLOW_SHARE, MAX_RADIUS_SHARE).coerceAtLeast(1f)

/**
 * An outline's burst sites and their sparks' velocities, and the one sprite they all draw, sized against the outline
 * (see [OutlineShape.fit]). Sites sit on its [OutlineShape], and sparks fan out about its normal toward the open side.
 * Built once per layout.
 */
private class BurstField(outline: GlowOutline) {
    private val shape = outline.geometry.shape
    val reach = outline.key.let { burstReach(shape, it.thicknessPx, it.glowPx) }
    val radius = outline.key.let { burstRadius(shape, it.thicknessPx, it.glowPx) }
    val count = (shape.perimeter / (reach * SPACING_REACHES)).roundToInt().coerceIn(MIN_BURSTS, MAX_BURSTS)
    val seeds = BurstSeeds(count, SPARKS_PER_BURST, if (outline.opensInward) SEED else SEED + 1)
    val x = FloatArray(count)
    val y = FloatArray(count)

    /** Each spark's heading scaled by its speed (site-major): its offset from the site per unit of [reach] flown. */
    val vx = FloatArray(count * SPARKS_PER_BURST)
    val vy = FloatArray(count * SPARKS_PER_BURST)
    val sprite = sparkleSprite(radius, core = min(outline.key.thicknessPx * 0.6f, radius * 0.3f))

    init {
        val point = FloatArray(4)
        val direction = FloatArray(2)
        for (b in 0 until count) {
            shape.pointAt(shape.perimeter * seeds.fractions[b], point)
            x[b] = point[0]
            y[b] = point[1]
            for (s in b * SPARKS_PER_BURST until (b + 1) * SPARKS_PER_BURST) {
                sparkDirection(point[2], point[3], seeds.angles[s], direction)
                vx[s] = direction[0] * seeds.rates[s]
                vy[s] = direction[1] * seeds.rates[s]
            }
        }
    }
}

private val Bursts = OutlinePart(::BurstField)
