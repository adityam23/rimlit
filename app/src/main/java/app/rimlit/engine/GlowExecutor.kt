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
    private val timers = EnumMap<Timer, LatestHandle>(Timer::class.java).apply {
        Timer.entries.forEach { put(it, LatestHandle()) }
    }
    private val extraction = LatestHandle()

    /** Runs [effect] of a step that led to [state]. */
    fun execute(effect: GlowEffect, state: GlowState) {
        when (effect) {
            is GlowEffect.Open -> {
                val glow = checkNotNull(state.glow) { "Opens only for its glow" }
                slots.getValue(effect.surface).open { driver.open(effect.surface, effect.wakeScreen, glow.spec) }
            }

            is GlowEffect.Close -> slots.getValue(effect.surface).close()

            is GlowEffect.Arm -> timers.getValue(effect.timer).replace { claim ->
                driver.schedule(effect.delayMs) { report { GlowEvent.TimerFired(effect.timer).takeIf { claim() } } }
            }

            is GlowEffect.Disarm -> timers.getValue(effect.timer).cancel()

            is GlowEffect.ExtractPalette -> extraction.replace { claim ->
                driver.extract(effect.post) { colors ->
                    report { GlowEvent.PaletteExtracted(colors).takeIf { claim() } }
                }
            }

            GlowEffect.CancelExtraction -> extraction.cancel()

            GlowEffect.WakeScreenDenied -> driver.wakeScreenDenied()
        }
    }

    fun surfaceOpened(surface: Surface, instance: SurfaceInstance) {
        val isCurrent = slots.getValue(surface).opened(instance) ?: return
        report { GlowEvent.SurfaceOpened(surface).takeIf { isCurrent() } }
    }

    /** [instance] is closed in any case: it is gone, whether or not it is the one the machine shows. */
    fun surfaceGone(surface: Surface, instance: SurfaceInstance?) {
        val isCurrent = slots.getValue(surface).gone(instance)
        instance?.close()
        isCurrent ?: return
        report { GlowEvent.SurfaceGone(surface).takeIf { isCurrent() } }
    }
}
