package app.rimlit.engine

import app.rimlit.engine.Fixtures.ENVIRONMENTS
import app.rimlit.engine.Fixtures.EXTRACTED
import app.rimlit.engine.Fixtures.POSTS
import app.rimlit.engine.Fixtures.PREVIEW_SPEC
import app.rimlit.engine.Fixtures.SETTINGS
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Exhaustive model check of the shipped [GlowState.on]: a breadth-first search over every state reachable from
 * idle by any sequence of events over the fixtures' alphabet, asserting the invariants on every state and every
 * transition. Any violation fails with the shortest event trace that reaches it.
 *
 * External inputs (posts, removals, settings, environment, listener, accessibility, previews) arrive at any time.
 * Executor reports follow their contracts: timers fire only while armed, a palette result only while pending, and a
 * surface reports only on the glow's current surface (opened only while opening). Never trim the alphabet to make
 * the search fit: make the search cheaper instead.
 *
 * Visited states are kept only as exact packed codes ([StateCodec]), and each one's parent and event as indices; a
 * state is decoded back into an object only while it is expanded. The search expands one breadth-first level at a
 * time, in parallel chunks, so every trace is still a shortest one; each level's new states are numbered in one pass
 * after it, contiguously.
 */
class GlowMachineModelCheckTest {

    /** What one chunk of a level produced: the states it reached first, with their packed parents. */
    private class Expansion(val codes: LongList, val parents: LongList, val transitions: Long, val violation: String?)

    @Test
    fun everyReachableStateAndTransitionKeepsTheInvariants() {
        val codec = StateCodec()
        val visited = ConcurrentLongSet()
        // Per state, in number order: its parent's number and the event from it, packed (the start has parent -1).
        // Written only between levels, so the workers can read it for traces.
        val parents = LongList(initialCapacity = 1 shl 20)
        val start = codec.encode(Fixtures.initial())
        visited.add(start)
        parents.add(parentOf(-1, 0))
        var level = longArrayOf(start)
        var first = 0
        var transitions = 0L
        while (level.isNotEmpty()) {
            val current = level
            val firstOfLevel = first
            val expansions = (current.indices step CHUNK).toList().parallelStream()
                .map { from -> expand(current, from, firstOfLevel, codec, visited, parents) }
                .toList()
            expansions.firstNotNullOfOrNull { it.violation }?.let { fail(it) }
            transitions += expansions.sumOf { it.transitions }
            first += current.size
            val next = LongList(initialCapacity = maxOf(16, current.size))
            for (expansion in expansions) {
                for (i in 0 until expansion.codes.size) {
                    parents.add(expansion.parents[i])
                    next.add(expansion.codes[i])
                }
            }
            level = next.toArray()
        }
        println("Model check: ${visited.size} states, $transitions transitions")
        assertTrue(visited.size > 1_000, "the search should reach a meaningful state space")
    }

    /** Checks and expands up to [CHUNK] states of [level] from index [from]; the level's states are numbered from [first]. */
    private fun expand(
        level: LongArray,
        from: Int,
        first: Int,
        codec: StateCodec,
        visited: ConcurrentLongSet,
        parents: LongList,
    ): Expansion {
        val codes = LongList()
        val found = LongList()
        var transitions = 0L
        for (index in from until minOf(from + CHUNK, level.size)) {
            val state = codec.decode(level[index])
            val number = first + index
            stateViolation(state)?.let { return Expansion(codes, found, transitions, "$it\n${trace(parents, number)}") }
            for (event in alphabet(state)) {
                val step = state.on(event)
                transitions++
                transitionViolation(state, event, step)?.let {
                    val message = "$it\n${trace(parents, number)}\n  → $event\n  effects ${step.effects}\n  ⇒ ${step.state}"
                    return Expansion(codes, found, transitions, message)
                }
                val code = codec.encode(step.state)
                if (visited.add(code)) {
                    codes.add(code)
                    found.add(parentOf(number, EVENT_INDEX.getValue(event)))
                }
            }
        }
        return Expansion(codes, found, transitions, violation = null)
    }

