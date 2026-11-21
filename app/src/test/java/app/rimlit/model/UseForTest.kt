package app.rimlit.model

import org.junit.Test
import kotlin.test.assertEquals

/** Saving an edited look with where it applies: its apps exactly, and the default look always one of them. */
class UseForTest {

    private val work = Look(id = "work", name = "Work", style = GlowStyle.BLADE)
    private val settings = GlowSettings(
        looks = listOf(Look(), work),
        appRules = mapOf("a" to Rule.UseLook("work"), "b" to Rule.UseLook("work"), "c" to Rule.UseLook(DEFAULT_LOOK_ID)),
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
        val saved = settings.savingLook(edited, UseFor(isDefault = false, apps = setOf("b", "d")))
        assertEquals(edited, saved.look("work"))
        assertEquals(
            mapOf("b" to Rule.UseLook("work"), "c" to Rule.UseLook(DEFAULT_LOOK_ID), "d" to Rule.UseLook("work")),
            saved.appRules,
            "a no longer has it; d takes it from no rule",
        )
        assertEquals(DEFAULT_LOOK_ID, saved.defaultLookId)
    }

    @Test
    fun anAppTickedForAnotherLookMovesToIt() {
        val saved = settings.savingLook(work, UseFor(isDefault = false, apps = setOf("a", "b", "c")))
        assertEquals(Rule.UseLook("work"), saved.appRules["c"])
    }

    @Test
    fun aLookTakesTheDefaultButTheDefaultIsNeverLeftWithout() {
        assertEquals("work", settings.savingLook(work, UseFor(isDefault = true, apps = emptySet())).defaultLookId)
        val stays = settings.savingLook(Look(), UseFor(isDefault = false, apps = emptySet()))
        assertEquals(DEFAULT_LOOK_ID, stays.defaultLookId, "one look is always the default")
    }

    @Test
    fun aNewLookIsAddedWhileItFitsAndItsNameIsFree() {
        val night = Look(id = "night", name = "Night")
        val added = settings.savingLook(night, UseFor(isDefault = false, apps = setOf("e")))
        assertEquals(night, added.look("night"))
        assertEquals(Rule.UseLook("night"), added.appRules["e"])
        assertEquals(settings, settings.savingLook(night.copy(name = "work"), UseFor(false, emptySet())), "name taken")
        val full = GlowSettings(looks = listOf(Look()) + (2..MAX_LOOKS).map { Look(id = "$it", name = "Look $it") })
        assertEquals(full, full.savingLook(night, UseFor(false, emptySet())), "no room")
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
        val saved = settings.savingLook(nothing, UseFor(isDefault = false, apps = emptySet()))
        assertEquals(work.basedOn.style, saved.look("work")?.style, "no edge and no particles: the edge comes back")
    }
}
