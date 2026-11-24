package app.rimlit.model

import java.util.Locale

/**
 * A colour as the code people read and type: `#RRGGBB`, with no alpha. This is the user-facing form;
 * it is not `ColorHex` (`data/SettingsJson.kt`), the 8-digit ARGB form settings are stored in. They stay
 * separate because `ColorHex` is the forgiving storage codec, while `RgbHex` is the strict code a user types.
 */
object RgbHex {
    private const val DIGITS = 6
    private const val RGB_MASK = 0xFFFFFF
    private const val OPAQUE = 0xFF000000.toInt()
    private val code = Regex("#?[0-9A-Fa-f]{$DIGITS}")

    /** [argb] as `#RRGGBB`, uppercase; its alpha is ignored. */
    fun format(argb: Int): String = "#%06X".format(Locale.ROOT, argb and RGB_MASK)

    /**
     * The opaque ARGB colour [text] names, or `null` when it isn't six hex digits, with or without a
     * leading `#`, in any case and with surrounding spaces. No shorthand and no alpha digits.
     */
    fun parse(text: String): Int? {
        val trimmed = text.trim()
        if (!code.matches(trimmed)) return null
        return OPAQUE or trimmed.removePrefix("#").toInt(radix = 16)
    }
}
