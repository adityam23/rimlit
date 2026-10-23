package app.rimlit.engine

import app.rimlit.engine.Fixtures.AUTO
import app.rimlit.engine.Fixtures.CUSTOM
import app.rimlit.engine.Fixtures.EXTRACTED
import app.rimlit.engine.Fixtures.LOCK_SCREEN
import app.rimlit.engine.Fixtures.PREVIEW_SPEC
import app.rimlit.engine.Fixtures.SCREEN_OFF
import app.rimlit.engine.Fixtures.UNLOCKED
import app.rimlit.engine.Fixtures.post
import app.rimlit.model.GlowSettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The decided semantics, one scenario each. The model check proves nothing goes wrong; these prove what goes right. */
class GlowMachineScenarioTest {

    /** Drives the machine and keeps the effects of the last [send]. */
    private class Run(var state: GlowState) {
        var effects: List<GlowEffect> = emptyList()

        fun send(vararg events: GlowEvent) = apply {
            effects = events.flatMap { event -> state.on(event).also { state = it.state }.effects }
        }

        fun fire(vararg timers: Timer) = send(*timers.map { GlowEvent.TimerFired(it) }.toTypedArray())

        fun environment(environment: Environment) = send(GlowEvent.EnvironmentChanged(environment))

        val glow: Glow get() = checkNotNull(state.glow)
    }

    private fun run(settings: GlowSettings = CUSTOM, environment: Environment = UNLOCKED, accessibility: Boolean = false) =
        Run(Fixtures.initial(settings, environment, accessibility))

    private fun posted(key: String, ringing: Boolean = false, ongoing: Boolean = ringing) =
        GlowEvent.Posted(post(key, fullScreenIntent = ringing, ongoing = ongoing))

    private fun opened(surface: Surface) = GlowEvent.SurfaceOpened(surface)

    /** Posts [key] on screen and lets it fade in, so it is holding. */
    private fun holding(key: String = "a") = run().send(posted(key), opened(Surface.APP_OVERLAY)).fire(Timer.FADE_IN)

    /** A lock-screen glow woken from screen off, opened and holding, with the screen now on and locked. */
    private fun onLockScreen(settings: GlowSettings = CUSTOM) = run(settings, SCREEN_OFF).send(posted("a"))
        .environment(LOCK_SCREEN).send(opened(Surface.LOCK_SCREEN)).fire(Timer.FADE_IN)

    private fun List<GlowEffect>.surfaceEffects() = filter { it is GlowEffect.Open || it is GlowEffect.Close }

    @Test
    fun aPostRunsItsWholeTimelineOnTheAppOverlay() {
        val run = run().send(posted("a"))
        assertEquals(GlowEffect.Open(Surface.APP_OVERLAY, wakeScreen = false), run.effects.first())
        assertNull(run.state.frameFor(Surface.APP_OVERLAY), "nothing is drawn before the surface opens")

        run.send(opened(Surface.APP_OVERLAY))
        assertEquals(Phase.FADING_IN, run.glow.phase)
        assertEquals(true, run.state.frameFor(Surface.APP_OVERLAY)?.visible)

        run.fire(Timer.FADE_IN)
        assertTrue(GlowEffect.Arm(Timer.HOLD, CUSTOM.durationMs) in run.effects, "holds for the user's duration")

        run.fire(Timer.HOLD)
        assertEquals(false, run.state.frameFor(Surface.APP_OVERLAY)?.visible)

        run.fire(Timer.FADE_OUT)
        assertNull(run.state.glow)
        assertEquals(listOf(GlowEffect.Close(Surface.APP_OVERLAY)), run.effects.surfaceEffects())
    }

    @Test
    fun aNewerPostRetargetsTheGlowAndRestartsTheHold() {
        val run = holding("a").send(posted("b"))
        assertEquals("b", run.glow.source.post?.key)
        assertEquals(Phase.FADING_IN, run.glow.phase)
        assertTrue(run.effects.surfaceEffects().isEmpty(), "no flicker")
        assertTrue(GlowEffect.Disarm(Timer.HOLD) in run.effects)
    }

    @Test
    fun onlyRemovingTheShownNotificationEndsTheGlowEarly() {
        val run = holding("a").send(posted("b"), GlowEvent.Removed("a"))
        assertEquals(Phase.FADING_IN, run.glow.phase, "removing a notification that isn't shown changes nothing")

        run.send(GlowEvent.Removed("b"))
        assertEquals(Phase.FADING_OUT, run.glow.phase)
    }

    @Test
    fun theLockScreenGlowWakesTheScreenAndEndsEarlyWhenItsNotificationIsRemoved() {
        val run = run(environment = SCREEN_OFF).send(posted("a"))
        assertEquals(GlowEffect.Open(Surface.LOCK_SCREEN, wakeScreen = true), run.effects.first())

        run.environment(LOCK_SCREEN).send(opened(Surface.LOCK_SCREEN), GlowEvent.Removed("a"))
        assertEquals(Phase.FADING_OUT, run.glow.phase)
    }

