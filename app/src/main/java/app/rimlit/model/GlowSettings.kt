package app.rimlit.model

/**
 * Visual treatment of the edge glow shown when a notification arrives. [NONE] draws no edge, so a look can show its
 * particles alone.
 */
enum class GlowStyle { BASIC, GLOW, BLADE, WAVE, GLITTER, ECHO, LOOP, MIRROR, BOOMERANG, FIREWORKS, NONE }

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

/** Where a look's colors come from. Per-app palettes override all modes. */
enum class ColorMode {
    /** Palette extracted from the notifying app, as [Look.autoColors] says. */
    AUTO,

    /** The look's [Look.customColors] (one color = solid, more = gradient). */
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

/** What a notification glows with, from [GlowSettings.resolve]. */
sealed interface Resolution {

    /** The colors are known: show [spec]. */
    data class Ready(val spec: GlowSpec) : Resolution

    /** Auto colors: extract them as [autoColors] say (costly, so the caller does), then resolve again with them. */
    data class Extract(val autoColors: AutoColors) : Resolution
}

/**
 * Persisted user configuration.
 *
 * How glows look lives in [looks]: notifications glow with the one named by [defaultLookId], unless their app's rule
 * in [appRules] picks another look or a palette of its own. The rest is global: how long glows hold, and when and
 * where they show.
 * `blockedPackages` holds apps that never trigger edge lighting (empty = allow all).
 * Where glows show: [showWhenScreenOff] the lock-screen surface (the screen woken from off), [showOnLockScreen] an
 * overlay above a visible lock screen, [showWhenUnlocked] an overlay while unlocked.
 */
data class GlowSettings(
    val enabled: Boolean = false,
    /** 1–[MAX_LOOKS] looks, with unique ids and names (see [lookNameKey]), one of them the default. */
    val looks: List<Look> = listOf(Look()),
    val defaultLookId: String = DEFAULT_LOOK_ID,
    /** Brightness on the lock-screen surface; `null` follows the look's brightness. */
    val screenOffBrightness: Float? = null,
    val durationMs: Long = 4_000L,
    val batterySaver: BatterySaver = BatterySaver.WITH_SYSTEM,
    val showWhenScreenOff: Boolean = true,
    val showOnLockScreen: Boolean = true,
    val showWhenUnlocked: Boolean = true,
    /**
     * A double tap on the lock-screen glow unlocks and opens its notification (off: it only ends the glow), and one
     * on the pill opens it (off: it does nothing).
     */
    val openOnDoubleTap: Boolean = true,
    /** On-screen glows show a pill with the notifying app's icon and name, out of the camera cutout. */
    val showPill: Boolean = true,
    /** Swiping the pill away also dismisses its notification in the shade; off, it only ends the glow. */
    val swipeClears: Boolean = false,
    val quietHours: QuietHours = QuietHours(),
    /** Corner radius of the screen-edge outline; `null` matches the display's own corners. */
    val cornerRadiusDp: Float? = null,
    /** The camera cutout the ring hugs; `null` matches the one the display reports. */
    val customCutout: CustomCutout? = null,
    val blockedPackages: Set<String> = emptySet(),
    /** Per-app rules, by package name, overriding the default look. */
    val appRules: Map<String, Rule> = emptyMap(),
) {
    init {
        require(looks.size in 1..MAX_LOOKS) { "Settings hold 1–$MAX_LOOKS looks" }
        require(looks.distinctBy { it.id }.size == looks.size) { "Look ids must be unique" }
        require(looks.distinctBy { lookNameKey(it.name) }.size == looks.size) { "Look names must be unique" }
        require(looks.any { it.id == defaultLookId }) { "The default look must exist" }
    }

    /** The look notifications glow with unless their app's rule says otherwise. */
    val defaultLook: Look get() = looks.first { it.id == defaultLookId }

    /** The look with [id], or `null` when there is none. */
    fun look(id: String): Look? = looks.firstOrNull { it.id == id }

    /** These settings with the look [id] replaced by [edit] of it. */
    fun updateLook(id: String, edit: (Look) -> Look): GlowSettings =
        copy(looks = looks.map { if (it.id == id) edit(it) else it })

    /**
     * [packageName]'s rule as it applies: `null`, the default look, when it has none or its rule names a look that no
     * longer exists.
     */
    fun ruleFor(packageName: String): Rule? =
        appRules[packageName]?.takeUnless { it is Rule.UseLook && look(it.lookId) == null }

    /**
     * The one place a notification's look and colors are chosen: [packageName]'s rule as it applies (see [ruleFor]),
     * else the default look. A palette rule paints the default look; otherwise the look's own colors apply, and auto
     * colors need [extracted] (see [Resolution.Extract]).
     */
    fun resolve(packageName: String, extracted: List<Int>? = null): Resolution {
        val rule = ruleFor(packageName)
        val look = (rule as? Rule.UseLook)?.let { look(it.lookId) } ?: defaultLook
        val palette = (rule as? Rule.UsePalette)?.palette
            ?: look.palette
            ?: extracted?.let(GlowPalette::Colors)
            ?: return Resolution.Extract(look.autoColors)
        return Resolution.Ready(spec(look, palette))
    }

    /**
     * The spec previews of [look] render (the default look unless one is being edited): auto colors, with no
     * notifying app to sample, use a stand-in.
     */
    fun previewSpec(look: Look = defaultLook): GlowSpec =
        spec(look, look.palette ?: GlowPalette.Colors(PreviewAutoColors.take(look.autoColors.count)))

    /** The one spec builder: [look] painted with [palette], with these settings' global parts. */
    private fun spec(look: Look, palette: GlowPalette): GlowSpec = GlowSpec(
        style = look.style,
        palette = palette,
        region = look.region,
        cornerRadiusDp = cornerRadiusDp,
        customCutout = customCutout,
        thicknessDp = look.thicknessDp,
        glowWidthDp = look.glowWidthDp,
        speed = look.speed,
        brightness = look.brightness,
        screenOffBrightness = screenOffBrightness ?: look.brightness,
        durationMs = durationMs,
        batterySaver = batterySaver,
        particles = look.particles,
    )

    /** Valid ranges, shared by the UI sliders and the repository's sanitising of stored values. */
    object Ranges {
        val autoColorCount = 1..MAX_AUTO_COLORS
        val thicknessDp = 1f..12f
        val glowWidthDp = 0f..64f
        val cornerRadiusDp = 0f..120f
        val cutoutSizeDp = 4f..80f
        val cutoutRadiusDp = 0f..40f
        val cutoutOffsetDp = -40f..40f
        val speed = 0.25f..3f
        val brightness = 0.2f..1f
        val durationMs = 1_000L..10_000L
        val minuteOfDay = 0 until MINUTES_PER_DAY
        val particleSizeDp = 8f..48f
        val particleSpeed = speed
        val particleAmount = 0.1f..1f
        val particleRotation = 0f..360f
        val lookNameLength = 1..30
    }
}
