package app.rimlit.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.MAX_PALETTE_COLORS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "settings")

private object Keys {
    val ENABLED = booleanPreferencesKey("enabled")
    val GLOW_STYLE = stringPreferencesKey("glow_style")
    val COLOR_MODE = stringPreferencesKey("color_mode")
    val CUSTOM_COLORS = stringPreferencesKey("custom_colors")
    val THICKNESS_DP = floatPreferencesKey("thickness_dp")
    val GLOW_WIDTH_DP = floatPreferencesKey("glow_width_dp")
    val SPEED = floatPreferencesKey("speed")
    val BRIGHTNESS = floatPreferencesKey("brightness")
    val DURATION_MS = longPreferencesKey("duration_ms")
    val SHOW_WHEN_LOCKED = booleanPreferencesKey("show_when_locked")
    val SHOW_WHEN_UNLOCKED = booleanPreferencesKey("show_when_unlocked")
    val REGION = stringPreferencesKey("region")
    val BLOCKED_PACKAGES = stringSetPreferencesKey("blocked_packages")
    val APP_PALETTES = stringSetPreferencesKey("app_palettes")

    /** Pre-palette single custom color; read only to migrate into [CUSTOM_COLORS]. */
    val LEGACY_CUSTOM_COLOR = longPreferencesKey("custom_color_argb")
}

/** Single source of truth for user settings, backed by Preferences DataStore. */
class SettingsRepository(private val context: Context) {

    val settings: Flow<GlowSettings> = context.dataStore.data.map { it.toSettings() }

    /** Atomically read-modify-writes the settings. */
    suspend fun update(transform: (GlowSettings) -> GlowSettings) {
        context.dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }
}

private val defaults = GlowSettings()

/** Tolerant decode: unknown or out-of-range stored values fall back to defaults or are clamped. */
private fun Preferences.toSettings() = GlowSettings(
    enabled = this[Keys.ENABLED] ?: defaults.enabled,
    glowStyle = this[Keys.GLOW_STYLE].toEnumOr(defaults.glowStyle),
    colorMode = this[Keys.COLOR_MODE].toEnumOr(defaults.colorMode),
    customColors = this[Keys.CUSTOM_COLORS]?.let(::decodePalette)
        ?: this[Keys.LEGACY_CUSTOM_COLOR]?.let { listOf(it.toInt()) }
        ?: defaults.customColors,
    thicknessDp = (this[Keys.THICKNESS_DP] ?: defaults.thicknessDp).coerceIn(Ranges.thicknessDp),
    glowWidthDp = (this[Keys.GLOW_WIDTH_DP] ?: defaults.glowWidthDp).coerceIn(Ranges.glowWidthDp),
    speed = (this[Keys.SPEED] ?: defaults.speed).coerceIn(Ranges.speed),
    brightness = (this[Keys.BRIGHTNESS] ?: defaults.brightness).coerceIn(Ranges.brightness),
    durationMs = (this[Keys.DURATION_MS] ?: defaults.durationMs).coerceIn(Ranges.durationMs),
    showWhenLocked = this[Keys.SHOW_WHEN_LOCKED] ?: defaults.showWhenLocked,
    showWhenUnlocked = this[Keys.SHOW_WHEN_UNLOCKED] ?: defaults.showWhenUnlocked,
    region = this[Keys.REGION].toEnumOr(defaults.region),
    blockedPackages = this[Keys.BLOCKED_PACKAGES] ?: defaults.blockedPackages,
    appPalettes = this[Keys.APP_PALETTES]?.let(::decodeAppPalettes) ?: defaults.appPalettes,
)

private fun MutablePreferences.write(settings: GlowSettings) {
    this[Keys.ENABLED] = settings.enabled
    this[Keys.GLOW_STYLE] = settings.glowStyle.name
    this[Keys.COLOR_MODE] = settings.colorMode.name
    this[Keys.CUSTOM_COLORS] = encodePalette(settings.customColors)
    this[Keys.THICKNESS_DP] = settings.thicknessDp
    this[Keys.GLOW_WIDTH_DP] = settings.glowWidthDp
    this[Keys.SPEED] = settings.speed
    this[Keys.BRIGHTNESS] = settings.brightness
    this[Keys.DURATION_MS] = settings.durationMs
    this[Keys.SHOW_WHEN_LOCKED] = settings.showWhenLocked
    this[Keys.SHOW_WHEN_UNLOCKED] = settings.showWhenUnlocked
    this[Keys.REGION] = settings.region.name
    this[Keys.BLOCKED_PACKAGES] = settings.blockedPackages
    this[Keys.APP_PALETTES] = settings.appPalettes.map { (pkg, colors) -> "$pkg=${encodePalette(colors)}" }.toSet()
    remove(Keys.LEGACY_CUSTOM_COLOR)
}

private inline fun <reified E : Enum<E>> String?.toEnumOr(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

/** Palettes are stored as comma-separated 8-digit ARGB hex, e.g. `ff00bcd4,ffe040fb`. */
private fun encodePalette(colors: List<Int>): String = colors.joinToString(",") { "%08x".format(Locale.ROOT, it) }

private fun decodePalette(encoded: String): List<Int>? = encoded.split(',')
    .mapNotNull { it.toLongOrNull(16)?.toInt() }
    .take(MAX_PALETTE_COLORS)
    .ifEmpty { null }

/** Entries are `package=palette`; package names never contain `=`. */
private fun decodeAppPalettes(entries: Set<String>): Map<String, List<Int>> = entries.mapNotNull { entry ->
    val pkg = entry.substringBefore('=', missingDelimiterValue = "")
    val palette = decodePalette(entry.substringAfter('='))
    if (pkg.isEmpty() || palette == null) null else pkg to palette
}.toMap()
