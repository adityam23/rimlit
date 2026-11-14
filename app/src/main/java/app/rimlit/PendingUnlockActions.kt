package app.rimlit

/**
 * [UnlockActivity]'s pending actions, one per launch, keyed by that launch's own id. Keeps concurrent launches from
 * clobbering each other the way a single slot would: a quick second [add] can't overwrite a first whose activity
 * hasn't started yet, since each is [take]n only by its own id.
 *
 * Bounded at [limit] entries so a launch that's blocked and never starts (so never takes its action) can't leak
 * forever: adding past the limit drops the oldest still-pending id first. Not thread-safe; [UnlockActivity] only
 * touches this from the main thread.
 */
internal class PendingUnlockActions(private val limit: Int = DEFAULT_LIMIT) {

    private val actions = LinkedHashMap<String, () -> Unit>()

    /** Remembers [action] for [id], evicting the oldest still-pending id first if already at [limit]. */
    fun add(id: String, action: () -> Unit) {
        if (actions.size >= limit) {
            val oldest = actions.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            }
        }
        actions[id] = action
    }

    /** Removes and returns [id]'s action, or `null` if there isn't one (already taken, evicted, or never added). */
    fun take(id: String): (() -> Unit)? = actions.remove(id)

    private companion object {
        const val DEFAULT_LIMIT = 4
    }
}
