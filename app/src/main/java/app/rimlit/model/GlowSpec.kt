package app.rimlit.model

/** Colors painted around the glow outline. */
sealed interface GlowPalette {

    /** One color = solid; 2–5 colors = gradient spread evenly around the outline, flowing over time. */
    data class Colors(val argb: List<Int>) : GlowPalette {
        init {
            require(argb.size in 1..MAX_PALETTE_COLORS) { "Palette needs 1–$MAX_PALETTE_COLORS colors" }
        }
    }

    /** Full hue cycle flowing around the outline. */
    data object Rainbow : GlowPalette
}

/**
 * Everything needed to render one glow. The single input to the renderer, the overlay,
 * the lock-screen activity, and the in-app previews; built via [GlowSettings.toGlowSpec].
 */
data class GlowSpec(
    val style: GlowStyle,
    val palette: GlowPalette,
    val region: EdgeRegion,
    val thicknessDp: Float,
    val glowWidthDp: Float,
    /** Animation rate multiplier (1 = default). */
    val speed: Float,
    /** Peak opacity in [0, 1]. */
    val brightness: Float,
    /** Peak opacity on the lock-screen surface, woken from off. */
    val screenOffBrightness: Float,
    /** How long the glow holds before fading out. */
    val durationMs: Long,
    val batterySaver: BatterySaver,
)

/** When glows save battery by drawing fewer frames. */
enum class BatterySaver {
    OFF,

    /** While the system's battery saver is on. */
    WITH_SYSTEM,
    ALWAYS,
}
