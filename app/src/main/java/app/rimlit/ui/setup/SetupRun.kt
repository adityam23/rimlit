package app.rimlit.ui.setup

import app.rimlit.model.GlowSettings
import app.rimlit.model.Premade
import app.rimlit.model.UiPrefs
import app.rimlit.model.canAddLook
import app.rimlit.model.defaultLookAdded
import app.rimlit.model.defaultLookSwitchedTo

/**
 * Which run of the guided first launch this is: the [FIRST], until it is finished, or a [RERUN] from Behavior's "Run
 * setup again", which leaves what the user already set up as it is.
 */
internal enum class SetupRun {
    FIRST,
    RERUN,
    ;

    /** Whether a premade can be picked as the default look: a rerun adds one, so only while another look fits. */
    fun canPick(settings: GlowSettings): Boolean = this == FIRST || settings.canAddLook

    /**
     * [settings] with [premade] picked as the default look, named [name]: the first run switches the default look to
     * it (the one a fresh install has); a rerun adds it as a new look and makes it the default, keeping the old one.
     * `null` when it can't be picked (see [canPick]).
     */
    fun picking(settings: GlowSettings, premade: Premade, name: String): GlowSettings? = when (this) {
        FIRST -> settings.defaultLookSwitchedTo(premade, name)
        RERUN -> settings.defaultLookAdded(premade, name)
    }

    /** [settings] as Finish leaves them: the first run turns glowing on; a rerun keeps the switch as it is. */
    fun finishing(settings: GlowSettings): GlowSettings = when (this) {
        FIRST -> settings.copy(enabled = true)
        RERUN -> settings
    }

    companion object {
        /** The run that starts with the stored UI preferences [prefs]: a rerun once setup was finished. */
        fun of(prefs: UiPrefs?): SetupRun = if (prefs?.setupDone == true) RERUN else FIRST
    }
}
