package app.rimlit.data

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Settings stored before looks migrate, once, to the same glows; and what is written reads back the same. */
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
    private val migration = PaletteLooksMigration(names)

    /** These preferences as DataStore hands them to the first read: migrated, when they need to be. */
    private fun Preferences.migrated(): Preferences = runBlocking {
        if (migration.shouldMigrate(this@migrated)) migration.migrate(this@migrated) else this@migrated
    }

    /** The settings a read of these stored preferences gives. */
    private fun Preferences.read() = migrated().toSettings()

    /** Runs [block] on a real settings store in [file], with [migrations], closing it after. */
    private fun <T> withStore(
        file: File,
        migrations: List<DataMigration<Preferences>>,
        block: suspend (DataStore<Preferences>) -> T,
    ): T = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            block(PreferenceDataStoreFactory.create(migrations = migrations, scope = scope) { file })
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test
    fun theGlobalLookBecomesTheDefaultLook() {
        val settings = preLooks().read()
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
        val settings = preLooks().read()
        assertEquals(listOf("Default", "Alpha colors", "Beta colors"), settings.looks.map { it.name })
        assertEquals(
            mapOf("com.a" to Rule("colours:com.a"), "com.b" to Rule("colours:com.b")),
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
        val settings = prefs.read()
        assertEquals(Rule("work"), settings.appRules["com.a"])
        assertEquals(Rule("colours:com.b"), settings.appRules["com.b"])
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
    fun theMigrationIsWrittenOnceAndItsLooksNeverChange() {
        val migrated = preLooks().migrated()
        assertEquals(2, names.labelLookups, "each app's label looked up once")
        assertFalse(runBlocking { migration.shouldMigrate(migrated) }, "nothing left to migrate")
        val settings = migrated.toSettings()
        names.labels = mapOf("com.a" to "Renamed")
        assertEquals(settings, migrated.read(), "an app renamed since changes nothing")
        assertEquals(settings, migrated.read(), "same ids and names on every read")
        assertEquals(2, names.labelLookups, "never looked up again")
    }

    @Test
    fun aStoreMigratesBeforeItsFirstReadAndNeverAgain() {
        val file = File.createTempFile("settings", ".preferences_pb").apply { delete() }
        try {
            withStore(file, migrations = emptyList()) { store -> store.updateData { preLooks() } }
            val first = withStore(file, listOf(migration)) { SettingsRepository(it).stored() }
            assertEquals(listOf("Default", "Alpha colors", "Beta colors"), first.looks.map { it.name })
            assertEquals(Rule("colours:com.a"), first.appRules["com.a"])
            names.labels = emptyMap()
            val reopened = withStore(file, listOf(migration)) { SettingsRepository(it).stored() }
            assertEquals(first, reopened, "the migrated settings as written, whatever the apps are named now")
            assertEquals(2, names.labelLookups, "migrated once")
        } finally {
            file.delete()
        }
    }

    @Test
    fun theMigrationCompletesWhenALabelLookupFails() {
        val dying = RuntimeException("DeadSystemException")
        val names = FakePaletteLookNames(mapOf("com.b" to "Beta"), throwing = mapOf("com.a" to dying))
        val migration = PaletteLooksMigration(names)
        val migrated = runBlocking { migration.migrate(preLooks()) }
        assertFalse(runBlocking { migration.shouldMigrate(migrated) }, "written, so never run again")
        val settings = migrated.toSettings()
        assertEquals(listOf("Default", "Beta colors", "com.a colors"), settings.looks.map { it.name })
        assertEquals(Rule("colours:com.a"), settings.appRules["com.a"])
        assertEquals(listOf<Pair<String, Exception>>("com.a" to dying), names.failures, "reported, not swallowed")
    }

    @Test
    fun settingsWithNoPalettesNeedNoMigration() {
        for (prefs in listOf(emptyPreferences(), mutablePreferencesOf().apply { write(GlowSettings()) })) {
            assertFalse(runBlocking { migration.shouldMigrate(prefs) })
        }
        val looksOnly = mutablePreferencesOf().apply {
            this[stringPreferencesKey("app_rules")] = """{"com.a":{"look":"default"}}"""
        }
        assertFalse(runBlocking { migration.shouldMigrate(looksOnly) })
        assertEquals(0, names.labelLookups)
    }

    @Test
    fun theOldestSingleCustomColourStillMigrates() {
        val prefs = mutablePreferencesOf(longPreferencesKey("custom_color_argb") to 0xFF00FF00L)
        assertEquals(listOf(green), prefs.read().defaultLook.customColors.argb)
    }

    @Test
    fun nothingStoredDecodesToTheDefaults() {
        assertEquals(GlowSettings(), emptyPreferences().read())
    }

    @Test
    fun writingReplacesTheOldKeysAndReadsBackTheSame() {
        val prefs = preLooks()
        val migrated = prefs.read()
        prefs.write(migrated)
        val old = setOf(
            "glow_style", "color_mode", "auto_color_source", "auto_color_count", "custom_colors", "region",
            "thickness_dp", "glow_width_dp", "speed", "brightness", "app_palettes",
        )
        assertTrue(prefs.asMap().keys.none { it.name in old }, "old keys removed")
        assertEquals(migrated, prefs.read())
    }

    @Test
    fun looksAndRulesReadBackTheSame() {
        val settings = GlowSettings(
            looks = listOf(
                Look(),
                Look(id = "work", name = "Work", style = GlowStyle.BASIC, particles = ParticlePreset.RAIN.spec),
            ),
            defaultLookId = "work",
            appRules = mapOf("com.a" to Rule("work"), "com.b" to Rule(DEFAULT_LOOK_ID)),
        )
        val prefs = mutablePreferencesOf().apply { write(settings) }
        assertEquals(settings, prefs.read())
    }

    @Test
    fun thePillTogglesReadBackTheSame() {
        val settings = GlowSettings().let { it.copy(showPill = !it.showPill, swipeClears = !it.swipeClears) }
        val prefs = mutablePreferencesOf().apply { write(settings) }
        assertEquals(settings, prefs.read())
    }

    @Test
    fun aMissingDefaultLookFallsBackToTheFirstLook() {
        val prefs = mutablePreferencesOf().apply {
            write(GlowSettings(looks = listOf(Look(id = "a", name = "A")), defaultLookId = "a"))
            this[stringPreferencesKey("default_look")] = "gone"
        }
        assertEquals("a", prefs.read().defaultLookId)
    }
}
