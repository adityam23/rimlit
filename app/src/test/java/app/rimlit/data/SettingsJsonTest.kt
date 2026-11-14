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
import app.rimlit.model.MAX_PARTICLE_IMAGES
import app.rimlit.model.ParticleImage
import app.rimlit.model.ParticleMotion
import app.rimlit.model.ParticlePicture
import app.rimlit.model.ParticlePreset
import app.rimlit.model.ParticleSpec
import app.rimlit.model.ParticleTint
import app.rimlit.model.Rule
import app.rimlit.model.newParticleImageId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsJsonTest {

    private val red = 0xFFFF0000.toInt()
    private val star = ParticlePicture.Builtin(ParticleImage.STAR)
    private val imageA = "0b8e1c5e-3f4a-4c2d-9a71-5d2e8f6b1a90"
    private val imageB = "f2c4a6e8-1b3d-4f5a-8c7e-9d0b2a4c6e81"
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
                picture = ParticlePicture.Builtin(ParticleImage.CONFETTI),
                motion = ParticleMotion.POP,
                sizeDp = 30.5f,
                speed = 0.75f,
                amount = 0.9f,
                rotationDegPerSecond = 120f,
                tint = ParticleTint.IMAGE,
            ),
        ),
        // Particles alone, with no edge.
        Look(id = "snow", name = "Snow", style = GlowStyle.NONE, particles = ParticlePreset.SNOW.spec),
        Look(
            id = "mine",
            name = "Mine",
            particles = ParticleSpec(ParticlePicture.Custom(listOf(imageA, imageB)), ParticleMotion.RISE),
        ),
        Look(id = "icon", name = "Icon", particles = ParticleSpec(ParticlePicture.AppIcon, ParticleMotion.DRIFT)),
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
        assertEquals(ParticleSpec(star, ParticleMotion.DRIFT), decodeParticles(""), "missing: defaults")
        assertEquals(ParticleTint.PALETTE, decodeParticles(""","tint":"GLITTER"""")?.tint, "unknown tint: default")

        val clamped = decodeParticles(""","sizeDp":1,"speed":99,"amount":-1,"rotationDegPerSecond":1e999""")
        assertEquals(
            ParticleSpec(
                picture = star,
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
    fun builtInImagesSavedBeforeCustomPicturesReadAsTheyWere() {
        // Exactly how particles-in-looks saved its particles: the image by name.
        val saved = """{"image":"HEART","motion":"RISE","sizeDp":20,"speed":0.8,"amount":0.3,"tint":"IMAGE"}"""
        assertEquals(ParticlePreset.HEARTS.spec, decodeOne(""","particles":$saved""").particles)
        // And a built-in picture is still saved that way, so an older version reads it too.
        val encoded = encodeLooks(listOf(Look(particles = ParticlePreset.HEARTS.spec)))
        assertTrue(""""image":"HEART"""" in encoded, encoded)
    }

    @Test
    fun customImagesKeepOnlyValidDistinctIdsUpToTheLimit() {
        fun images(ids: String) = decodeOne(""","particles":{"images":$ids,"motion":"FALL"}""").particles?.picture
        val mixed = images("""["$imageA",7,"../x","$imageA","$imageB"]""")
        assertEquals(ParticlePicture.Custom(listOf(imageA, imageB)), mixed, "invalid and repeated ids dropped")
        val many = List(MAX_PARTICLE_IMAGES + 2) { newParticleImageId() }
        val capped = images(many.joinToString(",", "[", "]") { "\"$it\"" })
        assertEquals(ParticlePicture.Custom(many.take(MAX_PARTICLE_IMAGES)), capped, "the first $MAX_PARTICLE_IMAGES")
        for (ids in listOf("[]", "[\"\"]", "[\"${imageA.uppercase()}\"]", "[\"$imageA/..\"]", "\"$imageA\"", "null")) {
            assertNull(images(ids), "no valid image, no particles: $ids")
        }
        // Custom images never fall back to a built-in image saved beside them.
        assertNull(decodeOne(""","particles":{"images":[],"image":"STAR","motion":"FALL"}""").particles)
    }

    @Test
    fun theAppIconIsOnlyATrueFlag() {
        assertEquals(ParticlePicture.AppIcon, decodeParticles(""","appIcon":true""")?.picture)
        for (flag in listOf("false", "1", "\"true\"", "null")) {
            assertEquals(star, decodeParticles(""","appIcon":$flag""")?.picture, "not the app icon: $flag")
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
