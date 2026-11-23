package app.rimlit.ui

import app.rimlit.data.AppInfo
import app.rimlit.model.GlowSettings

/** The Apps list's filter chips. */
internal enum class AppFilter {
    ALL,

    /** The apps with a look of their own. */
    OWN_LOOK,

    /** The apps switched away from the mode's default: off while every app glows, on while only chosen apps do. */
    SWITCHED,
}

/** [packageName] differs from the mode's default: it has its own look, or its switch is the other way. */
private fun GlowSettings.isCustomised(packageName: String): Boolean =
    appLook(packageName) != null || isSwitched(packageName)

private fun GlowSettings.isSwitched(packageName: String): Boolean = glowsFor(packageName) == onlyChosenApps

/**
 * These apps (in name order) as the Apps list shows them under [filter], by [settings] as they are now: the customised
 * apps first, then the rest, each in name order. Worked out when the list is opened, searched or filtered, never as
 * the user changes an app, so a row stays where it is.
 */
internal fun List<AppInfo>.arranged(settings: GlowSettings, filter: AppFilter): List<AppInfo> = filter { app ->
    when (filter) {
        AppFilter.ALL -> true
        AppFilter.OWN_LOOK -> settings.appLook(app.packageName) != null
        AppFilter.SWITCHED -> settings.isSwitched(app.packageName)
    }
}.sortedBy { !settings.isCustomised(it.packageName) }
