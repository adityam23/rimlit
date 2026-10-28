package app.rimlit.ui.glow.particles

import app.rimlit.engine.FADE_IN_MS
import app.rimlit.engine.FADE_OUT_MS
import kotlin.math.floor
import kotlin.math.min

/**
 * When a glow's particles spawn and show, in seconds from the glow's first frame. The machine owns the glow's
 * timeline (fade in, hold [holdMs], fade out), and the surface's frame clock starts with it, so the particles play
 * the same timeline on that clock: they are out from the first frame, spawn until the hold ends ([spawnEnd]), and
 * whichever are still in flight then fade out with the glow, gone by its end ([cycle]). On a surface the glow's host
 * fades the whole glow, particles included, so they add no fade of their own. The glow's length never sets how fast
 * they move.
 *
 * The timeline repeats every [cycle]: one glow plays it once, and a glow that outlasts it keeps its particles coming
 * instead of running out. That covers a preview, which never ends and has no fades of its own, and a glow the machine
 * restarts on the same surface (a newer post re-targeting it), whose surface clock doesn't restart with it. Where no
 * host fade covers the turn of a [cycle], the particles fade out and back in by themselves ([opacity]).
 */
internal class ParticleTimeline(
    holdMs: Long,
    /** An in-app preview, which nothing else fades, so its particles fade themselves. */
    private val preview: Boolean,
) {

    /** The last moment a particle may spawn: the end of the hold. */
    val spawnEnd: Float = (FADE_IN_MS + holdMs) / MILLIS_PER_SECOND

    /** The glow's whole length: fade in, hold, fade out. */
    val cycle: Float = spawnEnd + FADE_OUT_SECONDS

    /** [t] (seconds since the first frame, at least 0) within the current repetition. */
    fun local(t: Float): Float = t - cycle * floor(t / cycle)

    /**
     * Every particle's opacity at [t] seconds since the first frame. A surface's host fades its whole glow, particles
     * included, so its first [cycle] adds no fade; a preview, which nothing fades, and a glow the machine re-targeted
     * past its first cycle fade with the [envelope] instead, so later cycles turn without a pop. (The first turn of a
     * re-targeted glow still cuts: telling it from a glow's own end would take the machine's glow identity.)
     */
    fun opacity(t: Float): Float = if (preview || t >= cycle) envelope(local(t)) else 1f

    /**
     * The particles' opacity at [local] seconds into the glow, following the glow's own fades: rising over its
     * fade-in, so the particles already out at the first frame don't pop in, and falling to 0 over its fade-out, so
     * none pops out at its end.
     */
    private fun envelope(local: Float): Float {
        val fadingIn = local / FADE_IN_SECONDS
        val fadingOut = (cycle - local) / FADE_OUT_SECONDS
        return min(fadingIn, fadingOut).coerceIn(0f, 1f)
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000f
        const val FADE_IN_SECONDS = FADE_IN_MS / MILLIS_PER_SECOND
        const val FADE_OUT_SECONDS = FADE_OUT_MS / MILLIS_PER_SECOND
    }
}
