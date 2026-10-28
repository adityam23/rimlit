package app.rimlit.model

/** What notifications matched by a rule (from an app) glow with, instead of the default look. */
sealed interface Rule {

    /** The look with [lookId]; the default look once that look no longer exists. */
    data class UseLook(val lookId: String) : Rule

    /** The default look, painted with [palette]. */
    data class UsePalette(val palette: GlowPalette.Colors) : Rule
}
