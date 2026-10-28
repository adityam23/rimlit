package app.rimlit.ui.glow.particles

import app.rimlit.engine.FADE_IN_MS
import app.rimlit.engine.FADE_OUT_MS
import app.rimlit.model.GlowSettings
import app.rimlit.model.ParticleImage
import app.rimlit.model.ParticleMotion
import app.rimlit.model.ParticleSpec
import app.rimlit.ui.glow.BATTERY_SAVER_PARTICLE_SHARE
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ParticleFieldTest {

    private val ranges = GlowSettings.Ranges
    private val defaultHold = GlowSettings().durationMs

    /** A phone at the largest particle size, a landscape one, and an in-app preview stand-in. */
    private val areas = listOf(
        ParticleArea(1080f, 2400f, spritePx = 132f),
        ParticleArea(2400f, 1080f, spritePx = 132f),
        ParticleArea(330f, 660f, spritePx = 10f),
    )
    private val holds = listOf(ranges.durationMs.first, defaultHold, ranges.durationMs.last)
    private val speeds = listOf(ranges.particleSpeed.start, 1f, ranges.particleSpeed.endInclusive)

    private data class Pose(val x: Float, val y: Float, val degrees: Float, val scale: Float, val alpha: Float, val tint: Int)

    /**
     * Fields are previews unless a test says otherwise: a preview fades itself, so what a test sees is all there is
     * to see. A surface's host fades it instead.
     */
    private fun field(
        motion: ParticleMotion,
        holdMs: Long = defaultHold,
        speed: Float = 1f,
        area: ParticleArea = areas.first(),
        seed: Int = 7,
        share: Float = 1f,
        preview: Boolean = true,
    ): ParticleField {
        val amount = ranges.particleAmount.endInclusive
        val spec = ParticleSpec(ParticleImage.STAR, motion, speed = speed, amount = amount, rotationDegPerSecond = 90f)
        val count = particleCount(motion, amount, share)
        return ParticleField(spec, count, tints = 3, ParticleTimeline(holdMs, preview), area, seed)
    }

    /** Particle [index] as drawn at [t] seconds since the first frame, its alpha faded as a frame fades it. */
    private fun ParticleField.pose(index: Int, t: Float, particle: Particle = Particle()): Pose? =
        if (at(index, timeline.local(t), particle)) {
            with(particle) { Pose(x, y, degrees, scale, alpha * timeline.opacity(t), tint) }
        } else {
            null
        }

    /** The live particles at [t], by index. */
    private fun ParticleField.poses(t: Float): Map<Int, Pose> {
        val particle = Particle()
        return (0 until count).mapNotNull { index -> pose(index, t, particle)?.let { index to it } }.toMap()
    }

    /** Every [step] seconds through one whole glow. */
    private fun ParticleField.times(step: Float = 0.01f) = generateSequence(0f) { it + step }
        .takeWhile { it < timeline.cycle }

    private class Case(val field: ParticleField, val motion: ParticleMotion, val area: ParticleArea, val holdMs: Long) {
        override fun toString() = "$motion over $area, hold $holdMs ms, lifetime ${field.lifetime} s"
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
    fun theSpeedAloneSetsHowFastParticlesMove() {
        for (motion in ParticleMotion.entries) {
            val lifetimes = holds.map { field(motion, holdMs = it).lifetime }
            assertTrue(lifetimes.all { it == lifetimes.first() }, "$motion: the glow's length changes $lifetimes")
            for (speed in speeds) {
                assertEquals(field(motion).lifetime / speed, field(motion, speed = speed).lifetime, 1e-4f, "$motion")
            }
            val slow = field(motion)
            val fast = field(motion, speed = 2f)
            val midHold = slow.timeline.spawnEnd / 2
            assertNotEquals(slow.poses(midHold), fast.poses(midHold), "$motion: speed changes nothing at the default hold")
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

    /** Every slot has a particle out from the first frame to the end of the hold: the amount is what shows. */
    @Test
    fun theFieldHoldsItsAmountThroughTheHold() {
        everyField { case ->
            val field = case.field
            for (t in field.times().filter { it <= field.timeline.spawnEnd }) {
                assertEquals(field.count, field.poses(t).size, "$case: particles out at $t s")
            }
        }
        // And most of them are in sight mid-hold, at the default hold and speed.
        for (motion in ParticleMotion.entries) for (area in areas) {
            val field = field(motion, area = area)
            val midHold = (FADE_IN_MS + defaultHold / 2) / 1000f
            for (t in listOf(midHold - 1f, midHold, midHold + 1f)) {
                val inSight = field.poses(t).values.count { !it.hidden(area) }
                assertTrue(inSight >= field.count * IN_SIGHT_SHARE, "$motion over $area: $inSight in sight at $t s")
            }
        }
    }

    @Test
    fun spawningStopsAtTheEndOfTheHold() {
        everyField { case ->
            val field = case.field
            val timeline = field.timeline
            assertEquals((FADE_IN_MS + case.holdMs) / 1000f, timeline.spawnEnd, 1e-4f, "$case")
            assertEquals((FADE_IN_MS + case.holdMs + FADE_OUT_MS) / 1000f, timeline.cycle, 1e-4f, "$case")

            // Once spawning has ended, particles only ever leave: no slot gets a new one, frame by frame. The first
            // frame past the end may still show one spawned within the frame before it.
            val particle = Particle()
            val after = ceil(timeline.spawnEnd / FRAME_STEP).toInt() + 1
            for (index in 0 until field.count) {
                var last = field.pose(index, (after - 1) * FRAME_STEP, particle)
                for (frame in after until ceil(timeline.cycle / FRAME_STEP).toInt()) {
                    val t = frame * FRAME_STEP
                    val pose = field.pose(index, t, particle)
                    val spawned = pose != null && (last == null || pose.isNewAfter(last, case.area))
                    assertTrue(!spawned, "$case: particle $index spawned at $t s, after ${timeline.spawnEnd} s")
                    last = pose
                }
            }
        }
    }

    /** A preview fades its particles out by itself as each glow's timeline ends. */
    @Test
    fun nothingShowsAsAPreviewsGlowEnds() {
        everyField { case ->
            val end = case.field.timeline.cycle
            for (t in listOf(end - 0.004f, end - 0.001f, 2 * end - 0.001f)) for ((index, pose) in case.field.poses(t)) {
                assertTrue(pose.hidden(case.area), "$case: particle $index still shows at $t s: $pose")
            }
        }
    }

    /**
     * A surface's host already fades its whole glow, so its particles add no fade of their own over the glow's
     * timeline: fading them too would fade them twice. Past it (a glow the machine re-targeted, whose clock runs on)
     * nothing else fades them where a cycle turns, so they fade themselves there, as a preview does.
     */
    @Test
    fun aSurfacesParticlesFadeOnlyWithTheirHost() {
        for (hold in holds) {
            val surface = field(ParticleMotion.FALL, holdMs = hold, preview = false)
            val preview = field(ParticleMotion.FALL, holdMs = hold)
            val cycle = surface.timeline.cycle
            for (t in generateSequence(0f) { it + FRAME_STEP }.takeWhile { it < cycle }) {
                assertEquals(1f, surface.timeline.opacity(t), "hold $hold ms: a surface's particles faded at $t s")
            }
            for (t in listOf(0f, 0.05f, cycle - 0.05f, cycle + 0.05f, 2 * cycle - 0.05f)) {
                assertTrue(preview.timeline.opacity(t) < 1f, "hold $hold ms: a preview's particles don't fade at $t s")
            }
            for (t in listOf(cycle, cycle + 0.05f, 2 * cycle - 0.05f, cycle + cycle / 2)) {
                assertEquals(preview.timeline.opacity(t), surface.timeline.opacity(t), "hold $hold ms: a re-targeted glow at $t s")
            }
        }
    }

    /**
     * Frame by frame (every millisecond) through one whole glow, each slot's particles come and go only out of sight,
     * and what is left when the glow ends is out of sight too. A burst pops up in sight: that is its look. A slot's
     * next particle starts elsewhere, so a jump of more than a sprite between frames is a new particle: none moves
     * that fast.
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
                    val pose = field.pose(index, t, particle)
                    val replaced = last != null && pose != null && pose.isNewAfter(last, case.area)
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
        val amounts = listOf(ranges.particleAmount.start, 0.3f, 0.5f, 0.7f, most)
        for (motion in ParticleMotion.entries) {
            assertEquals(MAX_PARTICLES, particleCount(motion, most, share = 1f), "$motion")
            for (amount in amounts) for (share in listOf(0.5f, 1f)) {
                val count = particleCount(motion, amount, share)
                assertTrue(count in 1..MAX_PARTICLES, "$motion: amount $amount, share $share gives $count")
            }
            for (amount in amounts) {
                val full = particleCount(motion, amount, share = 1f)
                val half = particleCount(motion, amount, share = BATTERY_SAVER_PARTICLE_SHARE)
                val within = if (motion == ParticleMotion.POP) BURST_SIZE else 1
                assertTrue(abs(full / 2f - half) <= within, "$motion: amount $amount gives $full, then $half")
            }
            val field = field(motion)
            val peak = field.times().maxOf { field.poses(it).size }
            assertTrue(peak in 1..MAX_PARTICLES, "$motion peaks at $peak live particles")
        }
    }

    @Test
    fun burstsAreAlwaysWhole() {
        for (amount in generateSequence(ranges.particleAmount.start) { it + 0.01f }
            .takeWhile { it <= ranges.particleAmount.endInclusive }) {
            for (share in listOf(BATTERY_SAVER_PARTICLE_SHARE, 1f)) {
                val count = particleCount(ParticleMotion.POP, amount, share)
                assertEquals(0, count % BURST_SIZE, "amount $amount, share $share gives $count")
                assertTrue(count >= BURST_SIZE, "amount $amount, share $share gives $count")
            }
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
            // As `ParticlePainter.draw` does it: the frame's time and fade once, then every particle.
            fun frame(t: Float) {
                val local = field.timeline.local(t)
                sink += field.timeline.opacity(t)
                for (index in 0 until field.count) if (field.at(index, local, particle)) sink += particle.x
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

        /** The least share of the particles in sight mid-hold: the rest are entering or leaving past an edge. */
        const val IN_SIGHT_SHARE = 0.7f
    }

    /**
     * This frame's pose is a slot's next particle, not [previous] (the frame before) moved on: a new particle starts
     * elsewhere, and none moves a sprite's width within a frame.
     */
    private fun Pose.isNewAfter(previous: Pose, area: ParticleArea) = hypot(x - previous.x, y - previous.y) > area.spritePx

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
