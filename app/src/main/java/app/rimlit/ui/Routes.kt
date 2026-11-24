package app.rimlit.ui

import app.rimlit.model.UiPrefs

/** The app's screens, as [AppNavHost] routes them. */
internal object Routes {
    const val HOME = "home"
    const val APPS = "apps"
    const val BEHAVIOR = "behavior"
    const val DEVICE = "device"
    const val SETUP = "setup"
}

/**
 * Where the app opens, from the stored UI preferences ([prefs], `null` until read, so nothing is chosen yet): the
 * guided first launch until it is finished, then Home.
 */
internal fun startRoute(prefs: UiPrefs?): String? = prefs?.let { if (it.setupDone) Routes.HOME else Routes.SETUP }

/**
 * Where the app opens: [pinned], the start once chosen, or else [startRoute]. Pinned, finishing setup leaves the start
 * as it was, rather than moving it to Home and rebuilding the screens there, where Finish has just gone anyway.
 */
internal fun startRoute(pinned: String?, prefs: UiPrefs?): String? = pinned ?: startRoute(prefs)
