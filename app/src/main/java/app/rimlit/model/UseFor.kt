package app.rimlit.model

// Where looks apply: as the default look, or for apps of their own; and saving a look the editor made with it.

/** Where a look applies: as the default look (every app without a look of its own), and for [apps], by package. */
data class UseFor(val isDefault: Boolean, val apps: Set<String>)

/** Where the look [id] applies now. */
fun GlowSettings.useFor(id: String): UseFor =
    UseFor(isDefault = id == defaultLookId, apps = appRules.filterValues { it == Rule(id) }.keys)

/** How many apps have the look [id] as their own: those that go back to the default look if it is deleted. */
fun GlowSettings.appsUsing(id: String): Int = appRules.values.count { it == Rule(id) }

/**
 * These settings with [look] saved, replacing the look with its id or added as a new one, applied as [useFor] says:
 * exactly its apps have it as their own, and it becomes the default look when [UseFor.isDefault]. The default look
 * stays the default whatever [useFor] says, since one look always is: another takes it over. Unchanged when the look
 * is new and no look fits, or its name is taken.
 */
fun GlowSettings.savingLook(look: Look, useFor: UseFor): GlowSettings {
    val saved = when {
        !canName(look.name, exceptId = look.id) -> return this
        this.look(look.id) != null -> updateLook(look.id) { look }
        canAddLook -> copy(looks = looks + look)
        else -> return this
    }
    val rule = Rule(look.id)
    return saved.copy(
        appRules = saved.appRules.filterValues { it != rule } + useFor.apps.associateWith { rule },
        defaultLookId = if (useFor.isDefault) look.id else saved.defaultLookId,
    )
}
