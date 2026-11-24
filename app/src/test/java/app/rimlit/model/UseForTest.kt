package app.rimlit.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Saving an edited look with where it applies: its apps exactly, and the default look always one of them. */
class UseForTest {

    private val work = Look(id = "work", name = "Work", style = GlowStyle.BLADE)
    private val settings = GlowSettings(
        looks = listOf(Look(), work),
        appRules = mapOf("a" to Rule("work"), "b" to Rule("work"), "c" to Rule(DEFAULT_LOOK_ID)),
    )
    private val full = GlowSettings(looks = listOf(Look()) + (2..MAX_LOOKS).map { Look(id = "$it", name = "Look $it") })
    private val nowhere = UseFor(isDefault = false, apps = emptySet())

    /** The settings [save] saved, failing when it was refused. */
    private fun saved(save: LookSave): GlowSettings = assertIs<LookSave.Saved>(save).settings

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
        val saved = saved(settings.savingLook(edited, UseFor(false, apps = setOf("b", "d")), isNew = false))
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
        val saved = saved(settings.savingLook(work, UseFor(isDefault = false, apps = setOf("a", "b", "c")), false))
        assertEquals(Rule("work"), saved.appRules["c"])
    }

    @Test
    fun aLookTakesTheDefaultButTheDefaultIsNeverLeftWithout() {
        val made = saved(settings.savingLook(work, UseFor(isDefault = true, apps = emptySet()), isNew = false))
        assertEquals("work", made.defaultLookId)
        val stays = saved(settings.savingLook(Look(), nowhere, isNew = false))
        assertEquals(DEFAULT_LOOK_ID, stays.defaultLookId, "one look is always the default")
    }

    @Test
    fun aNewLookIsAddedWhileItFitsAndItsNameIsFree() {
        val night = Look(id = "night", name = "Night")
        val added = saved(settings.savingLook(night, UseFor(false, apps = setOf("e")), isNew = true))
        assertEquals(night, added.look("night"))
        assertEquals(Rule("night"), added.appRules["e"])
    }

    @Test
    fun eachRefusalSaysItsOwnCause() {
        val night = Look(id = "night", name = "Night")
        assertEquals(LookSave.Refused.NAME_TAKEN, settings.savingLook(night.copy(name = "work"), nowhere, isNew = true))
        assertEquals(
            LookSave.Refused.NAME_TAKEN,
            settings.savingLook(work.copy(name = "DEFAULT"), nowhere, isNew = false),
            "renamed as another look, matched ignoring case",
        )
        assertEquals(LookSave.Refused.LOOK_LIMIT, full.savingLook(night, nowhere, isNew = true))
        assertEquals(
            LookSave.Refused.LOOK_LIMIT,
            full.savingLook(night.copy(name = "Look 2"), nowhere, isNew = true),
            "no room comes first: a new name can't help",
        )
        assertEquals(LookSave.Refused.ID_TAKEN, settings.savingLook(work, nowhere, isNew = true), "never twice")
    }

    @Test
    fun aSavedLookThatIsGoneIsNotSavedAgain() {
        val deleted = settings.copy(looks = listOf(Look()), appRules = emptyMap())
        assertEquals(LookSave.Refused.LOOK_DELETED, deleted.savingLook(work, UseFor(false, setOf("a")), isNew = false))
        val renamedOver = deleted.copy(looks = listOf(Look(), Look(id = "other", name = "Work")))
        assertEquals(
            LookSave.Refused.LOOK_DELETED,
            renamedOver.savingLook(work, nowhere, isNew = false),
            "gone comes first, even when another look has its name now",
        )
        val edited = full.looks.last().copy(thicknessDp = 9f)
        assertEquals(edited, saved(full.savingLook(edited, nowhere, isNew = false)).look(edited.id), "full")
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
        val saved = saved(settings.savingLook(nothing, nowhere, isNew = false))
        assertEquals(work.basedOn.style, saved.look("work")?.style, "no edge and no particles: the edge comes back")
    }
}
