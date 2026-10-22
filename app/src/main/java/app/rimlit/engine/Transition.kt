package app.rimlit.engine

import app.rimlit.model.GlowPalette
import app.rimlit.model.GlowSpec
import java.util.EnumSet

/**
 * One step of the machine: the handlers update [state] and collect the effects in the order the executor runs them,
 * keeping the armed timers and the effects that (dis)arm them in step.
 */
// One reducer: a handler per event plus the lifecycle mechanics they share. Splitting it would only scatter one
// transition across classes.
@Suppress("TooManyFunctions")
internal class Transition(private var state: GlowState) {

    private val effects = mutableListOf<GlowEffect>()

    private val glow: Glow? get() = state.glow

    fun step() = GlowStep(state, effects)

    @Suppress("CyclomaticComplexMethod") // One flat dispatch entry per event; the logic lives in the handlers.
    fun handle(event: GlowEvent) {
        when (event) {
            is GlowEvent.Posted -> posted(event.post)

            is GlowEvent.Removed -> removed(event.key)

            is GlowEvent.ListenerConnected -> {
                state = state.copy(activeKeys = event.activeKeys, ringingKeys = event.ringingKeys)
                if (event.ringingKeys.isNotEmpty()) protectRingingScreen()
            }

            GlowEvent.ListenerDisconnected -> {
                dropPending()
                state = state.copy(activeKeys = emptySet(), ringingKeys = emptySet())
            }

            is GlowEvent.SettingsChanged -> state = state.copy(settings = event.settings)

            is GlowEvent.EnvironmentChanged -> environment(event.environment)

            is GlowEvent.AccessibilityChanged -> state = state.copy(accessibilityOverlay = event.available)

            is GlowEvent.Preview -> preview(event.target, event.spec)

            is GlowEvent.PaletteExtracted -> extracted(event.colors)

            is GlowEvent.SurfaceOpened -> opened(event.surface)

            is GlowEvent.SurfaceGone -> {
                check(glow?.surface == event.surface) { "Contract: $event for a surface that isn't the glow's" }
                end()
            }

            is GlowEvent.TimerFired -> fired(event.timer)
        }
    }

    private fun posted(post: Post) {
        val isUpdate = post.key in state.activeKeys
        val wasRinging = post.key in state.ringingKeys
        state = state.copy(
            activeKeys = state.activeKeys + post.key,
            ringingKeys = if (post.isRinging) state.ringingKeys + post.key else state.ringingKeys - post.key,
        )
        if (post.isRinging) {
            protectRingingScreen()
        } else if (wasRinging) {
            // Ringing ended: an answered call is re-posted under its key without the full-screen intent.
            if (state.pending?.post?.key == post.key) dropPending()
            if (glow?.source?.post?.key == post.key) dismiss()
        }

        val settings = state.settings
        if (!settings.wants(post) || !post.alerts(isUpdate)) return
        val source = GlowSource.Notification(post)
        val target = routed(state.route(source, settings)) ?: return
        val palette = settings.paletteFor(post.packageName)
        if (palette == null) {
            state = state.copy(pending = Pending(post, settings))
            effects += GlowEffect.ExtractPalette(post)
        } else {
            dropPending()
            show(source, settings.toGlowSpec(palette), target)
        }
    }

    /** Something rings: its own call/alarm screen is up or launching, and nothing may cover it, not even a post
     * pending from before. */
    private fun protectRingingScreen() {
        dropPending()
        if (glow?.surface == Surface.LOCK_SCREEN) end()
    }

    private fun extracted(colors: List<Int>) {
        val pending = checkNotNull(state.pending) { "Contract: a palette arrives only for the in-flight extraction" }
        state = state.copy(pending = null)
        val source = GlowSource.Notification(pending.post)
        val target = routed(state.route(source, pending.settings)) ?: return
        show(source, pending.settings.toGlowSpec(GlowPalette.Colors(colors)), target)
    }

    private fun removed(key: String) {
        state = state.copy(activeKeys = state.activeKeys - key, ringingKeys = state.ringingKeys - key)
        if (state.pending?.post?.key == key) dropPending()
        if (glow?.source?.post?.key == key) dismiss()
    }

    private fun preview(target: PreviewTarget, spec: GlowSpec) {
        val source = GlowSource.Preview(target)
        val surface = routed(state.route(source, state.settings)) ?: return
        dropPending()
        show(source, spec, surface)
    }

    /** [route]'s surface, or `null` for nowhere (reporting a missing full-screen-intent grant). */
    private fun routed(route: Route): Surface? {
        if (route is Route.NeedsFullScreenIntent) effects += GlowEffect.FullScreenIntentDenied
        return (route as? Route.To)?.surface
    }

    private fun show(source: GlowSource, spec: GlowSpec, surface: Surface) {
        val current = glow
        when {
            current?.surface == surface && current.opened -> {
                // Re-target the visible surface without flicker: fade (back) in and restart the hold.
                val retargeted = current.copy(source = source, spec = spec, phase = Phase.FADING_IN, instant = false)
                setGlow(retargeted)
                disarm(Timer.HOLD, Timer.FADE_OUT)
                fadeIn(retargeted)
            }

            current?.surface == surface -> {
                // Still opening: it starts fresh once open.
                setGlow(current.copy(source = source, spec = spec, phase = Phase.STARTING, instant = false))
                disarm(Timer.FADE_IN, Timer.HOLD, Timer.FADE_OUT, Timer.HAND_OFF_WINDOW)
            }

            else -> {
                end()
                setGlow(Glow(source, spec, surface, opened = false, phase = Phase.STARTING, instant = false))
                open(surface)
            }
        }
    }

