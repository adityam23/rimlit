package app.rimlit.engine

import java.util.EnumMap

/**
 * Runs the machine's effects on the [driver] and holds the executor contracts: every timer, palette and surface
 * report goes to [report] with a check that it is still current, evaluated when its turn in the queue comes.
 */
internal class GlowExecutor(
    private val driver: GlowDriver,
    /** Queues a report: the function yields its event, or `null` if it is no longer current. */
    private val report: (() -> GlowEvent?) -> Unit,
) {

    private val slots = EnumMap<Surface, SurfaceSlot>(Surface::class.java).apply {
        Surface.entries.forEach { put(it, SurfaceSlot()) }
    }
    private val timers = EnumMap<Timer, Cancellable>(Timer::class.java)
    private var extraction: Cancellable? = null
    private var reportedFullScreenIntentDenied = false

    /** Runs [effect] of a step that led to [state]; [extractPalette] reads the colours of the post just dispatched. */
    fun execute(effect: GlowEffect, state: GlowState, extractPalette: (() -> List<Int>)?) {
        when (effect) {
            is GlowEffect.Open -> {
                val glow = checkNotNull(state.glow) { "Opens only for its glow" }
                slots.getValue(effect.surface).open()
                driver.open(effect.surface, effect.wakeScreen, glow.spec)
            }

            is GlowEffect.Close -> {
                slots.getValue(effect.surface).close()
                driver.close(effect.surface)
            }

            is GlowEffect.Arm -> arm(effect.timer, effect.delayMs)

            is GlowEffect.Disarm -> timers.remove(effect.timer)?.cancel()

            is GlowEffect.ExtractPalette -> extract(checkNotNull(extractPalette) { "Extracts only for its post" })

            GlowEffect.CancelExtraction -> {
                extraction?.cancel()
                extraction = null
            }

            GlowEffect.FullScreenIntentDenied -> if (!reportedFullScreenIntentDenied) {
                reportedFullScreenIntentDenied = true
                driver.fullScreenIntentDenied()
            }
        }
    }

    /** The missing grant is reported again after the listener reconnects. */
    fun listenerConnected() {
        reportedFullScreenIntentDenied = false
    }

    fun surfaceOpened(surface: Surface, instance: SurfaceInstance) {
        val isCurrent = slots.getValue(surface).opened(instance) ?: return
        report { GlowEvent.SurfaceOpened(surface).takeIf { isCurrent() } }
    }

    fun surfaceGone(surface: Surface, instance: SurfaceInstance?) {
        val isCurrent = slots.getValue(surface).gone(instance) ?: return
        report { GlowEvent.SurfaceGone(surface).takeIf { isCurrent() } }
    }

    /** A timer fires only for its latest arming: re-arming or disarming replaces the handle it checks against. */
    private fun arm(timer: Timer, delayMs: Long) {
        timers.remove(timer)?.cancel()
        lateinit var handle: Cancellable
        handle = driver.schedule(delayMs) {
            report { GlowEvent.TimerFired(timer).takeIf { timers.remove(timer, handle) } }
        }
        timers[timer] = handle
    }

    /** Delivers only to the extraction still in flight; a replaced or cancelled one is dropped. */
    private fun extract(extractPalette: () -> List<Int>) {
        extraction?.cancel()
        lateinit var handle: Cancellable
        handle = driver.extract(extractPalette) { colors ->
            report { GlowEvent.PaletteExtracted(colors).takeIf { extraction === handle }?.also { extraction = null } }
        }
        extraction = handle
    }
}
