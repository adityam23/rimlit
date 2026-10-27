package app.rimlit.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.rimlit.model.AutoColors
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.MAX_PALETTE_COLORS
import app.rimlit.model.QuietHours
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import java.util.Locale

private const val TAG = "SettingsRepository"

/** The first retry after a failed read; each later one waits twice as long, up to [MAX_RETRY_DOUBLINGS] times. */
private const val READ_RETRY_MS = 1_000L
private const val MAX_RETRY_DOUBLINGS = 6L

private val Context.dataStore by preferencesDataStore(
    name = "settings",
    // A corrupt file would otherwise fail every read, crashing each start: losing the settings once is better.
    corruptionHandler = ReplaceFileCorruptionHandler { e ->
        Log.w(TAG, "Settings file corrupt; reset to defaults", e)
        emptyPreferences()
    },
)

private object Keys {
    val ENABLED = booleanPreferencesKey("enabled")
    val GLOW_STYLE = stringPreferencesKey("glow_style")
    val COLOR_MODE = stringPreferencesKey("color_mode")
    val AUTO_COLOR_SOURCE = stringPreferencesKey("auto_color_source")
    val AUTO_COLOR_COUNT = intPreferencesKey("auto_color_count")
    val CUSTOM_COLORS = stringPreferencesKey("custom_colors")
    val THICKNESS_DP = floatPreferencesKey("thickness_dp")
    val GLOW_WIDTH_DP = floatPreferencesKey("glow_width_dp")
    val SPEED = floatPreferencesKey("speed")
    val BRIGHTNESS = floatPreferencesKey("brightness")
    val SCREEN_OFF_BRIGHTNESS = floatPreferencesKey("screen_off_brightness")
    val DURATION_MS = longPreferencesKey("duration_ms")
    val BATTERY_SAVER = stringPreferencesKey("battery_saver")
    val SHOW_WHEN_SCREEN_OFF = booleanPreferencesKey("show_when_screen_off")
    val SHOW_ON_LOCK_SCREEN = booleanPreferencesKey("show_on_lock_screen")
    val SHOW_WHEN_UNLOCKED = booleanPreferencesKey("show_when_unlocked")
    val QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
    val QUIET_START_MINUTE = intPreferencesKey("quiet_start_minute")
    val QUIET_END_MINUTE = intPreferencesKey("quiet_end_minute")
    val QUIET_SCOPE = stringPreferencesKey("quiet_scope")
    val REGION = stringPreferencesKey("region")
    val BLOCKED_PACKAGES = stringSetPreferencesKey("blocked_packages")
    val APP_PALETTES = stringSetPreferencesKey("app_palettes")

    /** Pre-palette single custom color; read only to migrate into [CUSTOM_COLORS]. */
    val LEGACY_CUSTOM_COLOR = longPreferencesKey("custom_color_argb")

    /** One toggle for both locked places; read only to migrate into [SHOW_WHEN_SCREEN_OFF], [SHOW_ON_LOCK_SCREEN]. */
    val LEGACY_SHOW_WHEN_LOCKED = booleanPreferencesKey("show_when_locked")
}

/** Single source of truth for user settings, backed by Preferences DataStore. */
class SettingsRepository(private val context: Context) {

    /**
     * The stored settings. On the first I/O error it emits defaults, then keeps retrying with backoff, so the stored
     * settings come back once a read succeeds instead of the defaults lasting until the process restarts.
     */
    val settings: Flow<GlowSettings> = context.dataStore.data
        .retryWhen { e, attempt ->
            if (e !is IOException) return@retryWhen false
            Log.w(TAG, "Couldn't read settings; retrying", e)
            if (attempt == 0L) emit(emptyPreferences())
            delay(READ_RETRY_MS shl attempt.coerceAtMost(MAX_RETRY_DOUBLINGS).toInt())
            true
        }
        .map { it.toSettings() }

    /** Atomically read-modify-writes the settings. */
    suspend fun update(transform: (GlowSettings) -> GlowSettings) {
        context.dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }
}

private val defaults = GlowSettings()