    private fun alphabet(state: GlowState): List<GlowEvent> = buildList {
        addAll(EXTERNAL_EVENTS)
        if (state.pending != null) add(EXTRACTED_EVENT)
        state.armed.mapTo(this) { TIMER_EVENTS.getValue(it) }
        val glow = state.glow ?: return@buildList
        if (!glow.opened) add(OPENED_EVENTS.getValue(glow.surface))
        add(GONE_EVENTS.getValue(glow.surface))
    }

    /** Invariants of a single state. */
    private fun stateViolation(state: GlowState): String? {
        val glow = state.glow ?: return "timers armed without a glow".takeIf { state.armed.isNotEmpty() }
        val armed = state.armed
        return when {
            (Timer.OPEN in armed) == glow.opened -> "the open timeout must be armed exactly while opening"

            glow.opened && glow.phase == Phase.STARTING -> "an opened glow must have started its timeline"

            // With the progress check on timer transitions, this proves every glow ends (invariant 7).
            !phaseTimersMatch(glow.phase, armed) -> "phase ${glow.phase} doesn't match armed timers $armed"

            glow.phase == Phase.FADING_OUT && glow.instant -> "a fade-out must fade, not snap"

            Timer.HAND_OFF_WINDOW in armed && glow.surface == Surface.LOCK_SCREEN ->
                "a lock-screen glow counts as recent for the screen-off hand-off"

            // Invariant 4: the opaque activity would cover the call/alarm screen.
            glow.surface == Surface.LOCK_SCREEN && (state.ringingKeys.isNotEmpty() || glow.source.post?.isRinging == true) ->
                "a lock-screen glow while something rings"

            else -> null
        }
    }

    private fun phaseTimersMatch(phase: Phase, armed: Set<Timer>): Boolean {
        val timeline = armed.intersect(TIMELINE_TIMERS)
        return when (phase) {
            Phase.STARTING -> timeline.isEmpty() && Timer.HAND_OFF_WINDOW !in armed
            Phase.FADING_IN -> timeline == setOf(Timer.FADE_IN)
            Phase.HOLDING -> timeline == setOf(Timer.HOLD)
            Phase.FADING_OUT -> timeline == setOf(Timer.FADE_OUT) && Timer.HAND_OFF_WINDOW !in armed
        }
    }

    /** Invariants relating a transition's states, event and effects. */
    private fun transitionViolation(old: GlowState, event: GlowEvent, step: GlowStep): String? =
        surfaceViolation(old, event, step) ?: timerViolation(old, event, step) ?: pendingViolation(old, event, step)
            ?: sourceViolation(old, event, step.state) ?: reportViolation(old, event, step)

    /** Invariant 1, and the wake-up rule: at most one live surface, and the state agrees with the surface effects. */
    private fun surfaceViolation(old: GlowState, event: GlowEvent, step: GlowStep): String? {
        val new = step.state
        val live = setOfNotNull(old.glow?.surface).toMutableSet()
        if (event is GlowEvent.SurfaceGone) live -= event.surface
        for (effect in step.effects) {
            when (effect) {
                is GlowEffect.Open -> {
                    if (!live.add(effect.surface) || live.size > 1) return "opens a second surface"
                    val mustWake = effect.surface == Surface.LOCK_SCREEN && !new.environment.screenOn
                    if (effect.wakeScreen != mustWake) return "wakes the screen wrongly: $effect"
                }

                is GlowEffect.Close -> live -= effect.surface

                else -> Unit
            }
        }
        if (live != setOfNotNull(new.glow?.surface)) return "live surfaces $live disagree with the state"
        val screenTurnedOff = old.environment.screenOn && !new.environment.screenOn
        // Only a hand-off opening the lock screen survives: timers stop in deep sleep, so a kept glow would freeze.
        val handedOff = new.glow?.surface == Surface.LOCK_SCREEN && step.effects.any { it is GlowEffect.Open }
        return "a glow outlived the screen turning off".takeIf { screenTurnedOff && new.glow != null && !handedOff }
    }

