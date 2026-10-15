package app.rimlit.model

/** Visual treatment of the edge glow shown when a notification arrives. */
enum class GlowStyle { BASIC, GLOW, BLADE }

/** Where the glow is drawn. [CUTOUT] falls back to [FULL_SCREEN] on devices without a camera cutout. */
enum class EdgeRegion { FULL_SCREEN, CUTOUT }

/** Where the glow colors come from. Per-app palettes override all modes. */
enum class ColorMode {
    /** Palette extracted from the notifying app (picture → large icon → app icon). */
    AUTO,

    /** The user's [GlowSettings.customColors] (one color = solid, more = gradient). */
    CUSTOM,

    /** Full hue cycle flowing around the edge. */
    RAINBOW,
}

/** Brand cyan; used as the default custom color and whenever no color can be resolved. */
const val DEFAULT_GLOW_ARGB: Int = 0xFF00BCD4.toInt()

/** A palette holds 1 (solid) to [MAX_PALETTE_COLORS] (gradient) colors. */
const val MAX_PALETTE_COLORS = 5

/**
 * Persisted user configuration.
 *
 * `blockedPackages` holds apps that never trigger edge lighting (empty = allow all).
 * `appPalettes` maps a package to its own 1–5 color palette, overriding [colorMode].
 */
data class GlowSettings(
    val enabled: Boolean = false,
    val glowStyle: GlowStyle = GlowStyle.GLOW,
    val colorMode: ColorMode = ColorMode.AUTO,
    val customColors: List<Int> = listOf(DEFAULT_GLOW_ARGB),
    val thicknessDp: Float = 4f,
    val glowWidthDp: Float = 24f,
    val speed: Float = 1f,
    val brightness: Float = 1f,
    val durationMs: Long = 4_000L,
    val showWhenLocked: Boolean = true,
    val showWhenUnlocked: Boolean = true,
    val region: EdgeRegion = EdgeRegion.FULL_SCREEN,
    val blockedPackages: Set<String> = emptySet(),
    val appPalettes: Map<String, List<Int>> = emptyMap(),
) {
    /**
     * Builds the render input for a notification from [packageName] (`null` for previews).
     * [autoColors] is only invoked when the auto palette is actually needed, since extraction is costly.
     */
    fun toGlowSpec(packageName: String?, autoColors: () -> List<Int>): GlowSpec {
        val palette = packageName?.let(appPalettes::get)?.let(GlowPalette::Colors)
            ?: when (colorMode) {
                ColorMode.AUTO -> GlowPalette.Colors(autoColors())
                ColorMode.CUSTOM -> GlowPalette.Colors(customColors)
                ColorMode.RAINBOW -> GlowPalette.Rainbow
            }
        return GlowSpec(
            style = glowStyle,
            palette = palette,
            region = region,
            thicknessDp = thicknessDp,
            glowWidthDp = glowWidthDp,
            speed = speed,
            brightness = brightness,
            durationMs = durationMs,
        )
    }

    /** Valid ranges, shared by the UI sliders and the repository's sanitising of stored values. */
    object Ranges {
        val thicknessDp = 1f..12f
        val glowWidthDp = 0f..64f
        val speed = 0.25f..3f
        val brightness = 0.2f..1f
        val durationMs = 1_000L..10_000L
    }
}
