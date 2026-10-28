package app.rimlit.ui.glow.particles

import app.rimlit.model.ParticleMotion
import app.rimlit.model.ParticleSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The most particles one glow draws at once: the bound on a frame's cost. */
internal const val MAX_PARTICLES = 120

/** How far, in sprite sizes, a particle's centre may stray outside the canvas: enough to enter and leave unseen. */
internal const val PARTICLE_REACH = 1.5f

/** Particles per burst of [ParticleMotion.POP]. */
internal const val BURST_SIZE = 8

/** Seconds the farthest (slowest) particle takes over its path at speed 1; speed alone scales it. */
private val ParticleMotion.lifetimeSeconds: Float
    get() = when (this) {
        ParticleMotion.FALL -> 4f
        ParticleMotion.RISE -> 4.5f
        ParticleMotion.POP -> 1.4f
        ParticleMotion.DRIFT -> 3f
    }

/**
 * How many particles a glow draws: its [amount] (a share of [MAX_PARTICLES]), of which [share] plays. [motion]'s
 * bursts ([ParticleMotion.POP]) round to whole bursts, so none is lopsided.
 */
internal fun particleCount(motion: ParticleMotion, amount: Float, share: Float): Int {
    val count = (amount * share * MAX_PARTICLES).roundToInt()
    val whole = if (motion == ParticleMotion.POP) (count + BURST_SIZE / 2) / BURST_SIZE * BURST_SIZE else count
    return whole.coerceIn(if (motion == ParticleMotion.POP) BURST_SIZE else 1, MAX_PARTICLES)
}

/** One particle's pose in one frame. Reused from frame to frame, so a frame allocates nothing. */
internal class Particle {
    /** Centre, in canvas pixels. */
    var x = 0f
    var y = 0f
    var degrees = 0f

    /** Size relative to the sprite, at most 1. */
    var scale = 1f
    var alpha = 1f

    /** Which of the field's tints colours it. */
    var tint = 0
}

/** The canvas particles move over, and a particle's size there at scale 1, all in pixels. */
internal data class ParticleArea(val width: Float, val height: Float, val spritePx: Float)

/**
 * [count] particles moving as [spec] says over [area], for a glow holding [holdMs]. Stateless: a
 * particle's pose is a pure function of ([seed], index, t), so a frame keeps nothing from the last one and the same
 * inputs always give the same picture.
 *
 * Each index is a slot that is never empty while the glow spawns: it spawns one particle as the last one ends, so
 * [count] is how many are out at once. A slot's particles live [life] each, from its own random phase, so at the
 * glow's first frame every slot already has one partway along its path (the field starts full, faded in by
 * [ParticleTimeline.envelope]). Each spawn (a slot's generation) draws its own random place and spin; bursts
 * ([ParticleMotion.POP]) are slots spawning together. A centre never strays more than [PARTICLE_REACH] sprites
 * outside the canvas.
 */