    private fun opened(surface: Surface) {
        val current = glow
        check(current?.surface == surface && !current.opened) { "Contract: $surface opened but wasn't opening" }
        disarm(Timer.OPEN)
        if (current.phase == Phase.STARTING) {
            val started = current.copy(opened = true, phase = Phase.FADING_IN)
            setGlow(started)
            fadeIn(started)
        } else {
            setGlow(current.copy(opened = true))
        }
    }

    private fun fired(timer: Timer) {
        check(timer in state.armed) { "Contract: $timer fired while not armed" }
        state = state.copy(armed = state.armed.without(timer))
        val current = checkNotNull(glow) { "Timers are only armed while there is a glow" }
        when (timer) {
            Timer.OPEN -> end()

            Timer.FADE_IN -> {
                setGlow(current.copy(phase = Phase.HOLDING))
                arm(Timer.HOLD, current.spec.durationMs)
            }

            Timer.HOLD -> fadeOut()

            Timer.FADE_OUT -> end()

            Timer.HAND_OFF_WINDOW -> Unit
        }
    }

    private fun environment(new: Environment) {
        val old = state.environment
        if (new == old) return
        state = state.copy(environment = new)
        val current = glow ?: return
        val screenTurnedOff = old.screenOn && !new.screenOn
        when {
            // The AOD moment is over (the activity's onStop follows): it is never re-targeted with the screen off.
            screenTurnedOff && current.surface == Surface.LOCK_SCREEN -> end()

            // A recent on-screen glow the user may have missed by turning the screen off.
            screenTurnedOff && Timer.HAND_OFF_WINDOW in state.armed -> reRoute(current)

            old.locked && !new.locked && new.screenOn && current.surface == Surface.LOCK_SCREEN ->
                if (current.phase == Phase.FADING_OUT || !reRoute(current)) end()
        }
    }

    /** Places the glow again after a screen or lock change, handing it off if it now belongs elsewhere. */
    private fun reRoute(current: Glow): Boolean {
        val surface = routed(state.route(current.source, state.settings, lockScreenUp = false)) ?: return false
        if (surface != current.surface) handOff(current, surface)
        return true
    }

    /** Moves the glow to [surface]; its timers keep running, so it shows the remaining hold there, instantly. */
    private fun handOff(current: Glow, surface: Surface) {
        effects += GlowEffect.Close(current.surface)
        disarm(Timer.HAND_OFF_WINDOW)
        setGlow(current.copy(surface = surface, opened = false, instant = current.phase != Phase.STARTING))
        open(surface)
    }

    /** Ends early with a fade-out, or at once if nothing is visible yet. */
    private fun dismiss() {
        val current = glow ?: return
        when {
            !current.opened -> end()
            current.phase != Phase.FADING_OUT -> fadeOut()
        }
    }

    /** (Re)starts the fade-in; a glow that starts on screen is recent for the screen-off hand-off. */
    private fun fadeIn(glow: Glow) {
        arm(Timer.FADE_IN, FADE_IN_MS)
        if (glow.surface != Surface.LOCK_SCREEN) {
            arm(Timer.HAND_OFF_WINDOW, (glow.spec.durationMs * HAND_OFF_SHARE).toLong())
        }
    }

    private fun fadeOut() {
        val current = glow ?: return
        setGlow(current.copy(phase = Phase.FADING_OUT, instant = false))
        disarm(Timer.FADE_IN, Timer.HOLD, Timer.HAND_OFF_WINDOW)
        arm(Timer.FADE_OUT, FADE_OUT_MS)
    }

    private fun open(surface: Surface) {
        val wakeScreen = surface == Surface.LOCK_SCREEN && !state.environment.screenOn
        effects += GlowEffect.Open(surface, wakeScreen)
        arm(Timer.OPEN, OPEN_TIMEOUT_MS)
    }

    /** Closes the glow's surface and forgets the glow and its timers. */
    private fun end() {
        val current = glow ?: return
        effects += GlowEffect.Close(current.surface)
        state.armed.mapTo(effects) { GlowEffect.Disarm(it) }
        state = state.copy(glow = null, armed = emptySet())
    }

    private fun dropPending() {
        if (state.pending == null) return
        state = state.copy(pending = null)
        effects += GlowEffect.CancelExtraction
    }

    private fun setGlow(new: Glow) {
        state = state.copy(glow = new)
    }

    private fun arm(timer: Timer, delayMs: Long) {
        state = state.copy(armed = state.armed.with(timer))
        effects += GlowEffect.Arm(timer, delayMs)
    }

    private fun disarm(vararg timers: Timer) {
        for (timer in timers) {
            if (timer !in state.armed) continue
            state = state.copy(armed = state.armed.without(timer))
            effects += GlowEffect.Disarm(timer)
        }
    }
}

/** Armed timers stay an [EnumSet]: compact and cheap to hash, which the model check's millions of states rely on. */
private fun Set<Timer>.with(timer: Timer): Set<Timer> = EnumSet.noneOf(Timer::class.java).apply {
    addAll(this@with)
    add(timer)
}

private fun Set<Timer>.without(timer: Timer): Set<Timer> = EnumSet.noneOf(Timer::class.java).apply {
    addAll(this@without)
    remove(timer)
}