/** Tolerant decode: unknown or out-of-range stored values fall back to defaults or are clamped. */
private fun Preferences.toSettings() = GlowSettings(
    enabled = valueOr(Keys.ENABLED, defaults.enabled),
    glowStyle = this[Keys.GLOW_STYLE].toEnumOr(defaults.glowStyle),
    colorMode = this[Keys.COLOR_MODE].toEnumOr(defaults.colorMode),
    autoColors = AutoColors(
        source = this[Keys.AUTO_COLOR_SOURCE].toEnumOr(defaults.autoColors.source),
        count = clamped(Keys.AUTO_COLOR_COUNT, defaults.autoColors.count, Ranges.autoColorCount),
    ),
    customColors = this[Keys.CUSTOM_COLORS]?.let(::decodePalette)
        ?: this[Keys.LEGACY_CUSTOM_COLOR]?.let { listOf(it.toInt()) }
        ?: defaults.customColors,
    thicknessDp = clamped(Keys.THICKNESS_DP, defaults.thicknessDp, Ranges.thicknessDp),
    glowWidthDp = clamped(Keys.GLOW_WIDTH_DP, defaults.glowWidthDp, Ranges.glowWidthDp),
    speed = clamped(Keys.SPEED, defaults.speed, Ranges.speed),
    brightness = clamped(Keys.BRIGHTNESS, defaults.brightness, Ranges.brightness),
    // Absent is the default, "same as on screen" (null): write removes the key for it.
    screenOffBrightness = (this[Keys.SCREEN_OFF_BRIGHTNESS] ?: defaults.screenOffBrightness)
        ?.coerceIn(Ranges.brightness),
    durationMs = clamped(Keys.DURATION_MS, defaults.durationMs, Ranges.durationMs),
    batterySaver = this[Keys.BATTERY_SAVER].toEnumOr(defaults.batterySaver),
    showWhenScreenOff = lockedToggle(Keys.SHOW_WHEN_SCREEN_OFF, defaults.showWhenScreenOff),
    showOnLockScreen = lockedToggle(Keys.SHOW_ON_LOCK_SCREEN, defaults.showOnLockScreen),
    showWhenUnlocked = valueOr(Keys.SHOW_WHEN_UNLOCKED, defaults.showWhenUnlocked),
    quietHours = QuietHours(
        enabled = valueOr(Keys.QUIET_ENABLED, defaults.quietHours.enabled),
        startMinute = clamped(Keys.QUIET_START_MINUTE, defaults.quietHours.startMinute, Ranges.minuteOfDay),
        endMinute = clamped(Keys.QUIET_END_MINUTE, defaults.quietHours.endMinute, Ranges.minuteOfDay),
        scope = this[Keys.QUIET_SCOPE].toEnumOr(defaults.quietHours.scope),
    ),
    region = this[Keys.REGION].toEnumOr(defaults.region),
    blockedPackages = valueOr(Keys.BLOCKED_PACKAGES, defaults.blockedPackages),
    appPalettes = this[Keys.APP_PALETTES]?.let(::decodeAppPalettes) ?: defaults.appPalettes,
)

/** The stored value of [key], or [default] when it was never written. */
private fun <T> Preferences.valueOr(key: Preferences.Key<T>, default: T): T = this[key] ?: default

/** A toggle split from the legacy show-when-locked one: never written yet, it starts at that toggle's value. */
private fun Preferences.lockedToggle(key: Preferences.Key<Boolean>, default: Boolean): Boolean =
    valueOr(key, valueOr(Keys.LEGACY_SHOW_WHEN_LOCKED, default))

/** The stored value of [key] clamped to [range], or [default] when it was never written. */
private fun <T : Comparable<T>> Preferences.clamped(key: Preferences.Key<T>, default: T, range: ClosedRange<T>): T =
    valueOr(key, default).coerceIn(range)

private fun MutablePreferences.write(settings: GlowSettings) {
    this[Keys.ENABLED] = settings.enabled
    this[Keys.GLOW_STYLE] = settings.glowStyle.name
    this[Keys.COLOR_MODE] = settings.colorMode.name
    this[Keys.AUTO_COLOR_SOURCE] = settings.autoColors.source.name
    this[Keys.AUTO_COLOR_COUNT] = settings.autoColors.count
    this[Keys.CUSTOM_COLORS] = encodePalette(settings.customColors)
    this[Keys.THICKNESS_DP] = settings.thicknessDp
    this[Keys.GLOW_WIDTH_DP] = settings.glowWidthDp
    this[Keys.SPEED] = settings.speed
    this[Keys.BRIGHTNESS] = settings.brightness
    val screenOffBrightness = settings.screenOffBrightness
    if (screenOffBrightness != null) {
        this[Keys.SCREEN_OFF_BRIGHTNESS] = screenOffBrightness
    } else {
        remove(Keys.SCREEN_OFF_BRIGHTNESS)
    }
    this[Keys.DURATION_MS] = settings.durationMs
    this[Keys.BATTERY_SAVER] = settings.batterySaver.name
    this[Keys.SHOW_WHEN_SCREEN_OFF] = settings.showWhenScreenOff
    this[Keys.SHOW_ON_LOCK_SCREEN] = settings.showOnLockScreen
    this[Keys.SHOW_WHEN_UNLOCKED] = settings.showWhenUnlocked
    this[Keys.QUIET_ENABLED] = settings.quietHours.enabled
    this[Keys.QUIET_START_MINUTE] = settings.quietHours.startMinute
    this[Keys.QUIET_END_MINUTE] = settings.quietHours.endMinute
    this[Keys.QUIET_SCOPE] = settings.quietHours.scope.name
    this[Keys.REGION] = settings.region.name
    this[Keys.BLOCKED_PACKAGES] = settings.blockedPackages
    this[Keys.APP_PALETTES] = settings.appPalettes.map { (pkg, colors) -> "$pkg=${encodePalette(colors)}" }.toSet()
    remove(Keys.LEGACY_CUSTOM_COLOR)
    remove(Keys.LEGACY_SHOW_WHEN_LOCKED)
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
