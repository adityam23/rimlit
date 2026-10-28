package app.rimlit.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The look library's edits on [GlowSettings]: naming, adding, renaming, deleting, and app rules. */
class LookEditsTest {

    private val app = "com.example.chat"
    private val red = 0xFFFF0000.toInt()
    private val redPalette = GlowPalette.Colors(listOf(red))

    private val work = Look(id = "work", name = "Work", style = GlowStyle.BLADE, colorMode = ColorMode.RAINBOW)
    private val settings = GlowSettings(looks = listOf(Look(), work))

    private fun full() = GlowSettings(looks = listOf(Look()) + (2..MAX_LOOKS).map { Look(id = "$it", name = "Look $it") })

    @Test
    fun namesAreValidAndUniqueRegardlessOfCase() {
        assertTrue(settings.canName("Night"))
        assertFalse(settings.canName("work"), "taken, other case")
        assertFalse(settings.canName("DEFAULT"), "taken, other case")
        assertTrue(settings.canName("WORK", exceptId = "work"), "a look may change its own name's case")
        for (name in listOf("", " ", " Night", "Night ", "x".repeat(31))) {
            assertFalse(settings.canName(name), "name '$name'")
        }
        assertTrue(settings.canName("x".repeat(30)))
    }

    @Test
    fun aNameNeverHoldsABrokenEmoji() {
        val sun = "☀️"
        val rocket = "🚀"
        assertTrue(isValidLookName("Launch $rocket$sun"))
        assertFalse(isValidLookName("Launch \uD83D"), "lone high surrogate")
        assertFalse(isValidLookName("\uDE80 Launch"), "lone low surrogate")
        assertFalse(isValidLookName("a\uDE80\uD83Db"), "surrogates in the wrong order")
    }

    @Test
    fun namesAreCutToFitOnACodePointBoundary() {
        val rocket = "🚀"
        assertEquals("Work copy", fitLookName("Work copy"))
        assertEquals("x".repeat(30), fitLookName("x".repeat(31)))
        // The rocket's pair spans units 30 and 31: it is left out whole rather than split.
        assertEquals("x".repeat(29), fitLookName("x".repeat(29) + rocket + " copy"))
        assertEquals("x".repeat(28) + rocket, fitLookName("x".repeat(28) + rocket + " copy"))
        assertEquals("x".repeat(27), fitLookName("x".repeat(27) + "   copy"), "trimmed after the cut")
        for (text in listOf("x".repeat(29) + rocket, "x".repeat(28) + rocket + "y")) {
            assertTrue(isValidLookName(fitLookName(text + " copy")), text)
        }
    }

    @Test
    fun aNewLookCopiesTheLookItStartsFrom() {
        val added = settings.addLook(work, "Night", id = "night")
        assertEquals(work.copy(id = "night", name = "Night"), added.look("night"))
        assertEquals(settings.looks, added.looks.dropLast(1), "the others are unchanged")
        assertEquals(settings.defaultLookId, added.defaultLookId)
    }

    @Test
    fun aLookIsNotAddedPastTheLimitOrUnderAnUnusableNameOrId() {
        assertFalse(full().canAddLook)
        assertEquals(full(), full().addLook(work, "Night", id = "night"), "full")
        assertEquals(settings, settings.addLook(work, "work", id = "night"), "name taken")
        assertEquals(settings, settings.addLook(work, " Night", id = "night"), "name untrimmed")
        assertEquals(settings, settings.addLook(Look(), "Night", id = "work"), "id taken")
    }

    @Test
    fun aLookIsRenamedOnlyToAUsableName() {
        assertEquals("Office", settings.renameLook("work", "Office").look("work")?.name)
        assertEquals("WORK", settings.renameLook("work", "WORK").look("work")?.name)
        assertEquals(settings, settings.renameLook("work", "Default"))
        assertEquals(settings, settings.renameLook("work", ""))
    }

    @Test
    fun deletingALookPrunesTheRulesThatUseItOnly() {
        val ruled = settings
            .withAppRule(app, Rule.UseLook("work"))
            .withAppRule("com.example.mail", Rule.UsePalette(redPalette))
        val deleted = ruled.deleteLook("work")
        assertNull(deleted.look("work"))
        assertEquals(mapOf("com.example.mail" to Rule.UsePalette(redPalette)), deleted.appRules)
        assertEquals(GlowStyle.GLOW, (deleted.resolve(app, listOf(red)) as Resolution.Ready).spec.style)
    }

    @Test
    fun theDefaultLookCannotBeDeleted() {
        val ruled = settings.withAppRule(app, Rule.UseLook("work"))
        assertEquals(ruled, ruled.deleteLook(DEFAULT_LOOK_ID))
    }

    @Test
    fun anAppRuleIsSetReplacedAndCleared() {
        val looked = settings.withAppRule(app, Rule.UseLook("work"))
        assertEquals(Rule.UseLook("work"), looked.appRules[app])
        val painted = looked.withAppRule(app, Rule.UsePalette(redPalette))
        assertEquals(Rule.UsePalette(redPalette), painted.appRules[app])
        assertEquals(emptyMap(), painted.withAppRule(app, null).appRules)
    }

    @Test
    fun theFirstFreeNameIsSuggested() {
        val numbered = generateSequence(1) { it + 1 }.map { "Look $it" }
        assertEquals("Work copy", settings.firstFreeName(sequenceOf("Work copy") + numbered))
        assertEquals("Look 1", settings.firstFreeName(sequenceOf("work") + numbered), "skips a taken name")
        val nine = GlowSettings(looks = listOf(Look()) + (1..MAX_LOOKS - 2).map { Look(id = "$it", name = "Look $it") })
        assertEquals("Look ${MAX_LOOKS - 1}", nine.firstFreeName(numbered))
        assertNull(settings.firstFreeName(sequenceOf("Work", "")))
    }
}
