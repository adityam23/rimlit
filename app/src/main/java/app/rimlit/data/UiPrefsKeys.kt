package app.rimlit.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.rimlit.model.LookPart
import app.rimlit.model.UiPrefs

// The UI preferences ([UiPrefs]) as the settings store holds them, beside the settings but written apart from them
// (see SettingsRepository.updateUi). Each keeps the key it had while it was a setting, so moving it lost nothing.

private object UiKeys {
    /** The editor's tabs set to tinker, by [LookPart] name. */
    val TINKERING = stringSetPreferencesKey("tinkering")
    val ALWAYS_TINKER = booleanPreferencesKey("always_tinker")

    /** The recommended permissions put off, by name. */
    val DISMISSED_PERMISSIONS = stringSetPreferencesKey("dismissed_permissions")
}

private val uiDefaults = UiPrefs()

/** The UI preferences, tolerantly: unknown tabs (from a newer version) are dropped. */
internal fun Preferences.toUiPrefs(): UiPrefs = UiPrefs(
    tinkering = this[UiKeys.TINKERING]?.mapNotNullTo(mutableSetOf()) { it.toEnumOrNull<LookPart>() }
        ?: uiDefaults.tinkering,
    alwaysTinker = this[UiKeys.ALWAYS_TINKER] ?: uiDefaults.alwaysTinker,
    dismissedPermissions = this[UiKeys.DISMISSED_PERMISSIONS] ?: uiDefaults.dismissedPermissions,
)

internal fun MutablePreferences.write(prefs: UiPrefs) {
    this[UiKeys.TINKERING] = prefs.tinkering.mapTo(mutableSetOf()) { it.name }
    this[UiKeys.ALWAYS_TINKER] = prefs.alwaysTinker
    this[UiKeys.DISMISSED_PERMISSIONS] = prefs.dismissedPermissions
}
