package app.rimlit.engine

import app.rimlit.model.GlowSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runs the machine: one serial queue of inputs, each turned into a [GlowState.on] step whose effects the
 * [GlowExecutor] runs on the [GlowDriver]. Main thread only.
 *
 * - **Settings gate the queue.** The machine is created on the first [settingsChanged]; inputs that arrive earlier
 *   wait in order, so nothing is decided on defaults and a removal can't overtake its post.
 * - **The environment is sampled before every input**, and sent first as [GlowEvent.EnvironmentChanged] when it
 *   changed: the keyguard can lock seconds after the screen goes off, and the grant has no broadcast.
 * - **Executor contracts are checked at dispatch.** Timer, palette and surface reports are queued with a check that
 *   they are still current when their turn comes, so a stale one is dropped instead of reaching the machine.
 */
class GlowCoordinator(driver: (GlowCoordinator) -> GlowDriver) {

    private val driver = driver(this)
    private val executor = GlowExecutor(this.driver) { take -> enqueue(Input.Report(take)) }

    private val machine = MutableStateFlow<GlowState?>(null)

    /** The machine's state, `null` until settings load. Surfaces draw `state.frameFor(surface)`. */
    val state: StateFlow<GlowState?> = machine.asStateFlow()

    private val queue = ArrayDeque<Input>()
    private var draining = false

    fun settingsChanged(settings: GlowSettings) {
        if (machine.value == null) {
            machine.value = GlowState(settings, driver.environment(), accessibilityOverlay = false)
            drain()
        } else {
            enqueue(Input.Event(GlowEvent.SettingsChanged(settings)))
        }
    }

    /** A notification was posted; [extractPalette] reads its colours if the machine asks for them. */
    fun posted(post: Post, extractPalette: () -> List<Int>) = enqueue(Input.Posted(post, extractPalette))

    fun send(event: GlowEvent.External) = enqueue(Input.Event(event))

    /** The screen or keyguard may have changed: re-sample before the next input. */
    fun environmentChanged() = enqueue(Input.Sample)

    /** [instance] of [surface] is up. An instance nobody asked for is closed. */
    fun surfaceOpened(surface: Surface, instance: SurfaceInstance) = executor.surfaceOpened(surface, instance)

    /** [instance] of [surface] went away by itself; `null` if it failed to open. */
    fun surfaceGone(surface: Surface, instance: SurfaceInstance?) = executor.surfaceGone(surface, instance)

    private fun enqueue(input: Input) {
        queue.addLast(input)
        drain()
    }

    /** Runs queued inputs in order; inputs queued meanwhile (by effects) wait for the current step to finish. */
    private fun drain() {
        if (draining || machine.value == null) return
        draining = true
        try {
            while (queue.isNotEmpty()) dispatch(queue.removeFirst())
        } finally {
            draining = false
        }
    }

    private fun dispatch(input: Input) {
        // Sampled before a report is checked: the change may end what the report is about.
        val environment = driver.environment()
        if (environment != checkNotNull(machine.value).environment) step(GlowEvent.EnvironmentChanged(environment))
        val event = when (input) {
            Input.Sample -> return
            is Input.Event -> input.event
            is Input.Posted -> GlowEvent.Posted(input.post)
            is Input.Report -> input.take() ?: return
        }
        step(event, (input as? Input.Posted)?.extractPalette)
    }

    private fun step(event: GlowEvent, extractPalette: (() -> List<Int>)? = null) {
        val next = checkNotNull(machine.value).on(event)
        machine.value = next.state
        next.effects.forEach { executor.execute(it, next.state, extractPalette) }
    }

    private sealed interface Input {
        /** Only re-samples the environment. */
        data object Sample : Input

        data class Event(val event: GlowEvent) : Input

        class Posted(val post: Post, val extractPalette: () -> List<Int>) : Input

        /** An executor report: [take] yields its event, or `null` if it is no longer current. */
        class Report(val take: () -> GlowEvent?) : Input
    }
}
