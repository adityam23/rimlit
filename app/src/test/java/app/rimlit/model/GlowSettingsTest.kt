package app.rimlit.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GlowSettingsTest {

    private val app = "com.example.chat"
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val extracted = listOf(0xFF336699.toInt())

    private val work = Look(id = "work", name = "Work", style = GlowStyle.BLADE, colorMode = ColorMode.RAINBOW)
    private val settings = GlowSettings(
        looks = listOf(Look(colorMode = ColorMode.CUSTOM, customColors = listOf(blue)), work),
    )

    private fun GlowSettings.ready(packageName: String = app) =
        (resolve(packageName, extracted) as Resolution.Ready).spec

    @Test
    fun withoutARuleTheDefaultLookApplies() {
        val spec = settings.ready()
        assertEquals(GlowStyle.GLOW, spec.style)
        assertEquals(GlowPalette.Colors(listOf(blue)), spec.palette)
    }

    @Test
    fun anAppsLookRuleWinsOverTheDefaultLook() {
        val spec = settings.copy(appRules = mapOf(app to Rule.UseLook("work"))).ready()
        assertEquals(GlowStyle.BLADE, spec.style)
        assertEquals(GlowPalette.Rainbow, spec.palette)
        assertEquals(GlowStyle.GLOW, settings.ready("com.example.other").style, "other apps keep the default")
    }

    @Test
    fun aRuleNamingAMissingLookFallsBackToTheDefaultLook() {
        val spec = settings.copy(appRules = mapOf(app to Rule.UseLook("gone"))).ready()
        assertEquals(settings.ready(), spec)
    }

    @Test
    fun aPaletteRulePaintsTheDefaultLook() {
        val spec = settings.copy(appRules = mapOf(app to Rule.UsePalette(listOf(red)))).ready()
        assertEquals(GlowStyle.GLOW, spec.style)
        assertEquals(GlowPalette.Colors(listOf(red)), spec.palette)
    }

    @Test
    fun aPaletteRuleOverridesAutoColours() {
        val auto = GlowSettings(appRules = mapOf(app to Rule.UsePalette(listOf(red))))
        assertEquals(GlowPalette.Colors(listOf(red)), (auto.resolve(app) as Resolution.Ready).spec.palette)
    }

    @Test
    fun autoColoursAreExtractedByTheCallerAsTheResolvedLookSays() {
        val image = AutoColors(AutoColorSource.NOTIFICATION_IMAGE, count = 2)
        val auto = settings.copy(
            looks = settings.looks + Look(id = "photo", name = "Photo", autoColors = image),
            appRules = mapOf(app to Rule.UseLook("photo")),
        )
        assertEquals(Resolution.Extract(image), auto.resolve(app))
        assertEquals(GlowPalette.Colors(extracted), auto.ready().palette)
    }

    @Test
    fun theGlobalPartsComeFromTheSettings() {
        val spec = settings.copy(durationMs = 7_000L, cornerRadiusDp = 12f, screenOffBrightness = 0.4f).ready()
        assertEquals(7_000L, spec.durationMs)
        assertEquals(12f, spec.cornerRadiusDp)
        assertEquals(0.4f, spec.screenOffBrightness)
    }

    @Test
    fun previewsShowAsManyAutoColoursAsChosen() {
        for (count in GlowSettings.Ranges.autoColorCount) {
            val settings = GlowSettings(looks = listOf(Look(autoColors = AutoColors(count = count))))
            val palette = settings.previewSpec().palette as GlowPalette.Colors
            assertEquals(count, palette.argb.size)
        }
    }

    @Test
    fun glowsHaveNoParticlesUntilALookAsksForThem() {
        assertNull(GlowSettings().previewSpec().particles)
        assertNull(settings.ready().particles)
    }

    @Test
    fun previewsShowTheLookBeingEdited() {
        assertEquals(GlowStyle.GLOW, settings.previewSpec().style)
        assertEquals(GlowStyle.BLADE, settings.previewSpec(work).style)
    }

    @Test
    fun theSpecMatchesTheDisplayCornersUntilTheUserSetsARadius() {
        assertNull(GlowSettings().previewSpec().cornerRadiusDp)
        assertEquals(40f, GlowSettings(cornerRadiusDp = 40f).previewSpec().cornerRadiusDp)
    }

    @Test
    fun theSpecMatchesTheCameraCutoutUntilTheUserDrawsOne() {
        assertNull(GlowSettings().previewSpec().customCutout)
        val notch = CustomCutout(CutoutShape.NOTCH, widthDp = 40f)
        assertEquals(notch, GlowSettings(customCutout = notch).previewSpec().customCutout)
    }

    @Test
    fun theLooksAreValid() {
        assertFailsWith<IllegalArgumentException>("no looks") { GlowSettings(looks = emptyList()) }
        assertFailsWith<IllegalArgumentException>("no default") { GlowSettings(looks = listOf(work)) }
        assertFailsWith<IllegalArgumentException>("same id") { settings.copy(looks = settings.looks + work) }
        assertFailsWith<IllegalArgumentException>("same name, other case") {
            settings.copy(looks = settings.looks + Look(id = "x", name = "WORK"))
        }
        assertFailsWith<IllegalArgumentException>("too many") {
            GlowSettings(looks = listOf(Look()) + (1..MAX_LOOKS).map { Look(id = "$it", name = "Look $it") })
        }
        for (name in listOf("", " ", " Work", "x".repeat(31))) {
            assertFailsWith<IllegalArgumentException>("name '$name'") { Look(name = name) }
        }
        Look(name = "x".repeat(30))
    }
}