    /** The executor's timers mirror the armed set, and every timer moves its glow strictly forward (invariant 7). */
    private fun timerViolation(old: GlowState, event: GlowEvent, step: GlowStep): String? {
        val armed = old.armed.toMutableSet()
        if (event is GlowEvent.TimerFired) armed -= event.timer
        for (effect in step.effects) {
            when (effect) {
                is GlowEffect.Arm -> armed += effect.timer
                is GlowEffect.Disarm -> if (!armed.remove(effect.timer)) return "disarms an unarmed timer"
                else -> Unit
            }
        }
        if (armed != step.state.armed) return "armed timers $armed disagree with the state"
        val before = old.glow
        val after = step.state.glow
        if (event !is GlowEvent.TimerFired || before == null || after == null) return null
        // Well-founded progress: the phase advances, or it stays and a timer is used up. With every phase keeping
        // its timer armed, timers alone always end the glow.
        val advanced = after.phase > before.phase || (after.phase == before.phase && step.state.armed.size < old.armed.size)
        return "$event didn't move the glow forward".takeUnless { advanced }
    }

    /** The executor's extraction mirrors the pending post; dropped posts never come back. */
    private fun pendingViolation(old: GlowState, event: GlowEvent, step: GlowStep): String? {
        val new = step.state
        val extracts = step.effects.filterIsInstance<GlowEffect.ExtractPalette>()
        val cancelled = GlowEffect.CancelExtraction in step.effects
        return when {
            extracts.any { it.post != new.pending?.post } -> "extracts for a post that isn't pending"

            // Revoked notification access starts no new glow.
            event == GlowEvent.ListenerDisconnected && new.pending != null ->
                "a post is still pending after the listener disconnected"

            new.pending == old.pending -> null

            new.pending != null && extracts.isEmpty() -> "pending without an extraction"

            new.pending != null && (event !is GlowEvent.Posted || new.pending.post != event.post) ->
                "pending a post other than the one just posted"

            new.pending == null && event !is GlowEvent.PaletteExtracted && !cancelled ->
                "dropped pending without cancelling its extraction"

            else -> null
        }
    }

    /** Invariants 2, 3, 6 and 8, and the ringing rules: what the glow shows, and when. */
    private fun sourceViolation(old: GlowState, event: GlowEvent, new: GlowState): String? {
        val oldGlow = old.glow
        val newGlow = new.glow
        val newSource = newGlow?.source
        val newest = when (event) {
            is GlowEvent.Posted -> GlowSource.Notification(event.post)
            is GlowEvent.Preview -> GlowSource.Preview(event.target)
            is GlowEvent.PaletteExtracted -> old.pending?.let { GlowSource.Notification(it.post) }
            else -> null
        }
        val confirmed = event is GlowEvent.SurfaceOpened && event.surface == newGlow?.surface
        val wasOpen = oldGlow != null && oldGlow.opened && oldGlow.surface == newGlow?.surface
        val endedRinging = event is GlowEvent.Posted && !event.post.isRinging && event.post.key in old.ringingKeys
        val ringingStarted = event is GlowEvent.Posted && event.post.isRinging
        // Rimlit's own posts other than the test (the lock-screen trampoline) only update the posted keys.
        val trampoline = event is GlowEvent.Posted && event.post.isOwn && !event.post.isTest &&
            event.post.key !in old.ringingKeys
        return when {
            trampoline && (newGlow != oldGlow || new.pending != old.pending) -> "Rimlit's own post changed the glow"

            newGlow != null && newGlow.opened && !wasOpen && !confirmed -> "shown without confirmation"

            event is GlowEvent.Removed && new.pending?.post?.key == event.key -> "a removed post is still pending"

            event is GlowEvent.Removed && newSource?.post?.key == event.key && newGlow.phase != Phase.FADING_OUT ->
                "removed but shown"

            event is GlowEvent.Removed && oldGlow?.source is GlowSource.Preview && newGlow != oldGlow ->
                "a removal changed a preview"

            newSource != null && newSource != oldGlow?.source && newSource != newest ->
                "the glow moved to something other than the newest post"

            newSource != null && newSource != oldGlow?.source && new.pending != null ->
                "an older post is still pending behind a newer glow"

            // A call that stopped ringing (answered) keeps neither its pending post nor its glow, except a re-post
            // that alerts by itself.
            endedRinging && new.pending?.post?.key == event.post.key && new.pending.post != event.post ->
                "an answered call is still pending"

            endedRinging && newSource?.post?.key == event.post.key && newSource.post != event.post &&
                newGlow.phase != Phase.FADING_OUT -> "an answered call still glows"

            ringingStarted && new.pending != null && new.pending.post != event.post ->
                "a post pending from before a call could launch over its screen"

            else -> null
        }
    }