    @Test
    fun ringingGlowsOnScreenOnlyAndEndsWhenAnswered() {
        val off = run(environment = SCREEN_OFF).send(posted("a", ringing = true))
        assertNull(off.state.glow, "the opaque activity would cover the call screen")

        val on = run().send(posted("a", ringing = true), opened(Surface.APP_OVERLAY))
        on.send(posted("a", ongoing = true)) // answered: re-posted, still ongoing, without its full-screen intent
        assertEquals(Phase.FADING_OUT, on.glow.phase)
    }

    @Test
    fun anAnsweredCallStillExtractingItsColoursNeverGlows() {
        val run = run(settings = AUTO).send(posted("a", ringing = true))
        assertEquals("a", run.state.pending?.post?.key)

        run.send(posted("a", ongoing = true))
        assertNull(run.state.pending)
        assertEquals(listOf(GlowEffect.CancelExtraction), run.effects)
    }

    @Test
    fun nothingCoversARingingCallScreen() {
        // A post still extracting when a call arrives could otherwise launch over the call screen.
        val pending = run(AUTO, SCREEN_OFF).send(posted("b"), posted("a", ringing = true))
        assertNull(pending.state.pending)

        // While it rings, no lock-screen glow opens…
        pending.send(posted("b"))
        assertNull(pending.state.pending)
        assertTrue(pending.effects.none { it is GlowEffect.Open })

        // …and one already up ends.
        val shown = onLockScreen().send(posted("b", ringing = true))
        assertNull(shown.state.glow)
        assertEquals(listOf(GlowEffect.Close(Surface.LOCK_SCREEN)), shown.effects.surfaceEffects())
    }

    @Test
    fun screenOffRightAfterAGlowStartsHandsItOffToTheLockScreen() {
        val run = run().send(posted("a"), opened(Surface.APP_OVERLAY)).environment(SCREEN_OFF)
        assertEquals(
            listOf(GlowEffect.Close(Surface.APP_OVERLAY), GlowEffect.Open(Surface.LOCK_SCREEN, wakeScreen = true)),
            run.effects.surfaceEffects(),
        )
        assertEquals(Phase.FADING_IN, run.glow.phase, "the timeline keeps running across the hand-off")

        run.environment(LOCK_SCREEN).send(opened(Surface.LOCK_SCREEN))
        assertEquals(true, run.state.frameFor(Surface.LOCK_SCREEN)?.instant)
    }

    @Test
    fun screenOffLaterInTheHoldLetsTheGlowFadeInTheDark() {
        val run = holding().fire(Timer.HAND_OFF_WINDOW).environment(SCREEN_OFF)
        assertTrue(run.effects.isEmpty())
        assertEquals(Surface.APP_OVERLAY, run.glow.surface)
    }

    @Test
    fun previewsNeverHandOff() {
        val run = run().send(GlowEvent.Preview(PreviewTarget.ON_SCREEN, PREVIEW_SPEC), opened(Surface.APP_OVERLAY))
            .environment(SCREEN_OFF)
        assertTrue(run.effects.surfaceEffects().isEmpty())
    }

    @Test
    fun unlockingDuringTheLockScreenGlowMovesItOnScreenInstantly() {
        val run = onLockScreen().environment(UNLOCKED)
        assertEquals(
            listOf(GlowEffect.Close(Surface.LOCK_SCREEN), GlowEffect.Open(Surface.APP_OVERLAY, wakeScreen = false)),
            run.effects.surfaceEffects(),
        )
        assertTrue(run.effects.none { it is GlowEffect.Arm && it.timer == Timer.HOLD }, "the remaining hold carries on")

        run.send(opened(Surface.APP_OVERLAY))
        assertEquals(GlowFrame(run.glow.spec, visible = true, instant = true), run.state.frameFor(Surface.APP_OVERLAY))
    }

    @Test
    fun aHandedOffGlowStillFadesOut() {
        val run = onLockScreen().environment(UNLOCKED).send(opened(Surface.APP_OVERLAY)).fire(Timer.HOLD)
        assertEquals(GlowFrame(run.glow.spec, visible = false, instant = false), run.state.frameFor(Surface.APP_OVERLAY))
    }

    @Test
    fun screenOffSoonAfterUnlockingDoesntWakeTheScreenAgain() {
        val run = onLockScreen().environment(UNLOCKED).send(opened(Surface.APP_OVERLAY)).environment(SCREEN_OFF)
        assertTrue(run.effects.none { it is GlowEffect.Open }, "the user already saw it on the lock screen")
    }

    @Test
    fun screenOffEndsTheLockScreenGlow() {
        val run = onLockScreen().environment(SCREEN_OFF)
        assertNull(run.state.glow)

        run.send(posted("b"))
        assertEquals(GlowEffect.Open(Surface.LOCK_SCREEN, wakeScreen = true), run.effects.first(), "woken again")
    }

    @Test
    fun aLockScreenGlowIsRetargetedInsteadOfRelaunched() {
        val run = onLockScreen().send(posted("b"))
        assertEquals("b", run.glow.source.post?.key)
        assertTrue(run.effects.none { it is GlowEffect.Open })
    }

