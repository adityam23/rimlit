package app.rimlit.model

/** Visual treatment of the edge glow shown when a notification arrives. */
enum class GlowStyle { BASIC, GLOW, BLADE }

/**
 * Where the glow is drawn: around the screen edge, the camera cutout, or both. Without a camera cutout, the
 * regions that ring it draw the screen edge only.
 */
enum class EdgeRegion(val ringsCutout: Boolean, val tracesEdge: Boolean) {
    FULL_SCREEN(ringsCutout = false, tracesEdge = true),
    CUTOUT(ringsCutout = true, tracesEdge = false),
    CUTOUT_AND_EDGE(ringsCutout = true, tracesEdge = true),
}

/** When glows save battery by drawing fewer frames. */
enum class BatterySaver {
    OFF,

    /** While the system's battery saver is on. */
    WITH_SYSTEM,
    ALWAYS,
}

/** Where the glow colors come from. Per-app palettes override all modes. */
enum class ColorMode {
    /** Palette extracted from the notifying app, as [GlowSettings.autoColors] says. */
    AUTO,

    /** The user's [GlowSettings.customColors] (one color = solid, more = gradient). */
    CUSTOM,

    /** Full hue cycle flowing around the edge. */
    RAINBOW,
}

/** The image [ColorMode.AUTO] reads its colors from. */
enum class AutoColorSource {
    /** The notifying app's icon: each app glows in its own color. */
    APP_ICON,

    /** The notification's picture or large icon (a photo, an avatar), falling back to the app icon. */
    NOTIFICATION_IMAGE,
}

/** How [ColorMode.AUTO] extracts a palette: from [source], its [count] most vivid distinct hues. */
data class AutoColors(val source: AutoColorSource = AutoColorSource.APP_ICON, val count: Int = 1) {
    init {
        require(count in GlowSettings.Ranges.autoColorCount) { "Auto colors pick 1–$MAX_AUTO_COLORS colors" }
    }
}

/** Brand cyan; used as the default custom color and whenever no color can be resolved. */
const val DEFAULT_GLOW_ARGB: Int = 0xFF00BCD4.toInt()

/** A palette holds 1 (solid) to [MAX_PALETTE_COLORS] (gradient) colors. */
const val MAX_PALETTE_COLORS = 5

/** Auto colors extract at most this many hues: an image rarely has more distinct vivid ones. */
const val MAX_AUTO_COLORS = 3

/** Stand-in for the auto palette in previews; they show its first [AutoColors.count]. */
private val PreviewAutoColors = listOf(DEFAULT_GLOW_ARGB, 0xFFE040FB.toInt(), 0xFFFFC400.toInt())

/**
 * Persisted user configuration.
 *
 * `blockedPackages` holds apps that never trigger edge lighting (empty = allow all).
 * `appPalettes` maps a package to its own 1–5 color palette, overriding [colorMode].
 * Where glows show: [showWhenScreenOff] the lock-screen surface (the screen woken from off), [showOnLockScreen] an
 * overlay above a visible lock screen, [showWhenUnlocked] an overlay while unlocked.
 */
data class GlowSettings(
    val enabled: Boolean = false,
    val glowStyle: GlowStyle = GlowStyle.GLOW,
    val colorMode: ColorMode = ColorMode.AUTO,
    val autoColors: AutoColors = AutoColors(),
    val customColors: List<Int> = listOf(DEFAULT_GLOW_ARGB),
    val thicknessDp: Float = 4f,
    val glowWidthDp: Float = 24f,
    val speed: Float = 1f,
    val brightness: Float = 1f,
    /** Brightness on the lock-screen surface; `null` follows [brightness]. */
    val screenOffBrightness: Float? = null,
    val durationMs: Long = 4_000L,
    val batterySaver: BatterySaver = BatterySaver.WITH_SYSTEM,
    val showWhenScreenOff: Boolean = true,
    val showOnLockScreen: Boolean = true,
    val showWhenUnlocked: Boolean = true,
    val quietHours: QuietHours = QuietHours(),
    val region: EdgeRegion = EdgeRegion.FULL_SCREEN,
    /** Corner radius of the screen-edge outline; `null` matches the display's own corners. */
    val cornerRadiusDp: Float? = null,
    val blockedPackages: Set<String> = emptySet(),
    val appPalettes: Map<String, List<Int>> = emptyMap(),
) {
    /**
     * The palette for a notification from [packageName] (`null` for previews): a per-app palette wins, then
     * [colorMode]. `null` means [ColorMode.AUTO]: the caller extracts it from the notification, since that's costly.
     */
    fun paletteFor(packageName: String?): GlowPalette? =
        packageName?.let(appPalettes::get)?.let(GlowPalette::Colors) ?: when (colorMode) {
            ColorMode.AUTO -> null
            ColorMode.CUSTOM -> GlowPalette.Colors(customColors)
            ColorMode.RAINBOW -> GlowPalette.Rainbow
        }

    /** The render input for these settings, painted with [palette] (see [paletteFor]). */
    fun toGlowSpec(palette: GlowPalette): GlowSpec = GlowSpec(
        style = glowStyle,
        palette = palette,
        region = region,
        cornerRadiusDp = cornerRadiusDp,
        thicknessDp = thicknessDp,
        glowWidthDp = glowWidthDp,
        speed = speed,
        brightness = brightness,
        screenOffBrightness = screenOffBrightness ?: brightness,
        durationMs = durationMs,
        batterySaver = batterySaver,
    )

    /** The spec previews render for these settings: auto colours, with no notifying app to sample, use a stand-in. */
    fun previewSpec(): GlowSpec =
        toGlowSpec(paletteFor(packageName = null) ?: GlowPalette.Colors(PreviewAutoColors.take(autoColors.count)))

    /** Valid ranges, shared by the UI sliders and the repository's sanitising of stored values. */
    object Ranges {
        val autoColorCount = 1..MAX_AUTO_COLORS
        val thicknessDp = 1f..12f
        val glowWidthDp = 0f..64f
        val cornerRadiusDp = 0f..120f
        val speed = 0.25f..3f
        val brightness = 0.2f..1f
        val durationMs = 1_000L..10_000L
        val minuteOfDay = 0 until MINUTES_PER_DAY
    }
}
