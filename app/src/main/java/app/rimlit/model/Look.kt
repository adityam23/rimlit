package app.rimlit.model

import java.util.Locale

/** The id of the look settings start with, and the one older settings migrate into. */
const val DEFAULT_LOOK_ID = "default"

/** The name of the look settings start with, and the one older settings migrate into. */
const val DEFAULT_LOOK_NAME = "Default"

/** At most this many looks are saved. */
const val MAX_LOOKS = 10

/**
 * A named, saved bundle of how a glow looks: its style, colours, region and tuning. Notifications glow with the
 * default look unless their app's [Rule] picks another (see [GlowSettings.resolve]); how long and where glows show
 * stays global in [GlowSettings].
 */
data class Look(
    val id: String = DEFAULT_LOOK_ID,
    val name: String = DEFAULT_LOOK_NAME,
    val style: GlowStyle = GlowStyle.GLOW,
    val colorMode: ColorMode = ColorMode.AUTO,
    val autoColors: AutoColors = AutoColors(),
    /** The palette [ColorMode.CUSTOM] paints with. */
    val customColors: GlowPalette.Colors = GlowPalette.Colors(listOf(DEFAULT_GLOW_ARGB)),
    val region: EdgeRegion = EdgeRegion.FULL_SCREEN,
    val thicknessDp: Float = 4f,
    val glowWidthDp: Float = 24f,
    val speed: Float = 1f,
    val brightness: Float = 1f,
) {
    init {
        require(id.isNotEmpty()) { "A look needs an id" }
        require(isValidLookName(name)) { "Invalid look name" }
    }

    /** The colors [colorMode] fixes; `null` for [ColorMode.AUTO], which are extracted per notification. */
    val palette: GlowPalette?
        get() = when (colorMode) {
            ColorMode.AUTO -> null
            ColorMode.CUSTOM -> customColors
            ColorMode.RAINBOW -> GlowPalette.Rainbow
        }
}

/** A look name is 1–30 characters, with no leading or trailing blanks. */
fun isValidLookName(name: String): Boolean =
    name.length in GlowSettings.Ranges.lookNameLength && name.isNotBlank() && name == name.trim()

/** Look names are unique regardless of case, so two looks never read the same in a list. */
fun lookNameKey(name: String): String = name.lowercase(Locale.ROOT)
