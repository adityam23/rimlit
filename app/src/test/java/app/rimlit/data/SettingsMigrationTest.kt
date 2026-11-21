package app.rimlit.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.rimlit.model.AutoColorSource
import app.rimlit.model.AutoColors
import app.rimlit.model.BatterySaver
import app.rimlit.model.ColorMode
import app.rimlit.model.DEFAULT_LOOK_ID
import app.rimlit.model.EdgeRegion
import app.rimlit.model.GlowPalette
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSpec
import app.rimlit.model.GlowStyle
import app.rimlit.model.Look
import app.rimlit.model.ParticlePreset
import app.rimlit.model.Premade
import app.rimlit.model.Resolution
import app.rimlit.model.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Settings stored before looks decode to the same glows; and what is written reads back the same. */
class SettingsMigrationTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val green = 0xFF00FF00.toInt()

    /** The global look and per-app palettes as the settings stored them before looks. */
    private fun preLooks() = mutablePreferencesOf(
        stringPreferencesKey("glow_style") to "BLADE",
        stringPreferencesKey("color_mode") to "CUSTOM",
        stringPreferencesKey("auto_color_source") to "NOTIFICATION_IMAGE",
        intPreferencesKey("auto_color_count") to 2,
        stringPreferencesKey("custom_colors") to "ffff0000,ff0000ff",
        stringPreferencesKey("region") to "CUTOUT",
        floatPreferencesKey("thickness_dp") to 6f,
        floatPreferencesKey("glow_width_dp") to 30f,
        floatPreferencesKey("speed") to 2f,
        floatPreferencesKey("brightness") to 0.5f,
        floatPreferencesKey("screen_off_brightness") to 0.3f,
        longPreferencesKey("duration_ms") to 6_000L,
        stringPreferencesKey("battery_saver") to "ALWAYS",
        stringSetPreferencesKey("app_palettes") to setOf("com.a=ff00ff00", "com.b=ff00ff00,ff0000ff", "=ff00ff00"),
    )

    /** The spec the settings before looks built for a glow painted with [palette]. */
    private fun preLooksSpec(palette: GlowPalette) = GlowSpec(
        style = GlowStyle.BLADE,
        palette = palette,
        region = EdgeRegion.CUTOUT,
        cornerRadiusDp = null,
        customCutout = null,
        thicknessDp = 6f,
        glowWidthDp = 30f,
        speed = 2f,
        brightness = 0.5f,
        screenOffBrightness = 0.3f,
        durationMs = 6_000L,
        batterySaver = BatterySaver.ALWAYS,
    )

    private fun GlowSettings.spec(packageName: String) = (resolve(packageName) as Resolution.Ready).spec

    private val names = FakePaletteLookNames(mapOf("com.a" to "Alpha", "com.b" to "Beta"))

    private fun Preferences.toSettings() = toSettings(names)

    @Test
    fun theGlobalLookBecomesTheDefaultLook() {
        val settings = preLooks().toSettings()
        assertEquals(
            Look(
                basedOn = Premade.BLADE,
                style = GlowStyle.BLADE,
                colorMode = ColorMode.CUSTOM,
                autoColors = AutoColors(AutoColorSource.NOTIFICATION_IMAGE, count = 2),
                customColors = GlowPalette.Colors(listOf(red, blue)),
                region = EdgeRegion.CUTOUT,
                thicknessDp = 6f,
                glowWidthDp = 30f,
                speed = 2f,
                brightness = 0.5f,
            ),
            settings.defaultLook,
        )
        assertEquals(preLooksSpec(GlowPalette.Colors(listOf(red, blue))), settings.spec("com.other"))
    }

    @Test
    fun perAppPalettesBecomeLooksOfTheirOwn() {
        val settings = preLooks().toSettings()
        assertEquals(listOf("Default", "Alpha colors", "Beta colors"), settings.looks.map { it.name })
        assertEquals(
            mapOf("com.a" to Rule.UseLook("colours:com.a"), "com.b" to Rule.UseLook("colours:com.b")),
            settings.appRules,
        )
        // Each glows as before: the default look in the app's colours.
        assertEquals(preLooksSpec(GlowPalette.Colors(listOf(green))), settings.spec("com.a"))
        assertEquals(preLooksSpec(GlowPalette.Colors(listOf(green, blue))), settings.spec("com.b"))
    }

    @Test
    fun paletteRulesStoredWithLooksBecomeLooksToo() {
        val prefs = mutablePreferencesOf().apply {
            write(GlowSettings(looks = listOf(Look(), Look(id = "work", name = "Work"))))
            this[stringPreferencesKey("app_rules")] = """{"com.a":{"look":"work"},"com.b":{"palette":["ffff0000"]}}"""
        }
        val settings = prefs.toSettings()
        assertEquals(Rule.UseLook("work"), settings.appRules["com.a"])
        assertEquals(Rule.UseLook("colours:com.b"), settings.appRules["com.b"])
        assertEquals(
            Look(
                id = "colours:com.b",
                name = "Beta colors",
                colorMode = ColorMode.CUSTOM,
                customColors = GlowPalette.Colors(listOf(red)),
            ),
            settings.look("colours:com.b"),
            "a copy of the default look in the app's colours",
        )
    }

    @Test
    fun theMigrationReadsTheSameEveryTimeUntilWrittenAndAfter() {
        val prefs = preLooks()
        val migrated = prefs.toSettings()
        assertEquals(migrated, prefs.toSettings(), "same ids and names on every read")
        prefs.write(migrated)
        assertEquals(migrated, prefs.toSettings())
    }

    @Test
    fun theOldestSingleCustomColourStillMigrates() {
        val prefs = mutablePreferencesOf(longPreferencesKey("custom_color_argb") to 0xFF00FF00L)
        assertEquals(listOf(green), prefs.toSettings().defaultLook.customColors.argb)
    }

    @Test
    fun nothingStoredDecodesToTheDefaults() {
        assertEquals(GlowSettings(), emptyPreferences().toSettings())
    }

    @Test
    fun writingReplacesTheOldKeysAndReadsBackTheSame() {
        val prefs = preLooks()
        val migrated = prefs.toSettings()
        prefs.write(migrated)
        val old = setOf(
            "glow_style", "color_mode", "auto_color_source", "auto_color_count", "custom_colors", "region",
            "thickness_dp", "glow_width_dp", "speed", "brightness", "app_palettes",
        )
        assertTrue(prefs.asMap().keys.none { it.name in old }, "old keys removed")
        assertEquals(migrated, prefs.toSettings())
    }

    @Test
    fun looksAndRulesReadBackTheSame() {
        val settings = GlowSettings(
            looks = listOf(
                Look(),
                Look(id = "work", name = "Work", style = GlowStyle.BASIC, particles = ParticlePreset.RAIN.spec),
            ),
            defaultLookId = "work",
            appRules = mapOf("com.a" to Rule.UseLook("work"), "com.b" to Rule.UseLook(DEFAULT_LOOK_ID)),
        )
        val prefs = mutablePreferencesOf().apply { write(settings) }
        assertEquals(settings, prefs.toSettings())
    }

    @Test
    fun thePillTogglesReadBackTheSame() {
        val settings = GlowSettings().let { it.copy(showPill = !it.showPill, swipeClears = !it.swipeClears) }
        val prefs = mutablePreferencesOf().apply { write(settings) }
        assertEquals(settings, prefs.toSettings())
    }

    @Test
    fun aMissingDefaultLookFallsBackToTheFirstLook() {
        val prefs = mutablePreferencesOf().apply {
            write(GlowSettings(looks = listOf(Look(id = "a", name = "A")), defaultLookId = "a"))
            this[stringPreferencesKey("default_look")] = "gone"
        }
        assertEquals("a", prefs.toSettings().defaultLookId)
    }
}
