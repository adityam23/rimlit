package app.rimlit.engine

/**
 * The one open policy: the notification an open request from [surface] opens, or `null` when the request only ends
 * the glow. Only a notification glow shown there opens, and only while it is still posted, comes from another app and
 * is still wanted by the settings, with opening turned on. A preview has nothing to open, and Rimlit's own posts (the
 * test notification, the trampoline) lead nowhere new. Surfaces read it too, to listen for a double tap only when it
 * would open something.
 */
fun GlowState.opensOnRequest(surface: Surface): Post? {
    val shown = glow?.takeIf { it.surface == surface && it.opened } ?: return null
    return shown.source.post?.takeIf {
        settings.openOnDoubleTap && !it.isOwn && it.key in activeKeys && settings.wants(it)
    }
}
