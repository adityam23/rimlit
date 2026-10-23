package app.rimlit.engine

/**
 * Holds one timer or extraction to the executor contract: only its latest start may deliver. Starting again or
 * cancelling replaces the handle a pending delivery is checked against.
 */
internal class LatestHandle {

    private var handle: Cancellable? = null

    /**
     * Cancels the previous start and starts anew. [start] receives the check its delivery must pass when its turn
     * comes: `true` only for the latest start, which it also retires.
     */
    fun replace(start: (claim: () -> Boolean) -> Cancellable) {
        cancel()
        lateinit var started: Cancellable
        started = start { (handle === started).also { if (it) handle = null } }
        handle = started
    }

    fun cancel() {
        handle?.cancel()
        handle = null
    }
}
