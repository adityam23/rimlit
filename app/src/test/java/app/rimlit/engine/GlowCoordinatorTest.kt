package app.rimlit.engine

import app.rimlit.engine.Fixtures.AUTO
import app.rimlit.engine.Fixtures.CUSTOM
import app.rimlit.engine.Fixtures.LOCK_SCREEN
import app.rimlit.engine.Fixtures.SCREEN_OFF
import app.rimlit.engine.Fixtures.UNLOCKED
import app.rimlit.engine.Fixtures.post
import app.rimlit.model.AutoColors
import app.rimlit.model.GlowPalette
import app.rimlit.model.GlowSpec
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The coordinator's own guarantees: the settings gate, fresh environment samples and the executor contracts. */
class GlowCoordinatorTest {

    private class Instance : SurfaceInstance {
        var closed = false

        override fun close() {
            closed = true
        }
    }

    /** A virtual clock and recorded surfaces. [honoursCancel] off simulates a timer or extraction that races its cancel. */
    private class FakeDriver(var honoursCancel: Boolean = true) : GlowDriver {
        var environment = UNLOCKED
        var now = 0L
        val opens = mutableListOf<Surface>()
        var onOpen: (Surface) -> Unit = {}
        var deferExtraction = false
        val colorsByKey = mutableMapOf<String, List<Int>>()
        val extractions = mutableListOf<() -> Unit>()
        private val scheduled = mutableListOf<Triple<Long, () -> Unit, BooleanArray>>()

        var samples = 0

        override fun environment() = environment.also { samples++ }

        val withdrawn = mutableListOf<Surface>()

        override fun open(surface: Surface, wakeScreen: Boolean, spec: GlowSpec): Cancellable {
            opens += surface
            onOpen(surface)
            return Cancellable { withdrawn += surface }
        }

        override fun schedule(delayMs: Long, action: () -> Unit): Cancellable {
            val cancelled = booleanArrayOf(false)
            scheduled += Triple(now + delayMs, action, cancelled)
            return Cancellable { if (honoursCancel) cancelled[0] = true }
        }

        override fun extract(post: Post, autoColors: AutoColors, deliver: (List<Int>) -> Unit): Cancellable {
            val colors = colorsByKey.getValue(post.key)
            if (!deferExtraction) {
                deliver(colors)
                return Cancellable {}
            }
            val cancelled = booleanArrayOf(false)
            extractions += { if (!cancelled[0]) deliver(colors) }
            return Cancellable { if (honoursCancel) cancelled[0] = true }
        }

        override fun wakeScreenDenied() = Unit

        /** Runs every timer due within [ms], in order. */
        fun advance(ms: Long) {
            val until = now + ms
            while (true) {
                val next = scheduled.filter { it.first <= until }.minByOrNull { it.first } ?: break
                scheduled -= next
                now = next.first
                if (!next.third[0]) next.second()
            }
            now = until
        }
    }

    private val driver = FakeDriver()
    private val coordinator = GlowCoordinator { driver }

    /** Posts [key], whose notification extracts to [colors]. */
    private fun posted(key: String, colors: List<Int> = listOf(0xFF00FF00.toInt())) {
        driver.colorsByKey[key] = colors
        coordinator.send(GlowEvent.Posted(post(key)))
    }

    private val glow get() = coordinator.state.value?.glow

    @Test
    fun inputsWaitForSettingsInOrder() {
        posted("a")
        coordinator.send(GlowEvent.Removed("a"))
        posted("b")
        assertNull(coordinator.state.value, "nothing is decided before settings load")
        assertTrue(driver.opens.isEmpty())

        coordinator.settingsChanged(CUSTOM)
        // a opened, its removal closed it, then b opened: the removal didn't overtake its post.
        assertEquals(listOf(Surface.APP_OVERLAY, Surface.APP_OVERLAY), driver.opens)
        assertEquals("b", glow?.source?.post?.key)
    }

