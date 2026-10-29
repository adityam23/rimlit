package app.rimlit.model

import java.util.UUID

// The look library's edits on GlowSettings: naming, adding, renaming and deleting looks, and setting app rules. Each
// returns the settings unchanged when the edit isn't allowed, so the settings' invariants always hold.

/** Whether another look fits, at most [MAX_LOOKS]. */
val GlowSettings.canAddLook: Boolean get() = looks.size < MAX_LOOKS

/** Whether [name] is a valid look name (see [isValidLookName]) no look but [exceptId] uses. */
fun GlowSettings.canName(name: String, exceptId: String? = null): Boolean =
    isValidLookName(name) && looks.none { it.id != exceptId && lookNameKey(it.name) == lookNameKey(name) }

/**
 * The first of [candidates] a new look can be named, or `null` when none of the first [MAX_LOOKS] + 1 can: with at
 * most [MAX_LOOKS] looks, that many distinct valid names always hold a free one.
 */
fun GlowSettings.firstFreeName(candidates: Sequence<String>): String? =
    candidates.take(MAX_LOOKS + 1).firstOrNull { canName(it) }

/**
 * These settings with a copy of [from] added as [name], under [id]; unchanged when no look fits, the name can't be
 * used, or the id is taken.
 */
fun GlowSettings.addLook(from: Look, name: String, id: String = UUID.randomUUID().toString()): GlowSettings =
    if (!canAddLook || !canName(name) || look(id) != null) {
        this
    } else {
        copy(looks = looks + from.copy(id = id, name = name))
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
    copy(looks = looks.filterNot { it.id == id }, appRules = appRules.filterValues { it != Rule.UseLook(id) })
}

/** These settings with [rule] for [packageName], or without its rule for `null` (the default look). */
fun GlowSettings.withAppRule(packageName: String, rule: Rule?): GlowSettings =
    copy(appRules = if (rule == null) appRules - packageName else appRules + (packageName to rule))