internal class ParticleField(
    private val spec: ParticleSpec,
    val count: Int,
    /** How many tints (sprite colours) particles pick from; at least 1. */
    private val tints: Int,
    holdMs: Long,
    area: ParticleArea,
    private val seed: Int,
) {
    val timeline = ParticleTimeline(holdMs)

    /** The farthest (slowest) particle's life, in seconds: set by the speed alone, never by the glow's length. */
    val lifetime = spec.motion.lifetimeSeconds / spec.speed

    private val width = area.width
    private val height = area.height
    private val spritePx = area.spritePx
    private val shorter = min(width, height)

    /** How long each of [slot]'s particles lives, from its depth: nearer slots move quicker. */
    private fun life(slot: Int): Float = lifetime * (1f - NEAR_SPEEDUP * (1f - depth(slot)))

    /** 0 is the nearest: nearer particles are bigger, brighter and quicker, for a sense of depth. */
    private fun depth(slot: Int): Float = noise(slot, SLOT, DEPTH)

    /** The slot particle [index] spawns in: its burst's for [ParticleMotion.POP], its own otherwise. */
    private fun slotOf(index: Int): Int = if (spec.motion == ParticleMotion.POP) index / BURST_SIZE else index

    /** Poses particle [index] at [t] seconds into the glow into [into]; `false` when it isn't out then. */
    fun at(index: Int, t: Float, into: Particle): Boolean {
        val local = timeline.local(t)
        val slot = slotOf(index)
        val life = life(slot)
        val phase = noise(slot, SLOT, PHASE)
        // At least -1: the particle that is already out at the glow's first frame.
        val generation = floor(local / life - phase).toInt()
        val birth = (generation + phase) * life
        if (birth > timeline.spawnEnd) return false
        val age = local - birth
        val depth = depth(slot)

        into.scale = 1f - FAR_SHRINK * depth
        into.alpha = (1f - FAR_DIM * depth) * timeline.envelope(local)
        into.tint = (noise(slot, generation, TINT) * tints).toInt().coerceAtMost(tints - 1)
        into.degrees = spin(index, generation, age)
        val progress = (age / life).coerceIn(0f, 1f)
        when (spec.motion) {
            ParticleMotion.FALL -> sway(index, generation, age, progress, into, rising = false)
            ParticleMotion.RISE -> sway(index, generation, age, progress, into, rising = true)
            ParticleMotion.POP -> burst(index, slot, generation, progress, into)
            ParticleMotion.DRIFT -> drift(index, generation, progress, into)
        }
        return true
    }

    /** Fixed upright without rotation; otherwise from a random angle at up to the spec's spin, either way round. */
    private fun spin(index: Int, generation: Int, age: Float): Float {
        val fastest = spec.rotationDegPerSecond
        if (fastest == 0f) return 0f
        val rate = fastest * (0.5f + 0.5f * noise(index, generation, SPIN))
        val way = if (noise(index, generation, SPIN_WAY) < 0.5f) -1f else 1f
        return 360f * noise(index, generation, ANGLE) + way * rate * age
    }

    /** Down (or up when [rising]) from just out of sight past one edge to just past the other, swaying. */
    private fun sway(index: Int, generation: Int, age: Float, progress: Float, into: Particle, rising: Boolean) {
        val amplitude = spritePx * (SWAY_MIN + (PARTICLE_REACH - SWAY_MIN) * noise(index, generation, SWAY))
        val wave = sin(TAU * (SWAY_HZ * age + noise(index, generation, SWAY_PHASE)))
        val fallen = -spritePx + (height + 2f * spritePx) * progress
        into.x = width * noise(index, generation, X) + amplitude * wave
        into.y = if (rising) height - fallen else fallen
    }

    /** Out from the burst's centre, slowing down, popping up to size and fading as it goes. */
    private fun burst(index: Int, slot: Int, generation: Int, progress: Float, into: Particle) {
        val reach = POP_REACH * shorter
        val centreX = inset(noise(slot, generation, X), width, reach)
        val centreY = inset(noise(slot, generation, Y), height, reach)
        val direction = TAU * ((index % BURST_SIZE) + noise(index, generation, DIRECTION)) / BURST_SIZE
        val distance = reach * (POP_MIN_REACH + (1f - POP_MIN_REACH) * noise(index, generation, DISTANCE)) *
            (1f - (1f - progress) * (1f - progress))
        into.x = centreX + distance * cos(direction)
        into.y = centreY + distance * sin(direction)
        into.scale *= min(1f, POP_START_SCALE + progress * POP_GROWTH)
        into.alpha *= 1f - progress * progress
    }

    /** A short straight float from anywhere on the canvas, fading in and out. */
    private fun drift(index: Int, generation: Int, progress: Float, into: Particle) {
        val reach = DRIFT_REACH * shorter
        val direction = TAU * noise(index, generation, DIRECTION)
        val distance = reach * progress
        into.x = inset(noise(index, generation, X), width, reach) + distance * cos(direction)
        into.y = inset(noise(index, generation, Y), height, reach) + distance * sin(direction)
        into.alpha *= sin(PI.toFloat() * progress)
    }

    /** A random spot ([unit] in [0, 1)) along [extent], [margin] in from both ends; the middle when it can't be. */
    private fun inset(unit: Float, extent: Float, margin: Float): Float =
        if (extent > 2f * margin) margin + unit * (extent - 2f * margin) else extent / 2f

    /** A random number in [0, 1), fixed for ([seed], [index], [generation], [channel]). */
    private fun noise(index: Int, generation: Int, channel: Int): Float {
        val hash = mix(mix(mix(mix(seed) + index) + generation) + channel)
        return (hash ushr FRACTION_SHIFT) * FRACTION_SCALE
    }

    private companion object {
        const val TAU = 2f * PI.toFloat()

        /** How much shorter the nearest particle's life is than the farthest's: nearer ones move quicker. */
        const val NEAR_SPEEDUP = 0.25f
        const val FAR_SHRINK = 0.4f
        const val FAR_DIM = 0.3f

        /** Sway cycles per second, and the smallest sway, in sprite sizes (the largest is [PARTICLE_REACH]). */
        const val SWAY_HZ = 0.5f
        const val SWAY_MIN = 0.3f

        /** A burst's reach, as a share of the canvas's shorter side; each particle flies at least [POP_MIN_REACH]. */
        const val POP_REACH = 0.18f
        const val POP_MIN_REACH = 0.6f
        const val POP_START_SCALE = 0.4f
        const val POP_GROWTH = 3f

        /** How far a drifting particle floats, as a share of the canvas's shorter side. */
        const val DRIFT_REACH = 0.12f

        /** The generation a slot's own random numbers (its phase and depth) are drawn for: no spawn has it. */
        const val SLOT = Int.MIN_VALUE

        // Random channels: one independent number per use.
        const val PHASE = 0
        const val DEPTH = 1
        const val TINT = 2
        const val SPIN = 3
        const val SPIN_WAY = 4
        const val ANGLE = 5
        const val SWAY = 6
        const val SWAY_PHASE = 7
        const val X = 8
        const val Y = 9
        const val DIRECTION = 10
        const val DISTANCE = 11

        /** The top 24 bits of a hash, scaled into [0, 1): exactly representable as floats. */
        const val FRACTION_SHIFT = 8
        const val FRACTION_SCALE = 1f / (1 shl 24)

        /** A 32-bit integer hash (lowbias32): every input bit flips about half the output bits. */
        fun mix(value: Int): Int {
            var h = value
            h = h xor (h ushr 16)
            h *= 0x7feb352d
            h = h xor (h ushr 15)
            h *= -0x7b935975
            return h xor (h ushr 16)
        }
    }
}
