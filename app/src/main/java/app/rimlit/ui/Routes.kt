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
