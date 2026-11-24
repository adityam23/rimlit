package app.rimlit.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Saving an edited look with where it applies: its apps exactly, and the default look always one of them. */
class UseForTest {

    private val work = Look(id = "work", name = "Work", style = GlowStyle.BLADE)
    private val settings = GlowSettings(
        looks = listOf(Look(), work),
        appRules = mapOf("a" to Rule("work"), "b" to Rule("work"), "c" to Rule(DEFAULT_LOOK_ID)),
    )

    @Test
    fun aLookAppliesAsTheDefaultAndToItsOwnApps() {
        assertEquals(UseFor(isDefault = false, apps = setOf("a", "b")), settings.useFor("work"))
        assertEquals(UseFor(isDefault = true, apps = setOf("c")), settings.useFor(DEFAULT_LOOK_ID))
        assertEquals(2, settings.appsUsing("work"))
        assertEquals(0, settings.appsUsing("none"))
    }

    @Test
    fun savingReplacesTheLookAndItsAppsExactly() {
        val edited = work.copy(thicknessDp = 9f)
        val saved = assertNotNull(settings.savingLook(edited, UseFor(false, apps = setOf("b", "d")), isNew = false))
        assertEquals(edited, saved.look("work"))
        assertEquals(
            mapOf("b" to Rule("work"), "c" to Rule(DEFAULT_LOOK_ID), "d" to Rule("work")),
            saved.appRules,
            "a no longer has it; d takes it from no rule",
        )
        assertEquals(DEFAULT_LOOK_ID, saved.defaultLookId)
    }

    @Test
    fun anAppTickedForAnotherLookMovesToIt() {
        val saved = settings.savingLook(work, UseFor(isDefault = false, apps = setOf("a", "b", "c")), isNew = false)
        assertEquals(Rule("work"), saved?.appRules?.get("c"))
    }

    @Test
    fun aLookTakesTheDefaultButTheDefaultIsNeverLeftWithout() {
        val made = settings.savingLook(work, UseFor(isDefault = true, apps = emptySet()), isNew = false)
        assertEquals("work", made?.defaultLookId)
        val stays = settings.savingLook(Look(), UseFor(isDefault = false, apps = emptySet()), isNew = false)
        assertEquals(DEFAULT_LOOK_ID, stays?.defaultLookId, "one look is always the default")
    }

    @Test
    fun aNewLookIsAddedWhileItFitsAndItsNameIsFree() {
        val night = Look(id = "night", name = "Night")
        val added = assertNotNull(settings.savingLook(night, UseFor(false, apps = setOf("e")), isNew = true))
        assertEquals(night, added.look("night"))
        assertEquals(Rule("night"), added.appRules["e"])
        assertNull(settings.savingLook(night.copy(name = "work"), UseFor(false, emptySet()), isNew = true), "name taken")
        val full = GlowSettings(looks = listOf(Look()) + (2..MAX_LOOKS).map { Look(id = "$it", name = "Look $it") })
        assertNull(full.savingLook(night, UseFor(false, emptySet()), isNew = true), "no room")
        assertNull(settings.savingLook(work, UseFor(false, emptySet()), isNew = true), "its id is taken: never twice")
    }

    @Test
    fun aSavedLookThatIsGoneIsNotSavedAgain() {
        val deleted = settings.copy(looks = listOf(Look()), appRules = emptyMap())
        assertNull(deleted.savingLook(work, UseFor(false, setOf("a")), isNew = false), "deleted meanwhile: not re-added")
        val full = GlowSettings(looks = listOf(Look()) + (2..MAX_LOOKS).map { Look(id = "$it", name = "Look $it") })
        val edited = full.looks.last().copy(thicknessDp = 9f)
        assertEquals(edited, full.savingLook(edited, UseFor(false, emptySet()), isNew = false)?.look(edited.id), "full")
    }

    @Test
    fun aNewLooksNameIsNumberedWhenTaken() {
        assertEquals("Blade", settings.numberedName("Blade"))
        assertEquals("Work (2)", settings.numberedName("Work"))
        assertEquals("Work (2)", settings.freshName("Work"))
        assertEquals("x".repeat(30), settings.freshName("x".repeat(40)), "cut to fit first")
    }

    @Test
    fun aLookSavedAlwaysShowsSomething() {
        val nothing = work.copy(style = GlowStyle.NONE, particles = null)
        val saved = settings.savingLook(nothing, UseFor(isDefault = false, apps = emptySet()), isNew = false)
        assertEquals(work.basedOn.style, saved?.look("work")?.style, "no edge and no particles: the edge comes back")
    }
}
