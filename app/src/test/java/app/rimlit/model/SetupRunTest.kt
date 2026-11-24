package app.rimlit.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The guided first launch run for the first time, or again: a rerun leaves what was set up as it is. */
class SetupRunTest {

    private val added = "added"
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
        val picked = assertNotNull(SetupRun.FIRST.picking(settings, Premade.WAVE, "Wave", added))
        assertEquals(Premade.WAVE.look(DEFAULT_LOOK_ID, "Wave"), picked.defaultLook)
        assertEquals(settings.looks.size, picked.looks.size, "none added")
        assertTrue(SetupRun.FIRST.canPick(full(), added), "no look is added, so the limit doesn't matter")
        assertNotNull(SetupRun.FIRST.picking(full(), Premade.WAVE, "Wave", added))
    }

    @Test
    fun aRerunAddsThePremadeAsTheDefaultLookAndKeepsTheOldOne() {
        val picked = assertNotNull(SetupRun.RERUN.picking(settings, Premade.WAVE, "Wave", added))
        assertEquals(Premade.WAVE.look(added, "Wave"), picked.defaultLook)
        assertEquals(settings.looks, picked.looks.dropLast(1), "the old default untouched, still in the list")
        assertEquals("Work (2)", SetupRun.RERUN.picking(settings, Premade.BLADE, "Work", added)?.defaultLook?.name)
    }

    @Test
    fun pickingAgainInARerunReplacesTheLookItAdded() {
        val first = assertNotNull(SetupRun.RERUN.picking(settings, Premade.WAVE, "Wave", added))
        // Back to the first look, the added look now the default, and another premade picked.
        val again = assertNotNull(SetupRun.RERUN.picking(first, Premade.BLADE, "Blade", added))
        assertEquals(settings.looks.size + 1, again.looks.size, "one look added, the first pick not lingering")
        assertEquals(Premade.BLADE.look(added, "Blade"), again.defaultLook)
        assertEquals(settings.looks, again.looks.dropLast(1), "the old default still untouched")
        val same = assertNotNull(SetupRun.RERUN.picking(first, Premade.WAVE, "Wave", added))
        assertEquals("Wave", same.defaultLook.name, "its own name: not numbered \"Wave (2)\"")
        assertEquals(first, same)
    }

    @Test
    fun aRerunCantPickAtTheLookLimitButCanReplaceItsOwn() {
        assertTrue(SetupRun.RERUN.canPick(settings, added))
        assertFalse(SetupRun.RERUN.canPick(full(), added))
        assertNull(SetupRun.RERUN.picking(full(), Premade.WAVE, "Wave", added))
        val nearlyFull = GlowSettings(looks = full().looks.dropLast(1))
        val filled = assertNotNull(SetupRun.RERUN.picking(nearlyFull, Premade.WAVE, "Wave", added))
        assertTrue(SetupRun.RERUN.canPick(filled, added), "full now, but by its own look, which a pick replaces")
        assertEquals(Premade.BLADE, SetupRun.RERUN.picking(filled, Premade.BLADE, "Blade", added)?.defaultLook?.basedOn)
    }
}