    /** That the screen can't be woken is reported once per listener connection. */
    private fun reportViolation(old: GlowState, event: GlowEvent, step: GlowStep): String? {
        val reports = step.effects.count { it == GlowEffect.WakeScreenDenied }
        val reconnected = event is GlowEvent.ListenerConnected
        return when {
            reports > 1 || (reports == 1 && old.wakeScreenDeniedReported) -> "reports the missing wake access twice"

            reports == 1 && !step.state.wakeScreenDeniedReported -> "forgets reporting the missing wake access"

            old.wakeScreenDeniedReported && !step.state.wakeScreenDeniedReported && !reconnected ->
                "reports the missing wake access again without a reconnect"

            else -> null
        }
    }

    private fun parentOf(number: Int, event: Int): Long = (number.toLong() shl Int.SIZE_BITS) or event.toLong()

    private fun trace(parents: LongList, number: Int): String {
        val events = generateSequence(number) { (parents[it] shr Int.SIZE_BITS).toInt().takeIf { p -> p >= 0 } }
            .map { parents[it] }
            .filter { it shr Int.SIZE_BITS >= 0 }
            .map { ALL_EVENTS[it.toInt()] }
            .toList()
        return "trace from idle:\n" + events.asReversed().joinToString("\n") { "  $it" }
    }

    private companion object {
        /** States per parallel task: large enough to amortise scheduling, small enough to balance the cores. */
        const val CHUNK = 1024

        val TIMELINE_TIMERS = setOf(Timer.FADE_IN, Timer.HOLD, Timer.FADE_OUT)

        /** Inputs from outside the executor, possible in every state. Built once: the search visits millions. */
        val EXTERNAL_EVENTS: List<GlowEvent> = buildList {
            POSTS.mapTo(this) { GlowEvent.Posted(it) }
            add(GlowEvent.Removed("a"))
            add(GlowEvent.Removed("b"))
            add(GlowEvent.ListenerConnected(emptySet(), emptySet()))
            add(GlowEvent.ListenerConnected(setOf("a", "b"), emptySet()))
            add(GlowEvent.ListenerConnected(setOf("a", "b"), setOf("a")))
            add(GlowEvent.ListenerDisconnected)
            SETTINGS.mapTo(this) { GlowEvent.SettingsChanged(it) }
            ENVIRONMENTS.mapTo(this) { GlowEvent.EnvironmentChanged(it) }
            add(GlowEvent.AccessibilityChanged(true))
            add(GlowEvent.AccessibilityChanged(false))
            PreviewTarget.entries.mapTo(this) { GlowEvent.Preview(it, PREVIEW_SPEC) }
        }

        val EXTRACTED_EVENT = GlowEvent.PaletteExtracted(EXTRACTED)
        val TIMER_EVENTS = Timer.entries.associateWith { GlowEvent.TimerFired(it) }
        val OPENED_EVENTS = Surface.entries.associateWith { GlowEvent.SurfaceOpened(it) }
        val GONE_EVENTS = Surface.entries.associateWith { GlowEvent.SurfaceGone(it) }

        /** Every event the alphabet can produce, so a trace stores an index instead of the event. */
        val ALL_EVENTS: List<GlowEvent> =
            EXTERNAL_EVENTS + EXTRACTED_EVENT + TIMER_EVENTS.values + OPENED_EVENTS.values + GONE_EVENTS.values
        val EVENT_INDEX: Map<GlowEvent, Int> = ALL_EVENTS.withIndex().associate { (index, event) -> event to index }
    }
}
