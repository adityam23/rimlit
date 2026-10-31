package app.rimlit.data

import app.rimlit.model.AutoColorSource
import app.rimlit.model.AutoColors
import app.rimlit.model.ColorMode
import app.rimlit.model.EdgeRegion
import app.rimlit.model.GlowPalette
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.GlowStyle
import app.rimlit.model.Look
import app.rimlit.model.MAX_LOOKS
import app.rimlit.model.MAX_PALETTE_COLORS
import app.rimlit.model.ParticleImage
import app.rimlit.model.ParticleMotion
import app.rimlit.model.ParticlePreset
import app.rimlit.model.ParticleSpec
import app.rimlit.model.ParticleTint
import app.rimlit.model.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsJsonTest {

    private val red = 0xFFFF0000.toInt()
    private val looks = listOf(
        Look(),
        Look(
            id = "work",
            name = "Work",
            style = GlowStyle.BLADE,
            colorMode = ColorMode.CUSTOM,
            autoColors = AutoColors(AutoColorSource.NOTIFICATION_IMAGE, count = 3),
            customColors = GlowPalette.Colors(listOf(red, 0x8000FF00.toInt(), 0)),
            region = EdgeRegion.CUTOUT_AND_EDGE,
            thicknessDp = 7.5f,
            glowWidthDp = 0.1f,
            speed = 2.25f,
            brightness = 0.35f,
            particles = ParticleSpec(
                image = ParticleImage.CONFETTI,
                motion = ParticleMotion.POP,
                sizeDp = 30.5f,
                speed = 0.75f,
                amount = 0.9f,
                rotationDegPerSecond = 120f,
                tint = ParticleTint.IMAGE,
            ),
        ),
        Look(id = "snow", name = "Snow", particles = ParticlePreset.SNOW.spec),
    )

    /** One look with [fields] after its id and name, decoded. */
    private fun decodeOne(fields: String) = decodeLooks("""[{"id":"x","name":"X"$fields}]""")!!.single()

    /** The particles of one look whose particles object holds [fields] after a valid image and motion. */
    private fun decodeParticles(fields: String) =
        decodeOne(""","particles":{"image":"STAR","motion":"DRIFT"$fields}""").particles

    @Test
    fun looksRoundTrip() {
        assertEquals(looks, decodeLooks(encodeLooks(looks)))
    }

    @Test
    fun rulesRoundTrip() {
        val rules = mapOf("com.a" to Rule.UseLook("work"), "com.b" to Rule.UsePalette(GlowPalette.Colors(listOf(red, 0))))
        assertEquals(rules, decodeRules(encodeRules(rules)))
        assertEquals(emptyMap(), decodeRules(encodeRules(emptyMap())))
    }

    @Test
    fun malformedJsonDecodesAsNothing() {
        for (json in listOf(null, "", "not json", "{", "{}", "[]", "[1, \"a\", null]", "[{\"id\":\"x\"}]")) {
            assertNull(decodeLooks(json), "looks: $json")
        }
        for (json in listOf(null, "", "[]", "{\"a\":", "nope")) {
            assertNull(decodeRules(json), "rules: $json")
        }
    }

    @Test
    fun unknownValuesFallBackToTheDefaults() {
        val defaults = Look()
        val look = decodeOne(""","style":"SPARKLE","colorMode":7,"region":null,"autoColorSource":"CAMERA"""")
        assertEquals(defaults.style, look.style)
        assertEquals(defaults.colorMode, look.colorMode)
        assertEquals(defaults.region, look.region)
        assertEquals(defaults.autoColors.source, look.autoColors.source)

        val missing = decodeLooks("""[{"id":"x","name":"X"}]""")!!.single()
        assertEquals(Look(id = "x", name = "X"), missing, "missing fields take the defaults")
        assertNull(missing.particles, "looks saved before particles have none")
    }

    @Test
    fun particlesDecodeTolerantly() {
        assertEquals(ParticleSpec(ParticleImage.STAR, ParticleMotion.DRIFT), decodeParticles(""), "missing: defaults")
        assertEquals(ParticleTint.PALETTE, decodeParticles(""","tint":"GLITTER"""")?.tint, "unknown tint: default")

        val clamped = decodeParticles(""","sizeDp":1,"speed":99,"amount":-1,"rotationDegPerSecond":1e999""")
        assertEquals(
            ParticleSpec(
                image = ParticleImage.STAR,
                motion = ParticleMotion.DRIFT,
                sizeDp = Ranges.particleSizeDp.start,
                speed = Ranges.particleSpeed.endInclusive,
                amount = Ranges.particleAmount.start,
            ),
            clamped,
            "clamped to the ranges; not finite takes the default",
        )

        for (particles in listOf("null", "7", "\"SNOW\"", "[]", """{"motion":"FALL"}""", """{"image":"STAR"}""")) {
            assertNull(decodeOne(""","particles":$particles""").particles, "malformed: $particles")
        }
        val unknown = listOf("""{"image":"KITE","motion":"FALL"}""", """{"image":"STAR","motion":"ORBIT"}""")
        for (particles in unknown) {
            assertNull(decodeOne(""","particles":$particles""").particles, "unknown picture or motion: $particles")
        }
    }

    @Test
    fun numbersAreClamped() {
        val look = decodeOne(""","thicknessDp":99,"glowWidthDp":-4,"speed":"2","brightness":1e999,"autoColorCount":9""")
        assertEquals(Ranges.thicknessDp.endInclusive, look.thicknessDp)
        assertEquals(Ranges.glowWidthDp.start, look.glowWidthDp)
        assertEquals(Look().speed, look.speed, "not a number")
        assertEquals(Look().brightness, look.brightness, "not finite")
        assertEquals(Ranges.autoColorCount.last, look.autoColors.count)
    }

    @Test
    fun palettesKeepOnlyValidColoursUpToTheLimit() {
        val many = (1..8).joinToString(",") { "\"ff00000$it\"" }
        assertEquals(MAX_PALETTE_COLORS, decodeOne(""","customColors":[$many]""").customColors.argb.size)

        val mixed = decodeOne(""","customColors":["zz", 5, "1ffffffff", "-1", "ff0000ff"]""")
        assertEquals(listOf(0xFF0000FF.toInt()), mixed.customColors.argb)

        assertEquals(Look().customColors, decodeOne(""","customColors":["nope"]""").customColors)

        val rules = decodeRules("""{"a":{"palette":[$many]},"b":{"palette":["x"]},"c":{"look":""},"":{"look":"y"}}""")
        assertEquals(mapOf("a" to Rule.UsePalette(GlowPalette.Colors((1..5).map { 0xFF000000.toInt() + it }))), rules)
    }

    @Test
    fun invalidAndRepeatedLooksAreSkipped() {
        val json = listOf(
            """{"name":"No id"}""",
            """{"id":"blank","name":"   "}""",
            """{"id":"long","name":"${"x".repeat(31)}"}""",
            """{"id":"a","name":" Padded "}""",
            """{"id":"a","name":"Same id"}""",
            """{"id":"b","name":"PADDED"}""",
            """{"id":"c","name":"Other"}""",
        ).joinToString(",", "[", "]")
        assertEquals(listOf("a" to "Padded", "c" to "Other"), decodeLooks(json)!!.map { it.id to it.name })
    }

    @Test
    fun atMostTheLimitOfLooksDecodes() {
        val json = (0..MAX_LOOKS).joinToString(",", "[", "]") { """{"id":"$it","name":"Look $it"}""" }
        assertEquals(MAX_LOOKS, decodeLooks(json)!!.size)
    }
}
