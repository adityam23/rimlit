package app.rimlit.model

// Where looks apply: as the default look, or for apps of their own (set from either side: the look's, or the app's);
// and saving a look the editor made with it.

/** Where a look applies: as the default look (every app without a look of its own), and for [apps], by package. */
data class UseFor(val isDefault: Boolean, val apps: Set<String>)

/** Where the look [id] applies now. */
fun GlowSettings.useFor(id: String): UseFor =
    UseFor(isDefault = id == defaultLookId, apps = appRules.filterValues { it == Rule(id) }.keys)

/** How many apps have the look [id] as their own: those that go back to the default look if it is deleted. */
fun GlowSettings.appsUsing(id: String): Int = appRules.values.count { it == Rule(id) }

/** What saving a look the editor made comes to: the settings with it [Saved], or why it was [Refused]. */
sealed interface LookSave {
    data class Saved(val settings: GlowSettings) : LookSave

    /** Not saved, each cause its own, so the user is told which. */
    enum class Refused : LookSave {
        /** A new look, with no room left for it ([MAX_LOOKS]). */
        LOOK_LIMIT,

        /** Another look has its name. */
        NAME_TAKEN,

        /** The saved look it replaces is gone: deleted meanwhile. It isn't brought back. */
        LOOK_DELETED,

        /** A new look whose id a saved one has. Ids are generated, so only a bug gets here; it never saves twice. */
        ID_TAKEN,
    }
}

/** These settings with [rule] for [packageName], or without its rule for `null` (the default look). */
fun GlowSettings.withAppRule(packageName: String, rule: Rule?): GlowSettings =
    copy(appRules = if (rule == null) appRules - packageName else appRules + (packageName to rule))

/** These settings with [packageName] glowing with the look [lookId]: no rule at all for the default look. */
fun GlowSettings.withAppLook(packageName: String, lookId: String): GlowSettings =
    withAppRule(packageName, Rule(lookId).takeIf { lookId != defaultLookId })

/**
 * These settings with [edited] saved, replacing the look with its id or added as a new one, applied as [useFor] says:
 * exactly its apps have it as their own, and it becomes the default look when [UseFor.isDefault]. The default look
 * stays the default whatever [useFor] says, since one look always is: another takes it over. A look saved always shows
 * something (see [showingSomething]). A look [isNew] is added; any other replaces the saved one. Refused, for the
 * first cause that applies, when it can't be saved (see [LookSave.Refused]).
 */
fun GlowSettings.savingLook(edited: Look, useFor: UseFor, isNew: Boolean): LookSave {
    val look = edited.showingSomething()
    val exists = this.look(look.id) != null
    val refused = when {
        !isNew && !exists -> LookSave.Refused.LOOK_DELETED
        isNew && exists -> LookSave.Refused.ID_TAKEN
        isNew && !canAddLook -> LookSave.Refused.LOOK_LIMIT
        !canName(look.name, exceptId = look.id) -> LookSave.Refused.NAME_TAKEN
        else -> null
    }
    if (refused != null) return refused
    val saved = if (isNew) copy(looks = looks + look) else updateLook(look.id) { look }
    val rule = Rule(look.id)
    return LookSave.Saved(
        saved.copy(
            appRules = saved.appRules.filterValues { it != rule } + useFor.apps.associateWith { rule },
            defaultLookId = if (useFor.isDefault) look.id else saved.defaultLookId,
        ),
    )
}
