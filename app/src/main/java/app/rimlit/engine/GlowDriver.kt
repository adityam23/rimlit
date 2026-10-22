package app.rimlit.engine

import app.rimlit.model.GlowSpec

/**
 * The coordinator's only way to reach the platform: device state, surfaces, time and palette extraction. Called on
 * the main thread, and every callback it takes must run there too.
 */
interface GlowDriver {
    /** The screen, keyguard and full-screen-intent grant as they are now. */
    fun environment(): Environment

    /**
     * Starts opening [surface] for a glow of [spec]. The surface reports back through
     * [GlowCoordinator.surfaceOpened] or [GlowCoordinator.surfaceGone], now or later.
     */
    fun open(surface: Surface, wakeScreen: Boolean, spec: GlowSpec)

    /** [surface]'s instance was closed; the lock screen also cancels its trampoline. */
    fun close(surface: Surface)

    /** Runs [action] after [delayMs] unless cancelled first. */
    fun schedule(delayMs: Long, action: () -> Unit): Cancellable

    /** Runs [extract] and hands its colours to [deliver], unless cancelled first. */
    fun extract(extract: () -> List<Int>, deliver: (List<Int>) -> Unit): Cancellable

    /** A lock-screen glow was skipped for the missing full-screen-intent grant; at most once per connection. */
    fun fullScreenIntentDenied()
}

fun interface Cancellable {
    fun cancel()
}

/** One live instance of a surface: an overlay window or a lock-screen activity. */
fun interface SurfaceInstance {
    fun close()
}