    @Test
    fun aNewerPostSupersedesAPendingOne() {
        val run = run(settings = AUTO).send(posted("a"), posted("b"))
        assertEquals("b", run.state.pending?.post?.key)

        run.send(GlowEvent.PaletteExtracted(EXTRACTED))
        assertEquals("b", run.glow.source.post?.key)
        assertNull(run.state.pending)
    }

    @Test
    fun aPostRemovedWhilePendingIsNeverShown() {
        val run = run(settings = AUTO).send(posted("a")).send(GlowEvent.Removed("a"))
        assertNull(run.state.pending)
        assertEquals(listOf(GlowEffect.CancelExtraction), run.effects)
    }

    @Test
    fun revokingNotificationAccessDropsAPendingPost() {
        val run = run(settings = AUTO).send(posted("a")).send(GlowEvent.ListenerDisconnected)
        assertNull(run.state.pending)
        assertEquals(listOf(GlowEffect.CancelExtraction), run.effects)
    }

    @Test
    fun previewsAndPostsReplaceEachOtherAndNoRemovalEndsAPreview() {
        val run = holding("a").send(GlowEvent.Preview(PreviewTarget.ON_SCREEN, PREVIEW_SPEC))
        assertEquals(GlowSource.Preview(PreviewTarget.ON_SCREEN), run.glow.source)

        run.send(GlowEvent.Removed("a"))
        assertEquals(Phase.FADING_IN, run.glow.phase)

        run.send(posted("b"))
        assertEquals("b", run.glow.source.post?.key)
    }

    @Test
    fun theTrampolineIsNotACall() {
        val trampoline = GlowEvent.Posted(post("t", fullScreenIntent = true, ongoing = false, own = true))
        val run = onLockScreen().send(trampoline)
        assertEquals(Surface.LOCK_SCREEN, run.glow.surface, "waking the screen doesn't end the glow it woke it for")
        assertTrue(run.effects.isEmpty())
        assertTrue(run.state.ringingKeys.isEmpty())
    }

    @Test
    fun aFailedOrLostSurfaceReturnsToIdle() {
        val failed = run().send(posted("a"), GlowEvent.SurfaceGone(Surface.APP_OVERLAY))
        assertNull(failed.state.glow)

        val lost = run(environment = LOCK_SCREEN, accessibility = true).send(posted("a"))
            .send(opened(Surface.ACCESSIBILITY_OVERLAY), GlowEvent.SurfaceGone(Surface.ACCESSIBILITY_OVERLAY))
        assertNull(lost.state.glow, "no fallback to the app overlay")
    }

    @Test
    fun withoutFullScreenIntentAccessALockScreenGlowIsSkippedAndReported() {
        val denied = SCREEN_OFF.copy(fullScreenIntentGranted = false)
        val post = run(environment = denied).send(posted("a"))
        assertNull(post.state.glow)
        assertEquals(listOf(GlowEffect.FullScreenIntentDenied), post.effects)

        val preview = run(environment = denied).send(GlowEvent.Preview(PreviewTarget.LOCK_SCREEN, PREVIEW_SPEC))
        assertEquals(listOf(GlowEffect.FullScreenIntentDenied), preview.effects)
    }

    @Test
    fun theMissingGrantIsReportedOncePerListenerConnection() {
        val run = run(environment = SCREEN_OFF.copy(fullScreenIntentGranted = false)).send(posted("a"))
        assertEquals(listOf(GlowEffect.FullScreenIntentDenied), run.effects)
        assertTrue(run.send(posted("b")).effects.isEmpty(), "skipped again, not reported again")

        run.send(GlowEvent.ListenerConnected(emptySet(), emptySet()), posted("c"))
        assertEquals(listOf(GlowEffect.FullScreenIntentDenied), run.effects)
    }

    @Test
    fun aSurfaceThatNeverConfirmsIsGivenUp() {
        val run = run(environment = SCREEN_OFF).send(posted("a")).fire(Timer.OPEN)
        assertNull(run.state.glow)
        assertTrue(GlowEffect.Close(Surface.LOCK_SCREEN) in run.effects, "closing also cancels the trampoline")
    }

    @Test
    fun executorContractViolationsFailLoudly() {
        assertFailsWith<IllegalStateException> { run().send(GlowEvent.TimerFired(Timer.HOLD)) }
        assertFailsWith<IllegalStateException> { run().send(GlowEvent.PaletteExtracted(EXTRACTED)) }
        assertFailsWith<IllegalStateException> { run().send(posted("a"), opened(Surface.LOCK_SCREEN)) }
        assertFailsWith<IllegalStateException> { holding().send(opened(Surface.APP_OVERLAY)) }
        assertFailsWith<IllegalStateException> { run().send(GlowEvent.SurfaceGone(Surface.APP_OVERLAY)) }
        assertFalse(run().send(posted("a")).state.glow?.opened ?: true)
    }
}
