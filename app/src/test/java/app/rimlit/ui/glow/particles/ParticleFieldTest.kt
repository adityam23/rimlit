package app.rimlit.ui.glow.particles

import app.rimlit.engine.FADE_IN_MS
import app.rimlit.engine.FADE_OUT_MS
import app.rimlit.model.GlowSettings
import app.rimlit.model.ParticleImage
import app.rimlit.model.ParticleMotion
import app.rimlit.model.ParticleSpec
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ParticleFieldTest {

    private val ranges = GlowSettings.Ranges

    /** A phone at the largest particle size, a landscape one, and an in-app preview stand-in. */
    private val areas = listOf(
        ParticleArea(1080f, 2400f, spritePx = 132f),
        ParticleArea(2400f, 1080f, spritePx = 132f),
        ParticleArea(330f, 660f, spritePx = 10f),
    )
    private val holds = listOf(ranges.durationMs.first, GlowSettings().durationMs, ranges.durationMs.last)
    private val speeds = listOf(ranges.particleSpeed.start, 1f, ranges.particleSpeed.endInclusive)

    private data class Pose(
        val x: Float,
        val y: Float,
        val degrees: Float,
        val scale: Float,
        val alpha: Float,
        val tint: Int,
        val progress: Float,
    )

    private fun Particle.pose() = Pose(x, y, degrees, scale, alpha, tint, progress)

    private fun field(
        motion: ParticleMotion,
        holdMs: Long = GlowSettings().durationMs,
        speed: Float = 1f,
        area: ParticleArea = areas.first(),
        seed: Int = 7,
        share: Float = 1f,
    ): ParticleField {
        val amount = ranges.particleAmount.endInclusive
        val spec = ParticleSpec(ParticleImage.STAR, motion, speed = speed, amount = amount, rotationDegPerSecond = 90f)
        return ParticleField(spec, particleCount(amount, share), tints = 3, holdMs, area, seed)
    }

    /** The live particles at [t], by index. */
    private fun ParticleField.poses(t: Float): Map<Int, Pose> {
        val particle = Particle()
        return (0 until count).mapNotNull { index -> if (at(index, t, particle)) index to particle.pose() else null }
            .toMap()
    }

    /** Every [step] seconds through one whole glow. */
    private fun ParticleField.times(step: Float = 0.01f) = generateSequence(0f) { it + step }
        .takeWhile { it < timeline.cycle }

    private class Case(val field: ParticleField, val motion: ParticleMotion, val area: ParticleArea, val holdMs: Long) {
        override fun toString() = "$motion over $area, hold $holdMs ms"
    }

    /** Every motion, over every area, for the shortest, default and longest holds, at the slowest to fastest speed. */
    private fun everyField(block: (Case) -> Unit) {
        for (motion in ParticleMotion.entries) for (area in areas) for (hold in holds) for (speed in speeds) {
            block(Case(field(motion, hold, speed, area), motion, area, hold))
        }
    }

    @Test
    fun theSameSeedAndTimeAlwaysGiveTheSamePicture() {
        for (motion in ParticleMotion.entries) {
            val one = field(motion)
            val other = field(motion)
            val reseeded = field(motion, seed = 8)
            val times = one.times(step = 0.05f).toList()
            val forwards = times.map { one.poses(it) }
            // Out of order and from a different instance: nothing carries over from one frame to the next.
            assertEquals(forwards.reversed(), times.reversed().map { other.poses(it) }, "$motion")
            assertTrue(forwards.any { it.isNotEmpty() }, "$motion draws something")
            assertNotEquals(forwards, times.map { reseeded.poses(it) }, "$motion: another seed, another picture")
        }
    }

    @Test
    fun particlesStayWithinReachOfTheCanvas() {
        everyField { case ->
            val area = case.area
            val reach = PARTICLE_REACH * area.spritePx
            for (t in case.field.times()) for ((index, pose) in case.field.poses(t)) {
                val where = "$case: particle $index at $t s is $pose"
                assertTrue(pose.x in -reach..area.width + reach && pose.y in -reach..area.height + reach, where)
                assertTrue(pose.alpha in 0f..1f && pose.scale > 0f && pose.scale <= 1f && pose.tint in 0 until 3, where)
            }
        }
    }

    @Test
    fun spawningStopsSoTheLastParticlesLeaveByTheEndOfTheFadeOut() {
        everyField { case ->
            val field = case.field
            val timeline = field.timeline
            assertEquals((FADE_IN_MS + case.holdMs + FADE_OUT_MS) / 1000f, timeline.cycle, 1e-4f, "$case")
            assertEquals(timeline.cycle, timeline.spawnEnd + timeline.lifetime, 1e-4f, "$case")
            assertTrue(timeline.lifetime <= timeline.cycle / 2, "$case: spawning covers at least half the glow")

            // Once spawning has ended, particles only ever leave.
            var alive = Int.MAX_VALUE
            for (t in field.times().filter { it > timeline.spawnEnd }) {
                val now = field.poses(t).size
                assertTrue(now <= alive, "$case: a particle spawned at $t s, after ${timeline.spawnEnd} s")
                alive = now
            }
        }
    }

    /**
     * Frame by frame (every millisecond) through one whole glow, each slot's particles come and go only out of sight,
     * and what is left when the glow ends is out of sight too. A burst pops up in sight: that is its look.
     */
    @Test
    fun particlesAppearAndVanishOnlyOutOfSight() {
        everyField { case ->
            val field = case.field
            val particle = Particle()
            val frames = ceil(field.timeline.cycle / FRAME_STEP).toInt()
            for (index in 0 until field.count) {
                var last: Pose? = null
                for (frame in 0 until frames) {
                    val t = frame * FRAME_STEP
                    val pose = if (field.at(index, t, particle)) particle.pose() else null
                    // A slot's next particle starts its life over.
                    val replaced = last != null && pose != null && pose.progress < last.progress
                    if (pose != null && (last == null || replaced) && case.motion != ParticleMotion.POP) {
                        assertTrue(pose.hidden(case.area), "$case: particle $index appears in sight at $t s: $pose")
                    }
                    if (last != null && (pose == null || replaced)) {
                        assertTrue(last.hidden(case.area), "$case: particle $index vanishes in sight at $t s: $last")
                    }
                    last = pose
                }
                last?.let { assertTrue(it.hidden(case.area), "$case: particle $index still shows as the glow ends: $it") }
            }
        }
    }

    @Test
    fun theAmountMapsToACappedCountThatBatterySaverHalves() {
        val most = ranges.particleAmount.endInclusive
        assertEquals(MAX_PARTICLES, particleCount(most, share = 1f))
        assertEquals(MAX_PARTICLES / 2, particleCount(most, share = 0.5f))
        for (amount in listOf(ranges.particleAmount.start, 0.5f, most)) for (share in listOf(0.5f, 1f)) {
            assertTrue(particleCount(amount, share) in 1..MAX_PARTICLES, "amount $amount, share $share")
        }
        for (motion in ParticleMotion.entries) {
            val full = field(motion)
            val half = field(motion, share = 0.5f)
            assertEquals(full.count / 2, half.count)
            val peak = full.times().maxOf { full.poses(it).size }
            assertTrue(peak in 1..MAX_PARTICLES, "$motion peaks at $peak live particles")
        }
    }

    /**
     * The frame budget at the cap: the simulation's share of a frame, and proof that a frame allocates nothing. The
     * sprites' drawing is measured on a device (`dumpsys gfxinfo`).
     */
    @Test
    fun aFrameAtTheCapIsCheapAndAllocatesNothing() {
        for (motion in ParticleMotion.entries) {
            val field = field(motion, holdMs = ranges.durationMs.last)
            val particle = Particle()
            var sink = 0f
            fun frame(t: Float) {
                for (index in 0 until field.count) if (field.at(index, t, particle)) sink += particle.x
            }
            val frames = 20_000
            val step = field.timeline.cycle / frames
            repeat(frames) { frame(it * step) } // Warm-up, so the JIT has compiled the frame.

            val allocatedBefore = allocatedBytes()
            val start = System.nanoTime()
            repeat(frames) { frame(it * step) }
            val microsPerFrame = (System.nanoTime() - start) / 1_000f / frames
            val allocated = allocatedBytes() - allocatedBefore

            println("$motion: ${field.count} particles, %.2f µs per frame, $allocated bytes allocated".format(microsPerFrame))
            assertTrue(microsPerFrame < 500f, "$motion takes $microsPerFrame µs per frame")
            // The measurement's own reflection allocates about a kilobyte; one object per frame would be 16 bytes or more.
            assertTrue(allocated < frames, "$motion allocated $allocated bytes over $frames frames")
            assertTrue(sink.isFinite())
        }
    }

    private companion object {
        /** A frame every millisecond: a particle moves at most a few pixels from one to the next. */
        const val FRAME_STEP = 0.001f
    }

    /** Wholly outside the canvas (whatever its rotation), or all but transparent. */
    private fun Pose.hidden(area: ParticleArea): Boolean {
        val radius = area.spritePx * scale * sqrt(2f) / 2f
        val outside = x + radius <= 0f || x - radius >= area.width || y + radius <= 0f || y - radius >= area.height
        return outside || alpha < 0.02f
    }

    /**
     * Bytes this thread has allocated so far. Reached by reflection because unit tests compile against the Android
     * SDK, which lacks `java.lang.management`; the tests themselves run on a desktop JVM, which has it.
     */
    private fun allocatedBytes(): Long {
        val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        return Class.forName("com.sun.management.ThreadMXBean").getMethod("getCurrentThreadAllocatedBytes")
            .invoke(bean) as Long
    }
}