    @Test
    fun aGlowRunsItsTimelineAndClosesItsWindow() {
        coordinator.settingsChanged(CUSTOM)
        posted("a")
        val window = Instance()
        coordinator.surfaceOpened(Surface.APP_OVERLAY, window)
        assertEquals(true, coordinator.state.value?.frameFor(Surface.APP_OVERLAY)?.visible)

        driver.advance(FADE_IN_MS + CUSTOM.durationMs)
        assertEquals(false, coordinator.state.value?.frameFor(Surface.APP_OVERLAY)?.visible, "fading out")
        driver.advance(FADE_OUT_MS)
        assertNull(glow)
        assertTrue(window.closed)
    }

    @Test
    fun theEnvironmentIsSampledBeforeEveryInputThatRoutes() {
        coordinator.settingsChanged(CUSTOM)
        driver.environment = LOCK_SCREEN
        posted("a")
        assertNull(glow, "locked without the accessibility service: nowhere")

        driver.environment = UNLOCKED // no broadcast: the next post samples it
        posted("b")
        assertEquals(Surface.APP_OVERLAY, glow?.surface)

        val samples = driver.samples
        coordinator.send(GlowEvent.Removed("c"))
        coordinator.send(GlowEvent.AccessibilityChanged(available = true))
        assertEquals(samples, driver.samples, "inputs that don't route don't sample")
        coordinator.environmentChanged()
        assertEquals(samples + 1, driver.samples)
    }

    @Test
    fun aReportOvertakenByAnEarlierInputIsDropped() {
        coordinator.settingsChanged(CUSTOM)
        val window = Instance()
        // Both arrive while the post's step runs: the removal is queued first and closes what the report is about.
        driver.onOpen = {
            coordinator.send(GlowEvent.Removed("a"))
            coordinator.surfaceOpened(Surface.APP_OVERLAY, window)
        }
        posted("a")
        assertNull(glow)
        assertTrue(window.closed)
    }

    @Test
    fun staleTimersNeverReachTheMachine() {
        driver.honoursCancel = false
        coordinator.settingsChanged(CUSTOM)
        posted("a")
        coordinator.surfaceOpened(Surface.APP_OVERLAY, Instance())
        driver.advance(FADE_IN_MS + 100)
        posted("b") // re-targets: the hold's arming is replaced, though its timer still runs
        driver.advance(CUSTOM.durationMs)
        assertEquals("b", glow?.source?.post?.key)
        driver.advance(FADE_IN_MS + CUSTOM.durationMs + FADE_OUT_MS)
        assertNull(glow)
    }

    @Test
    fun onlyTheExtractionInFlightDelivers() {
        driver.honoursCancel = false
        driver.deferExtraction = true
        coordinator.settingsChanged(AUTO)
        posted("a", colors = listOf(1))
        posted("b", colors = listOf(2))
        driver.extractions.forEach { it() }
        assertEquals("b", glow?.source?.post?.key)
        assertEquals(GlowPalette.Colors(listOf(2)), glow?.spec?.palette)
    }

    @Test
    fun theLatestStartedLockScreenInstanceIsBound() {
        coordinator.settingsChanged(CUSTOM)
        driver.environment = SCREEN_OFF
        posted("a")
        assertEquals(listOf(Surface.LOCK_SCREEN), driver.opens)
        driver.environment = LOCK_SCREEN // woken by the trampoline
        val first = Instance()
        val second = Instance()
        coordinator.surfaceOpened(Surface.LOCK_SCREEN, first)
        coordinator.surfaceOpened(Surface.LOCK_SCREEN, second)
        coordinator.surfaceGone(Surface.LOCK_SCREEN, first)
        assertNotNull(glow, "the stop of a replaced instance is ignored")
        assertTrue(first.closed, "a reported instance is closed in any case")

        coordinator.surfaceGone(Surface.LOCK_SCREEN, second)
        assertNull(glow)
    }

    @Test
    fun anInstanceNobodyAskedForIsClosed() {
        coordinator.settingsChanged(CUSTOM)
        driver.environment = SCREEN_OFF
        posted("a")
        driver.advance(OPEN_TIMEOUT_MS) // the full-screen intent was never delivered
        assertNull(glow)
        assertEquals(listOf(Surface.LOCK_SCREEN), driver.withdrawn, "closing withdraws the request (the trampoline)")

        val late = Instance()
        coordinator.surfaceOpened(Surface.LOCK_SCREEN, late)
        assertTrue(late.closed)
        assertFalse(driver.opens.size > 1)
    }
}
