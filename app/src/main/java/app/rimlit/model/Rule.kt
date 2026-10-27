package app.rimlit.model

/** What notifications matched by a rule (from an app) glow with, instead of the default look. */
sealed interface Rule {

    /** The look with [lookId]; the default look once that look no longer exists. */
    data class UseLook(val lookId: String) : Rule

    /** The default look, painted with [colors] (one = solid, more = gradient). */
    data class UsePalette(val colors: List<Int>) : Rule {
        init {
            require(colors.size in 1..MAX_PALETTE_COLORS) { "Palette needs 1–$MAX_PALETTE_COLORS colors" }
        }
    }
}
