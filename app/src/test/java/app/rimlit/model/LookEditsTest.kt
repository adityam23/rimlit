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
            .withAppRule(app, Rule("work"))
            .withAppRule("com.example.mail", Rule(DEFAULT_LOOK_ID))
        val deleted = ruled.deleteLook("work")
        assertNull(deleted.look("work"))
        assertEquals(mapOf("com.example.mail" to Rule(DEFAULT_LOOK_ID)), deleted.appRules)
        assertEquals(GlowStyle.GLOW, (deleted.resolve(app, listOf(red)) as Resolution.Ready).spec.style)
    }

    @Test
    fun theDefaultLookCannotBeDeleted() {
        val ruled = settings.withAppRule(app, Rule("work"))
        assertEquals(ruled, ruled.deleteLook(DEFAULT_LOOK_ID))
    }

    @Test
    fun anAppRuleIsSetReplacedAndCleared() {
        val looked = settings.withAppRule(app, Rule("work"))
        assertEquals(Rule("work"), looked.appRules[app])
        val replaced = looked.withAppRule(app, Rule(DEFAULT_LOOK_ID))
        assertEquals(Rule(DEFAULT_LOOK_ID), replaced.appRules[app])
        assertEquals(emptyMap(), replaced.withAppRule(app, null).appRules)
    }

    @Test
    fun anAppGivenALookGlowsWithItAndTheDefaultLookNeedsNoRule() {
        assertEquals(DEFAULT_LOOK_ID, settings.lookFor(app).id, "no rule: the default look")
        val looked = settings.withAppLook(app, "work")
        assertEquals(Rule("work"), looked.appRules[app])
        assertEquals("work", looked.lookFor(app).id)
        val back = looked.withAppLook(app, DEFAULT_LOOK_ID)
        assertEquals(emptyMap(), back.appRules, "back to the default look is no rule at all")
        assertEquals(DEFAULT_LOOK_ID, settings.withAppRule(app, Rule("gone")).lookFor(app).id)
    }

    @Test
    fun aRuleNamingAMissingLookAppliesAsTheDefaultLook() {
        val ruled = settings
            .withAppRule(app, Rule("work"))
            .withAppRule("com.example.mail", Rule(DEFAULT_LOOK_ID))
            .withAppRule("com.example.old", Rule("gone"))
        assertEquals(work, ruled.appLook(app))
        assertEquals(Look(), ruled.appLook("com.example.mail"))
        assertNull(ruled.appLook("com.example.old"), "names a missing look")
        assertNull(ruled.appLook("com.example.none"), "no rule")
        assertEquals(
            ruled.resolve("com.example.none", listOf(red)),
            ruled.resolve("com.example.old", listOf(red)),
            "the resolver applies it as appLook says",
        )
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

    @Test
    fun importedLooksAreAddedUnderNewIdsWithClashingNamesNumbered() {
        val ruled = settings.withAppRule(app, Rule("work"))
        val night = Look(id = "work", name = "Night", style = GlowStyle.BASIC)
        val ids = generateSequence(1) { it + 1 }.map { "new$it" }.iterator()
        val imported = ruled.addImportedLooks(listOf(work, Look(), night)) { ids.next() }
        assertEquals(
            listOf("Default", "Work", "Work (2)", "Default (2)", "Night"),
            imported.looks.map { it.name },
        )
        assertEquals(listOf("default", "work", "new1", "new2", "new3"), imported.looks.map { it.id })
        assertEquals(work.copy(id = "new1", name = "Work (2)"), imported.look("new1"))
        assertEquals(ruled.appRules, imported.appRules, "rules untouched")
        assertEquals(ruled.defaultLookId, imported.defaultLookId, "default look untouched")
        // Numbered again past a taken number, and the number always fits a long name.
        assertEquals("Work (3)", imported.addImportedLooks(listOf(work)).looks.last().name)
        val long = Look(id = "long", name = "x".repeat(30))
        val twice = settings.addImportedLooks(listOf(long, long))
        assertEquals(listOf("x".repeat(30), "x".repeat(26) + " (2)"), twice.looks.drop(2).map { it.name })
    }

    @Test
    fun aNumberedLookIsAddedElseNothingIs() {
        assertEquals(work.copy(id = "new", name = "Work (2)"), settings.addLookNumbered(work, "Work", "new")?.look("new"))
        assertNull(full().addLookNumbered(work, "Night", "new"), "full")
        assertNull(settings.addLookNumbered(work, "Night", "work"), "id taken")
    }

    @Test
    fun importedLooksStopAtTheLookLimit() {
        val looks = (1..MAX_LOOKS).map { Look(id = "i$it", name = "Imported $it") }
        val imported = settings.addImportedLooks(looks)
        assertEquals(MAX_LOOKS, imported.looks.size)
        assertEquals((1..MAX_LOOKS - 2).map { "Imported $it" }, imported.looks.drop(2).map { it.name })
        assertEquals(full(), full().addImportedLooks(looks), "no room: unchanged")
    }
}
