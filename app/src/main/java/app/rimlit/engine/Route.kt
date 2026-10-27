package app.rimlit.engine

import app.rimlit.model.GlowSettings

/** Where a glow goes. */
internal sealed interface Route {
    data class To(val surface: Surface) : Route

    data object Nowhere : Route

    /** Only the lock screen would do, but the trampoline can't wake the screen (see [Environment.canWakeScreen]). */
    data object NeedsWakeAccess : Route
}

/**
 * The one placement policy: where a glow from [source], posted under [settings], goes now. [lockScreenUp]: the
 * lock-screen activity is in front (covering the keyguard), so notifications re-target it instead of an overlay.
 */
internal fun GlowState.route(source: GlowSource, settings: GlowSettings, lockScreenUp: Boolean): Route = when {
    // Previews ignore the show toggles and quiet hours: the user is asking to see that surface. Nothing draws on an
    // overlay in the dark: it would show nothing, and its timers stop in deep sleep.
    source is GlowSource.Preview && source.target == PreviewTarget.ON_SCREEN ->
        if (environment.screenOn) Route.To(onScreenOverlay) else Route.Nowhere

    source is GlowSource.Preview -> lockScreen(lockScreenUp, ringing = false)

    environment.screenOn && !lockScreenUp -> {
        val wanted = if (environment.locked) settings.showOnLockScreen else settings.showWhenUnlocked
        onScreenSurface()?.takeIf { wanted && !quiet(source, settings, lockScreen = false) }?.let(Route::To)
            ?: Route.Nowhere
    }

    settings.showWhenScreenOff && !quiet(source, settings, lockScreen = true) ->
        lockScreen(lockScreenUp, ringing = source.post?.isRinging == true)

    else -> Route.Nowhere
}

/**
 * Quiet hours mute this glow now. A ringing call or alarm always goes through, as with Do Not Disturb, and so does
 * Rimlit's own test notification, as it does with the master toggle off: the user asked to see it.
 */
private fun GlowState.quiet(source: GlowSource, settings: GlowSettings, lockScreen: Boolean): Boolean {
    val exempt = source.post?.let { it.isRinging || it.isTest } == true
    return !exempt && settings.quietHours.mutes(environment.minuteOfDay, lockScreen)
}

/** The overlay on-screen glows and previews are drawn on: the accessibility one whenever its service is up. */
val GlowState.onScreenOverlay: Surface
    get() = if (accessibilityOverlay) Surface.ACCESSIBILITY_OVERLAY else Surface.APP_OVERLAY

/** The accessibility overlay also draws above the lock screen; the app overlay only while unlocked. */
private fun GlowState.onScreenSurface(): Surface? =
    onScreenOverlay.takeIf { accessibilityOverlay || !environment.locked }

/**
 * The opaque lock-screen activity: never for a [ringing] post nor while anything rings (it would cover
 * Answer/Decline), and only the full-screen-intent trampoline can wake the screen into it.
 */
private fun GlowState.lockScreen(lockScreenUp: Boolean, ringing: Boolean): Route = when {
    ringing || ringingKeys.isNotEmpty() -> Route.Nowhere
    !lockScreenUp && !environment.screenOn && !environment.canWakeScreen -> Route.NeedsWakeAccess
    else -> Route.To(Surface.LOCK_SCREEN)
}
