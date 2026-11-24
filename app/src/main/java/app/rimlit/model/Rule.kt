package app.rimlit.model

/**
 * An app's rule: its notifications glow with the look [lookId] instead of the default look, and with the default look
 * again once that look no longer exists (see [GlowSettings.appLook]).
 */
data class Rule(val lookId: String)
