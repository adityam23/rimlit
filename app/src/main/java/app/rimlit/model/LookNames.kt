package app.rimlit.model

import app.rimlit.model.GlowSettings.Ranges

// How the looks in GlowSettings may be named: a name no other look uses, numbered when taken, "Work (2)".

/** Whether [name] is a valid look name (see [isValidLookName]) no look but [exceptId] uses. */
fun GlowSettings.canName(name: String, exceptId: String? = null): Boolean =
    isValidLookName(name) && looks.none { it.id != exceptId && lookNameKey(it.name) == lookNameKey(name) }

/**
 * The first of [candidates] a new look can be named, or `null` when none of the first [MAX_LOOKS] + 1 can: with at
 * most [MAX_LOOKS] looks, that many distinct valid names always hold a free one.
 */
fun GlowSettings.firstFreeName(candidates: Sequence<String>): String? =
    candidates.take(MAX_LOOKS + 1).firstOrNull { canName(it) }

/** [name] (a valid look name) if no look uses it, else the first numbered one free, "Work (2)"; `null` if none is. */
fun GlowSettings.numberedName(name: String): String? = firstFreeName(numberedNames(name))

/**
 * A new look's name from [base] ("Blade", "Work copy"): cut to fit, then numbered when taken, "Work copy (2)"; just cut
 * when no numbered one is free either (the look limit stops it being added then anyway).
 */
fun GlowSettings.freshName(base: String): String = numberedOrAsIs(fitLookName(base))

/**
 * [freshName] from an untrusted [base], such as an app's label: `null` when, cut to fit, it makes no look name at all
 * ("   ").
 */
fun GlowSettings.freshNameOrNull(base: String): String? =
    fitLookName(base).takeIf(::isValidLookName)?.let { numberedOrAsIs(it) }

/** [fitted] numbered when taken, as it is when no numbered one is free either. */
private fun GlowSettings.numberedOrAsIs(fitted: String): String = numberedName(fitted) ?: fitted

/** [name] (a valid look name), then numbered from 2, "Work (2)", cut so the number always fits. */
private fun numberedNames(name: String): Sequence<String> =
    sequenceOf(name) + generateSequence(2) { it + 1 }.map { number ->
        val suffix = " ($number)"
        fitLookName(name, Ranges.lookNameLength.last - suffix.length) + suffix
    }
