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
private const val BURST_SIZE = 8

/** Seconds a particle takes over its path at speed 1, before [ParticleTimeline] fits it to a short glow. */
private val ParticleMotion.lifetimeSeconds: Float
    get() = when (this) {
        ParticleMotion.FALL -> 4f
        ParticleMotion.RISE -> 4.5f
        ParticleMotion.POP -> 1.4f
        ParticleMotion.DRIFT -> 3f
    }

/** How many particles a glow draws: its [amount] (a share of [MAX_PARTICLES]), of which [share] plays. */
internal fun particleCount(amount: Float, share: Float): Int =
    (amount * share * MAX_PARTICLES).roundToInt().coerceIn(1, MAX_PARTICLES)

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

    /** How far through its life it is, in [0, 1): a slot's next particle starts again from 0. */
    var progress = 0f
}

/** The canvas particles move over, and a particle's size there at scale 1, all in pixels. */
internal data class ParticleArea(val width: Float, val height: Float, val spritePx: Float)

/**
 * [count] particles moving as [spec] says over [area], for a glow holding [holdMs]. Stateless: a
 * particle's pose is a pure function of ([seed], index, t), so a frame keeps nothing from the last one and the same
 * inputs always give the same picture.
 *
 * Each index is a slot that spawns one particle after another, every [ParticleTimeline.lifetime] from its own random
 * phase, while the timeline still spawns. Each spawn (a slot's generation) draws its own random place, depth and
 * spin. Bursts ([ParticleMotion.POP]) are slots spawning together. A centre never strays more than
 * [PARTICLE_REACH] sprites outside the canvas.
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
    val timeline = ParticleTimeline(holdMs, spec.motion.lifetimeSeconds / spec.speed)

    private val width = area.width
    private val height = area.height
    private val spritePx = area.spritePx
    private val shorter = min(width, height)

    /** Poses particle [index] at [t] seconds into the glow into [into]; `false` when it isn't alive then. */
    fun at(index: Int, t: Float, into: Particle): Boolean {
        val lifetime = timeline.lifetime
        val local = timeline.local(t)
        val slot = if (spec.motion == ParticleMotion.POP) index / BURST_SIZE else index
        val phase = noise(slot, -1, PHASE)
        val generation = floor(local / lifetime - phase).toInt()
        if (generation < 0) return false
        val birth = (generation + phase) * lifetime
        if (birth > timeline.spawnEnd) return false
        // 0 is the nearest: nearer particles are bigger, brighter and quicker, for a sense of depth.
        val depth = noise(index, generation, DEPTH)
        val life = lifetime * (1f - NEAR_SPEEDUP * (1f - depth))
        val age = local - birth
        if (age >= life) return false

        into.scale = 1f - FAR_SHRINK * depth
        into.alpha = 1f - FAR_DIM * depth
        into.tint = (noise(slot, generation, TINT) * tints).toInt().coerceAtMost(tints - 1)
        into.degrees = spin(index, generation, age)
        val progress = age / life
        into.progress = progress
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
