package app.rimlit.engine

import app.rimlit.model.AutoColors
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSpec

/*
 * The notification → glow lifecycle as one pure state machine: `state.on(event)` returns the next state and the
 * effects an executor must run. Every input (notifications, screen/lock changes, surfaces, timers, previews) is an
 * event, processed one at a time in arrival order; nothing here touches Android. The reducer is `Transition`, the
 * placement policy `route`.
 *
 * Timeline of one glow:  STARTING ─opened─▶ FADING_IN ─FADE_IN─▶ HOLDING ─HOLD─▶ FADING_OUT ─FADE_OUT─▶ (none)
 *
 * | Event                    | Transition                                                                       |
 * |--------------------------|----------------------------------------------------------------------------------|
 * | Posted                   | Ringing: drops any pending post and ends a lock-screen glow. Ringing ended (an   |
 * |                          | answered call, re-posted without its full-screen intent): its glow fades, its    |
 * |                          | pending post is dropped. Then, if wanted, alerting and routed somewhere: extract |
 * |                          | (pending, superseding any pending) or show at once. Routed nowhere: no change.   |
 * | PaletteExtracted         | The pending post is routed again (now, under its post-time settings) and shown.  |
 * | Removed                  | Drops a pending post with that key; the shown one fades out early (dismiss).     |
 * | Preview                  | Drops any pending post and shows: latest wins, like a post.                      |
 * | show(surface)            | Same surface, opened: re-target, FADING_IN, hold restarts, no flicker. Same      |
 * |                          | surface, still opening: starts fresh once open. Other surface: close, open.      |
 * | SurfaceOpened            | STARTING → FADING_IN; a hand-off keeps its phase and shows instantly.            |
 * | SurfaceGone              | Ended (no fade; nothing left to draw on).                                        |
 * | Screen off               | Lock-screen glow: ended. On-screen glow that started within HAND_OFF_SHARE of    |
 * |                          | its hold: re-routed, so it hands off to the lock screen when allowed there.      |
 * |                          | Otherwise ended: its timers stop in deep sleep, so it would reappear on wake.    |
 * | Unlocked                 | Lock-screen glow: re-routed on screen (hand-off), else ended; fading out: ended. |
 * | TimerFired               | OPEN: never confirmed, ended. FADE_IN → HOLDING. HOLD → FADING_OUT. FADE_OUT:    |
 * |                          | ended. HAND_OFF_WINDOW: the glow stops counting as recent.                       |
 * | Listener connected       | Stores the posted and ringing keys (ringing: as for a ringing post), and reports |
 * |                          | that the screen can't be woken again on the next skipped lock-screen glow.       |
 * | Listener disconnected    | Drops a pending post and forgets keys; a shown glow fades normally.              |
 * | Settings, accessibility  | Stored only: settings apply to the next post, availability to the next routing.  |
 *
 * A hand-off closes one surface and opens another while the timers keep running, so the new surface shows the
 * remaining hold, instantly. Ending closes the surface (which also cancels the lock-screen trampoline).
 *
 * Executor contracts (violations fail loudly): a timer fires only while armed, never stale; a palette result
 * arrives only for the in-flight extraction; a surface reports only on the instance opened by its latest `Open`.
 */

/** How long a glow fades in, then out, around its hold. `GlowHost` animates with the same durations. */
const val FADE_IN_MS = 180L
const val FADE_OUT_MS = 450L

/** A surface that hasn't confirmed opening by now (e.g. a full-screen intent never delivered) is given up. */
const val OPEN_TIMEOUT_MS = 5_000L

/** Screen off within this share of the hold (from fade-in) hands an on-screen glow off to the lock screen. */
const val HAND_OFF_SHARE = 0.25

/** Where a glow is drawn. At most one is live at a time. */
enum class Surface {
    /** `TYPE_APPLICATION_OVERLAY` window: hidden by the keyguard, so only while unlocked. */
    APP_OVERLAY,

    /** The optional accessibility service's overlay window: also above the lock screen. */
    ACCESSIBILITY_OVERLAY,

    /** `LockScreenGlowActivity`, the black AOD-style screen; woken by a full-screen intent when the screen is off. */
    LOCK_SCREEN,
}

/** Where a glow is in its timeline; the matching timer is always armed, so every phase ends. */
enum class Phase { STARTING, FADING_IN, HOLDING, FADING_OUT }

/** One-shot timers; the executor never fires one that was disarmed or re-armed since. */
enum class Timer { OPEN, FADE_IN, HOLD, FADE_OUT, HAND_OFF_WINDOW }

enum class PreviewTarget { ON_SCREEN, LOCK_SCREEN }

/** The device state routing depends on; the coordinator samples it before each input that routes. */
data class Environment(
    val screenOn: Boolean,
    val locked: Boolean,
    /**
     * The full-screen-intent trampoline can wake the screen: its grant (Android 14+), notifications allowed for
     * Rimlit, and its channel important enough to launch a full-screen intent.
     */
    val canWakeScreen: Boolean,
)

