package app.rimlit.ui.setup

import app.rimlit.model.DEFAULT_LOOK_ID
import app.rimlit.model.GlowSettings
import app.rimlit.model.Look
import app.rimlit.model.MAX_LOOKS
import app.rimlit.model.Premade
import app.rimlit.model.UiPrefs
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The guided first launch run for the first time, or again: a rerun leaves what was set up as it is. */
class SetupRunTest {

    private val settings = GlowSettings(looks = listOf(Look(speed = 2f), Look(id = "work", name = "Work")))

    private fun full() = GlowSettings(looks = listOf(Look()) + (2..MAX_LOOKS).map { Look(id = "$it", name = "Look $it") })

    @Test
    fun aRerunOnceSetupIsFinished() {
        assertEquals(SetupRun.FIRST, SetupRun.of(UiPrefs()))
        assertEquals(SetupRun.FIRST, SetupRun.of(null), "not read: never treated as finished")
        assertEquals(SetupRun.RERUN, SetupRun.of(UiPrefs(setupDone = true)))
    }

    @Test
    fun onlyTheFirstFinishTurnsGlowingOn() {
        val off = settings.copy(enabled = false)
        assertTrue(SetupRun.FIRST.finishing(off).enabled)
        assertEquals(off, SetupRun.RERUN.finishing(off), "the switch, and everything else, as it was")
        assertEquals(off.copy(enabled = true), SetupRun.RERUN.finishing(off.copy(enabled = true)))
    }

    @Test
    fun theFirstRunSwitchesTheDefaultLookToThePremade() {
        val picked = assertNotNull(SetupRun.FIRST.picking(settings, Premade.WAVE, "Wave"))
        assertEquals(Premade.WAVE.look(DEFAULT_LOOK_ID, "Wave"), picked.defaultLook)
        assertEquals(settings.looks.size, picked.looks.size, "none added")
        assertTrue(SetupRun.FIRST.canPick(full()), "no look is added, so the limit doesn't matter")
        assertNotNull(SetupRun.FIRST.picking(full(), Premade.WAVE, "Wave"))
    }

    @Test
    fun aRerunAddsThePremadeAsTheDefaultLookAndKeepsTheOldOne() {
        val picked = assertNotNull(SetupRun.RERUN.picking(settings, Premade.WAVE, "Wave"))
        assertNotEquals(DEFAULT_LOOK_ID, picked.defaultLookId)
        assertEquals(Premade.WAVE, picked.defaultLook.basedOn)
        assertEquals("Wave", picked.defaultLook.name)
        assertEquals(settings.looks, picked.looks.dropLast(1), "the old default untouched, still in the list")
        assertEquals("Work (2)", SetupRun.RERUN.picking(settings, Premade.BLADE, "Work")?.defaultLook?.name)
    }

    @Test
    fun aRerunCantPickAtTheLookLimit() {
        assertTrue(SetupRun.RERUN.canPick(settings))
        assertFalse(SetupRun.RERUN.canPick(full()))
        assertNull(SetupRun.RERUN.picking(full(), Premade.WAVE, "Wave"))
    }
}
