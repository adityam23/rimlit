package app.rimlit.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Colors painted around the glow outline. */
sealed interface GlowPalette : Parcelable {

    /** One color = solid; 2–5 colors = gradient spread evenly around the outline, flowing over time. */
    @Parcelize
    data class Colors(val argb: List<Int>) : GlowPalette {
        init {
            require(argb.size in 1..MAX_PALETTE_COLORS) { "Palette needs 1–$MAX_PALETTE_COLORS colors" }
        }
    }

    /** Full hue cycle flowing around the outline. */
    @Parcelize
    data object Rainbow : GlowPalette
}

/**
 * Everything needed to render one glow. The single input to the renderer, the overlay,
 * the lock-screen activity, and the in-app previews; built via [GlowSettings.toGlowSpec].
 */
@Parcelize
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
    /** How long the glow holds before fading out. */
    val durationMs: Long,
) : Parcelable
