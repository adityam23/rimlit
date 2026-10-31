package app.rimlit.engine

/**
 * The one open policy: the notification an open request from [surface] opens, or `null` when the request only ends
 * the glow. Only a notification glow shown there opens (see [actionable]), with opening turned on. Surfaces read it
 * too, to listen for a double tap only when it would open something.
 */
fun GlowState.opensOnRequest(surface: Surface): Post? = actionable(surface)?.takeIf { settings.openOnDoubleTap }

/**
 * The one clearing policy: the notification a dismissal from [surface] also cancels in the shade, or `null` when the
 * dismissal only ends the glow. As for an open (see [actionable]), with clearing on swipe turned on instead.
 */
fun GlowState.clearsOnDismiss(surface: Surface): Post? = actionable(surface)?.takeIf { settings.swipeClears }

/**
 * The notification a gesture on [surface] may act on: the one its glow shows there, while it is still posted, comes
 * from another app and is still wanted by the settings. A preview has nothing to act on, and Rimlit's own posts (the
 * test notification, the trampoline) lead nowhere new.
 */
private fun GlowState.actionable(surface: Surface): Post? {
    val shown = glow?.takeIf { it.surface == surface && it.opened } ?: return null
    return shown.source.post?.takeIf { !it.isOwn && it.key in activeKeys && settings.wants(it) }
}
