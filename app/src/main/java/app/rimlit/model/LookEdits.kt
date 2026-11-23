package app.rimlit.model

import java.util.UUID

// The look library's edits on GlowSettings: adding, renaming and deleting looks (named as LookNames.kt allows; an
// app's look is set in UseFor.kt). Each returns the settings unchanged when the edit isn't allowed, so the settings'
// invariants always hold.

/** Whether another look fits, at most [MAX_LOOKS]. */
val GlowSettings.canAddLook: Boolean get() = looks.size < MAX_LOOKS

/**
 * These settings with a copy of [from] added as [name], under [id]; unchanged when no look fits, the name can't be
 * used, or the id is taken.
 */
fun GlowSettings.addLook(from: Look, name: String, id: String = UUID.randomUUID().toString()): GlowSettings =
    withLookAdded(from, name, id) ?: this

/**
 * These settings with copies of [imported] added after their own looks, each under a new id from [newId], as many as
 * fit. A name already taken gets a number, "Work (2)". Rules and the default look are unchanged.
 */
fun GlowSettings.addImportedLooks(
    imported: List<Look>,
    newId: () -> String = { UUID.randomUUID().toString() },
): GlowSettings = imported.fold(this) { settings, look ->
    settings.addLookNumbered(look, look.name, newId()) ?: settings
}

/**
 * These settings with a copy of [from] added as [name] (a valid look name), numbered when taken, "Work (2)", under
 * [id]; `null` when it isn't added: no look fits, or the id is taken.
 */
fun GlowSettings.addLookNumbered(from: Look, name: String, id: String): GlowSettings? =
    numberedName(name)?.let { withLookAdded(from, it, id) }

/** These settings with a copy of [from] added as [name] under [id]; `null` when it isn't allowed (see [addLook]). */
private fun GlowSettings.withLookAdded(from: Look, name: String, id: String): GlowSettings? =
    if (!canAddLook || !canName(name) || look(id) != null) {
        null
    } else {
        copy(looks = looks + from.copy(id = id, name = name))
    }

/**
 * These settings with the default look switched to [premade] (everything it changed is lost) and renamed [name], cut
 * to fit and numbered when another look has it, "Blade (2)": the first look picked in the guided first launch.
 */
fun GlowSettings.defaultLookSwitchedTo(premade: Premade, name: String): GlowSettings {
    val free = numberedNames(fitLookName(name))
        .take(MAX_LOOKS + 1)
        .firstOrNull { canName(it, exceptId = defaultLookId) }
    return updateLook(defaultLookId) { it.switchedTo(premade).copy(name = free ?: it.name) }
}

/** These settings with the look [id] renamed [name]; unchanged when the name can't be used. */
fun GlowSettings.renameLook(id: String, name: String): GlowSettings =
    if (canName(name, exceptId = id)) updateLook(id) { it.copy(name = name) } else this

/**
 * These settings without the look [id], and without the rules that use it, whose apps go back to the default look.
 * The default look can't be deleted: then they are unchanged.
 */
fun GlowSettings.deleteLook(id: String): GlowSettings = if (id == defaultLookId) {
    this
} else {
    copy(looks = looks.filterNot { it.id == id }, appRules = appRules.filterValues { it.lookId != id })
}
