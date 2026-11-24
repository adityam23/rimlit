package app.rimlit.model

/**
 * Which run of the guided first launch this is: the [FIRST], until it is finished, or a [RERUN] from Behavior's "Run
 * setup again", which leaves what the user already set up as it is.
 */
enum class SetupRun {
    FIRST,
    RERUN,
    ;

    /**
     * Whether a premade can be picked as the default look in [settings]: a rerun adds one, so only while another look
     * fits, or once it has added its look ([addedId]), which a pick replaces.
     */
    fun canPick(settings: GlowSettings, addedId: String): Boolean =
        this == FIRST || settings.canAddLook || settings.look(addedId) != null

    /**
     * [settings] with [premade] picked as the default look, named [name] (cut to fit, numbered when another look has
     * it). The first run switches the default look to it (the one a fresh install has). A rerun adds it as a new look
     * under [addedId] and makes it the default, keeping the old one; picking again in the same rerun switches that
     * look instead, so a rerun adds at most one. `null` when it can't be picked (see [canPick]).
     */
    fun picking(settings: GlowSettings, premade: Premade, name: String, addedId: String): GlowSettings? = when {
        this == FIRST -> settings.defaultLookSwitchedTo(premade, name)
        settings.look(addedId) != null -> settings.copy(defaultLookId = addedId).defaultLookSwitchedTo(premade, name)
        else -> settings.defaultLookAdded(premade, name, addedId)
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
