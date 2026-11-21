package app.rimlit.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A look is custom exactly where it differs from its premade, and each part resets on its own. */
class PremadeTest {

    private val red = GlowPalette.Colors(listOf(0xFFFF0000.toInt()))
    private val blade = Premade.BLADE.look(id = "a", name = "A")

    @Test
    fun aPremadeUntouchedIsNotCustom() {
        for (premade in Premade.entries) {
            val look = premade.look(id = "a", name = "A")
            assertEquals(emptySet(), look.changedParts(), "$premade")
            assertEquals(premade.style, look.style)
        }
        assertFalse(Look().isCustom, "the first look is Glow untouched")
    }

    @Test
    fun noEdgeIsNotAPremade() {
        assertTrue(Premade.entries.none { it.style == GlowStyle.NONE })
        assertEquals(Premade.BASIC, Premade.of(GlowStyle.NONE))
        for (premade in Premade.entries) assertEquals(premade, Premade.of(premade.style))
    }

    @Test
    fun eachChangeMarksOnlyItsPart() {
        val changes = listOf(
            LookPart.BASE to blade.copy(style = GlowStyle.FIREWORKS),
            LookPart.BASE to blade.copy(region = EdgeRegion.CUTOUT),
            LookPart.COLOUR to blade.copy(colorMode = ColorMode.RAINBOW),
            LookPart.PARTICLES to blade.copy(particles = ParticlePreset.SNOW.spec),
            LookPart.TUNING to blade.copy(thicknessDp = 6f),
            LookPart.TUNING to blade.copy(glowWidthDp = 30f),
            LookPart.TUNING to blade.copy(speed = 2f),
            LookPart.TUNING to blade.copy(brightness = 0.5f),
        )
        for ((part, look) in changes) assertEquals(setOf(part), look.changedParts(), "$look")
        assertEquals(setOf(LookPart.BASE), blade.copy(style = GlowStyle.FIREWORKS, region = EdgeRegion.CUTOUT).changedParts())
    }

    @Test
    fun onlyColoursThatShowMakeALookCustom() {
        val keptPalette = blade.copy(customColors = red)
        assertFalse(keptPalette.isCustom, "a palette kept while on auto colours")
        assertEquals(setOf(LookPart.COLOUR), keptPalette.copy(colorMode = ColorMode.CUSTOM).changedParts())
        val rainbow = blade.copy(colorMode = ColorMode.RAINBOW)
        assertEquals(rainbow.changedParts(), rainbow.copy(autoColors = AutoColors(count = 3)).changedParts())
        assertEquals(setOf(LookPart.COLOUR), blade.copy(autoColors = AutoColors(count = 3)).changedParts())
    }

    @Test
    fun aPartResetsToItsPremadeAlone() {
        val custom = blade.copy(
            style = GlowStyle.NONE,
            region = EdgeRegion.CUTOUT,
            colorMode = ColorMode.CUSTOM,
            customColors = red,
            particles = ParticlePreset.HEARTS.spec,
            thicknessDp = 8f,
            glowWidthDp = 10f,
            speed = 2f,
            brightness = 0.4f,
        )
        assertEquals(LookPart.entries.toSet(), custom.changedParts())
        for (part in LookPart.entries) {
            assertEquals(LookPart.entries.toSet() - part, custom.reset(part).changedParts(), "$part")
        }
        assertEquals(blade, LookPart.entries.fold(custom) { look, part -> look.reset(part) })
    }

    @Test
    fun switchingPremadeKeepsTheIdAndNameOnly() {
        val custom = blade.copy(id = "mine", name = "Mine", thicknessDp = 9f, particles = ParticlePreset.RAIN.spec)
        val switched = custom.switchedTo(Premade.WAVE)
        assertEquals(Premade.WAVE.look(id = "mine", name = "Mine"), switched)
        assertFalse(switched.isCustom)
    }
}
