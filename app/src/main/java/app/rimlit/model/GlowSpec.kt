package app.rimlit.model

/** Colors painted around the glow outline. */
sealed interface GlowPalette {

    /**
     * One color = solid; 2–5 colors = gradient spread evenly around the outline, flowing over time. The one place a
     * palette's size is checked: looks and rules hold their colors as this.
     */
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
 * the lock-screen activity, and the in-app previews; built via [GlowSettings.resolve] or [GlowSettings.previewSpec].
 */
data class GlowSpec(
    val style: GlowStyle,
    val palette: GlowPalette,
    val region: EdgeRegion,
    /** Corner radius of the screen-edge outline; `null` follows the display's corners. */
    val cornerRadiusDp: Float?,
    /** The camera cutout the ring hugs; `null` follows the display's. */
    val customCutout: CustomCutout?,
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
    /** Particles drawn beneath the edge; `null` for none. */
    val particles: ParticleSpec? = null,
    /**
     * The notifying app, whose icon [ParticlePicture.AppIcon] particles draw; `null` for any other picture, and for a
     * preview, which draws Rimlit's own icon.
     */
    val iconPackage: String? = null,
)
