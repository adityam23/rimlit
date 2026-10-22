package app.rimlit.ui.glow

import app.rimlit.model.GlowSpec

/**
 * One request to show a glow. Deliberately not a data class: every request is distinct by identity,
 * so a new notification restarts the timeline even when its spec equals the current one.
 */
class GlowRequest(val spec: GlowSpec)
