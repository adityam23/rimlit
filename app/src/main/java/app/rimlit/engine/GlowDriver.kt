package app.rimlit.engine

import app.rimlit.model.AutoColors
import app.rimlit.model.GlowSpec

/**
 * The coordinator's only way to reach the platform: device state, surfaces, time and palette extraction. Called on
 * the main thread, and every callback it takes must run there too.
 */
interface GlowDriver {
    /** The screen, keyguard and what waking the screen needs, as they are now. */
    fun environment(): Environment

    /**
     * Starts opening [surface] for a glow of [spec]. The surface reports back through
     * [GlowCoordinator.surfaceOpened] or [GlowCoordinator.surfaceGone], now or later. Returns the handle that
     * withdraws the request when the surface is closed: the lock screen's cancels its trampoline.
     */
    fun open(surface: Surface, wakeScreen: Boolean, spec: GlowSpec): Cancellable

    /** Runs [action] after [delayMs] unless cancelled first. */
    fun schedule(delayMs: Long, action: () -> Unit): Cancellable

    /** Reads [post]'s colours as [autoColors] say and hands them to [deliver], unless cancelled first. */
    fun extract(post: Post, autoColors: AutoColors, deliver: (List<Int>) -> Unit): Cancellable

    /** A lock-screen glow was skipped because the screen can't be woken; once per listener connection. */
    fun wakeScreenDenied()
}

fun interface Cancellable {
    fun cancel()
}

/** One live instance of a surface: an overlay window or a lock-screen activity. */
fun interface SurfaceInstance {
    fun close()
}
