package app.rimlit.data

import app.rimlit.model.ColorMode
import app.rimlit.model.GlowPalette
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowStyle
import app.rimlit.model.Look
import app.rimlit.model.MAX_LOOKS
import app.rimlit.model.ParticlePreset
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Older per-app palettes become looks: one per app while they fit, then shared by palette, then the default. */
class PaletteLooksTest {

    private val red = GlowPalette.Colors(listOf(0xFFFF0000.toInt()))
    private val blue = GlowPalette.Colors(listOf(0xFF0000FF.toInt()))
    private val green = GlowPalette.Colors(listOf(0xFF00FF00.toInt()))

    /** Settings holding the default look and enough others to leave [room] free. */
    private fun withRoom(room: Int) =
        GlowSettings(looks = listOf(Look()) + (1 until MAX_LOOKS - room).map { Look(id = "l$it", name = "Look $it") })

    /** Packages `p0`, `p1`… labelled `App 00`, `App 01`… so they sort by number. */
    private val names = FakePaletteLookNames((0..30).associate { "p$it" to "App %02d".format(it) })

    private fun GlowSettings.lookNameOf(packageName: String) = appLook(packageName)?.name

    @Test
    fun eachAppGetsALookWhenTheyFit() {
        val migrated = withRoom(2).withPaletteLooks(mapOf("p0" to red, "p1" to red), names)
        assertEquals("App 00 colors", migrated.lookNameOf("p0"))
        assertEquals("App 01 colors", migrated.lookNameOf("p1"), "same colours, still its own look")
        val look = migrated.look("colours:p0")!!
        assertEquals(ColorMode.CUSTOM, look.colorMode)
        assertEquals(red, look.customColors)
    }

    @Test
    fun pastTheLimitTheAppsLastByNameShareOneLookPerPalette() {
        // Five apps, three free looks: the first two get their own, and the rest share one per palette they need.
        val palettes = mapOf("p0" to red, "p1" to blue, "p2" to blue, "p3" to green, "p4" to green)
        val migrated = withRoom(3).withPaletteLooks(palettes, names)
        assertEquals(MAX_LOOKS, migrated.looks.size)
        assertEquals("App 00 colors", migrated.lookNameOf("p0"))
        assertEquals("App 01 colors", migrated.lookNameOf("p1"))
        assertEquals("App 01 colors", migrated.lookNameOf("p2"), "joins the own look already in its colours")
        assertEquals("App 03 + 1 colors", migrated.lookNameOf("p3"))
        assertEquals("App 03 + 1 colors", migrated.lookNameOf("p4"))
    }

    @Test
    fun appsThatCantEvenShareKeepTheDefaultLook() {
        val migrated = withRoom(1).withPaletteLooks(mapOf("p0" to red, "p1" to blue, "p2" to blue), names)
        assertEquals("App 00 colors", migrated.lookNameOf("p0"))
        assertNull(migrated.appRules["p1"])
        assertNull(migrated.appRules["p2"])
        assertEquals(MAX_LOOKS, migrated.looks.size)
    }

    @Test
    fun aTakenNameIsNumbered() {
        val settings = GlowSettings(looks = listOf(Look(), Look(id = "x", name = "App 00 colors")))
        assertEquals("App 00 colors (2)", settings.withPaletteLooks(mapOf("p0" to red), names).lookNameOf("p0"))
    }

    @Test
    fun appsSortByNameRegardlessOfCaseThenByPackage() {
        val names = FakePaletteLookNames(mapOf("b" to "zed", "a" to "Zed", "c" to "apple"))
        val migrated = withRoom(3).withPaletteLooks(mapOf("a" to red, "b" to blue, "c" to green), names)
        // Added in that order, so the second "zed" (names clash regardless of case) is the one numbered.
        assertEquals(listOf("apple colors", "Zed colors", "zed colors (2)"), migrated.looks.drop(MAX_LOOKS - 3).map { it.name })
        assertEquals("zed colors (2)", migrated.lookNameOf("b"))
    }

    @Test
    fun anAppWhoseLookIdIsTakenKeepsTheDefaultLook() {
        val settings = GlowSettings(looks = listOf(Look(), Look(id = "colours:p0", name = "Mine")))
        val migrated = settings.withPaletteLooks(mapOf("p0" to red), names)
        assertNull(migrated.appRules["p0"])
        assertEquals("Mine", migrated.look("colours:p0")?.name, "the user's look is untouched")
    }

    @Test
    fun aLookCopiesTheDefaultLookParticlesAndAll() {
        val hearts = Look(style = GlowStyle.BLADE, particles = ParticlePreset.HEARTS.spec)
        val migrated = GlowSettings(looks = listOf(hearts)).withPaletteLooks(mapOf("p0" to red), names)
        assertEquals(
            hearts.copy(id = "colours:p0", name = "App 00 colors", colorMode = ColorMode.CUSTOM, customColors = red),
            migrated.appLook("p0"),
        )
    }

    @Test
    fun aLabelThatCantNameALookNamesItByThePackageName() {
        val names = FakePaletteLookNames(mapOf("p0" to "X\uD800", "p1" to "Fine", "p2" to "\uDC00Y"))
        val migrated = withRoom(2).withPaletteLooks(mapOf("p0" to red, "p1" to blue, "p2" to blue), names)
        assertEquals("p0 colors", migrated.lookNameOf("p0"), "an unpaired surrogate: its colours kept all the same")
        assertEquals("Fine colors", migrated.lookNameOf("p1"))
        assertEquals("Fine colors", migrated.lookNameOf("p2"))
    }

    @Test
    fun aSharedLookWhoseFirstLabelCantNameItIsNamedByItsPackageName() {
        // As in the test past the limit: the two green apps last by name share a look.
        val names = FakePaletteLookNames(names.labels + mapOf("q" to "X\uD800", "r" to "\uDC00Y"))
        val palettes = mapOf("p0" to red, "p1" to blue, "p2" to blue, "q" to green, "r" to green)
        val migrated = withRoom(3).withPaletteLooks(palettes, names)
        assertEquals("q + 1 colors", migrated.lookNameOf("q"))
        assertEquals("q + 1 colors", migrated.lookNameOf("r"))
    }

    @Test
    fun theLabelIsCutSoTheRestOfTheNameFits() {
        val long = "x".repeat(40)
        assertEquals("x".repeat(23) + " colors", names.fittedLookName(long, others = 0))
        assertEquals("x".repeat(19) + " + 2 colors", names.fittedLookName(long, others = 2))
        assertEquals("Short colors", names.fittedLookName("Short", others = 0))
        // The rocket's pair would end at the cut: it is left out whole rather than split.
        val rocket = "🚀"
        assertEquals("x".repeat(22) + " colors", names.fittedLookName("x".repeat(22) + rocket, others = 0))
        assertEquals("x".repeat(21) + rocket + " colors", names.fittedLookName("x".repeat(21) + rocket, others = 0))
        assertNull(names.fittedLookName("X\uD800", others = 0), "never a broken name")
    }

    @Test
    fun aTranslationTooLongForAnyLabelIsCutWhole() {
        val wordy = object : PaletteLookNames {
            override fun label(packageName: String) = packageName

            override fun lookName(label: String, others: Int) = "$label " + "y".repeat(40)
        }
        assertEquals("A " + "y".repeat(28), wordy.fittedLookName("Alpha", others = 0), "a character of the label at least")
    }

    @Test
    fun nothingToMigrateChangesNothing() {
        val settings = withRoom(0)
        assertEquals(settings, settings.withPaletteLooks(emptyMap(), names))
    }
}
