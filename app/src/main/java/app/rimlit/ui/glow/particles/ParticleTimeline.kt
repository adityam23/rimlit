package app.rimlit.ui.glow.particles

import app.rimlit.engine.FADE_IN_MS
import app.rimlit.engine.FADE_OUT_MS
import kotlin.math.floor
import kotlin.math.min

/** No particle lives longer than this share of its glow, so spawning covers at least the rest. */
private const val MAX_LIFETIME_SHARE = 0.5f

/**
 * When a glow's particles spawn and live, in seconds from the glow's first frame. The machine owns the glow's
 * timeline (fade in, hold [holdMs], fade out), and the surface's frame clock starts with it, so the particles play
 * the same timeline on that clock: they spawn from the start until [spawnEnd], and each lives at most [lifetime], so
 * the last one leaves by the end of the fade-out ([cycle]).
 *
 * The timeline repeats every [cycle]: one glow plays it once, and a glow that outlasts it keeps its particles coming
 * instead of running out. That covers a preview, which never ends, and a glow the machine restarts on the same surface
 * (a newer post re-targeting it): the surface's clock doesn't restart with it, so its particles carry on in cycles
 * counted from the surface's first frame, and the glow's fade-out hides whichever are still out when it ends.
 */
internal class ParticleTimeline(holdMs: Long, naturalLifetime: Float) {

    /** The glow's whole length: fade in, hold, fade out. */
    val cycle: Float = (FADE_IN_MS + holdMs + FADE_OUT_MS) / MILLIS_PER_SECOND

    /** A particle's longest life: its motion's own, shortened to fit a short glow. */
    val lifetime: Float = min(naturalLifetime, cycle * MAX_LIFETIME_SHARE)

    /** The last moment a particle may spawn. */
    val spawnEnd: Float = cycle - lifetime

    /** [t] (seconds since the first frame, at least 0) within the current repetition. */
    fun local(t: Float): Float = t - cycle * floor(t / cycle)

    private companion object {
        const val MILLIS_PER_SECOND = 1_000f
    }
}