sealed interface GlowSource {
    /** The notification behind the glow; `null` for a preview. */
    val post: Post?

    data class Notification(override val post: Post) : GlowSource

    /** In-app preview: has no notification, so no removal ends it. */
    data class Preview(val target: PreviewTarget) : GlowSource {
        override val post: Post? get() = null
    }
}

data class Glow(
    val source: GlowSource,
    val spec: GlowSpec,
    val surface: Surface,
    /** The surface confirmed it is up; nothing is drawn before. */
    val opened: Boolean,
    val phase: Phase,
    /** Appear without fading in: handed off mid-timeline from another surface. */
    val instant: Boolean,
)

/** An accepted post waiting for its palette, with the settings it was posted under. */
data class Pending(val post: Post, val settings: GlowSettings)

/** What a surface draws: [spec], faded towards [visible] (or snapped there when [instant]). */
data class GlowFrame(val spec: GlowSpec, val visible: Boolean, val instant: Boolean)

/** Settings are non-null by type: the coordinator only creates the machine once they have loaded. */
data class GlowState(
    val settings: GlowSettings,
    val environment: Environment,
    /** The accessibility service is connected, so its overlay is available for routing. */
    val accessibilityOverlay: Boolean,
    /** Keys currently posted, so a re-post can be recognised as an update. */
    val activeKeys: Set<String> = emptySet(),
    /** Keys currently ringing (an incoming call or alarm showing its own full-screen UI). */
    val ringingKeys: Set<String> = emptySet(),
    val pending: Pending? = null,
    val glow: Glow? = null,
    val armed: Set<Timer> = emptySet(),
    /** [GlowEffect.WakeScreenDenied] was emitted since the listener last connected: it is reported once. */
    val wakeScreenDeniedReported: Boolean = false,
) {
    /** What [surface] should draw now, or `null` for nothing. */
    fun frameFor(surface: Surface): GlowFrame? = glow?.takeIf { it.surface == surface && it.opened }
        ?.let { GlowFrame(it.spec, visible = it.phase != Phase.FADING_OUT, instant = it.instant) }

    fun on(event: GlowEvent): GlowStep = Transition(this).apply { handle(event) }.step()
}

sealed interface GlowEvent {
    /** Inputs a component sends straight to the coordinator; the others are settings, samples or executor reports. */
    sealed interface External : GlowEvent

    data class Posted(val post: Post) : External

    data class Removed(val key: String) : External

    /** [activeKeys] and [ringingKeys] are read from the notifications already up when the listener connects. */
    data class ListenerConnected(val activeKeys: Set<String>, val ringingKeys: Set<String>) : External

    data object ListenerDisconnected : External

    data class SettingsChanged(val settings: GlowSettings) : GlowEvent

    data class EnvironmentChanged(val environment: Environment) : GlowEvent

    data class AccessibilityChanged(val available: Boolean) : External

    data class Preview(val target: PreviewTarget, val spec: GlowSpec) : External

    /** The result of the in-flight [GlowEffect.ExtractPalette]. */
    data class PaletteExtracted(val colors: List<Int>) : GlowEvent

    /**
     * The surface's latest [GlowEffect.Open] is up. Reported only for that instance, and only while it hasn't been
     * closed since; the adapter closes instances nobody asked for (e.g. an activity starting after its glow ended).
     */
    data class SurfaceOpened(val surface: Surface) : GlowEvent

    /**
     * The surface's latest open instance went away by itself: it failed to open, stopped, was touched, or its
     * accessibility service unbound. Same contract as [SurfaceOpened].
     */
    data class SurfaceGone(val surface: Surface) : GlowEvent

    data class TimerFired(val timer: Timer) : GlowEvent
}

sealed interface GlowEffect {
    /** Opens [surface]; the lock screen is woken through the full-screen-intent trampoline when [wakeScreen]. */
    data class Open(val surface: Surface, val wakeScreen: Boolean) : GlowEffect

    /** Closes [surface] if it is up; the lock screen also cancels its trampoline. Idempotent. */
    data class Close(val surface: Surface) : GlowEffect

    /** (Re-)arms [timer] to fire after [delayMs], replacing any earlier arming. */
    data class Arm(val timer: Timer, val delayMs: Long) : GlowEffect

    data class Disarm(val timer: Timer) : GlowEffect

    /**
     * Extracts [post]'s palette as [autoColors] (its post-time settings) say, replacing any in-flight extraction,
     * whose result is then never delivered.
     */
    data class ExtractPalette(val post: Post, val autoColors: AutoColors) : GlowEffect

    /** Cancels the in-flight extraction; its result is never delivered. */
    data object CancelExtraction : GlowEffect

    /**
     * A lock-screen glow was skipped: the trampoline can't wake the screen (see [Environment.canWakeScreen]). Emitted
     * once per listener connection.
     */
    data object WakeScreenDenied : GlowEffect
}

data class GlowStep(val state: GlowState, val effects: List<GlowEffect>)
