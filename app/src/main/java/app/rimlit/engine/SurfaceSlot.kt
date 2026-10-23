package app.rimlit.engine

/**
 * Holds one surface to the executor contract: its reports count only for the instance of its latest `Open`, until
 * that is closed. Every open and close starts a new generation, and a queued report is delivered only if its
 * generation is still current when its turn comes. Closing withdraws the request and closes the instance.
 */
internal class SurfaceSlot {

    private var generation = 0

    /** An `Open` is outstanding and hasn't been closed or reported gone since. */
    private var requested = false

    private var instance: SurfaceInstance? = null

    /** Withdraws the latest request (e.g. cancels the lock screen's trampoline); kept until the slot is closed. */
    private var withdraw: Cancellable? = null

    /** [request] starts opening the surface and returns the handle that withdraws it. */
    fun open(request: () -> Cancellable) {
        generation++
        requested = true
        instance = null
        withdraw = request()
    }

    fun close() {
        generation++
        requested = false
        withdraw?.cancel()
        withdraw = null
        instance?.close()
        instance = null
    }

    /**
     * [opening] is up. Returns the check for its `SurfaceOpened` report, or `null` if there is nothing to report: it
     * replaces an instance already confirmed, or nobody asked for it, in which case it is closed.
     */
    fun opened(opening: SurfaceInstance): (() -> Boolean)? {
        if (!requested) {
            opening.close()
            return null
        }
        val confirmed = instance != null
        instance = opening
        return if (confirmed) null else current()
    }

    /**
     * [gone] went away by itself; `null` for an instance that failed to open. Returns the check for its
     * `SurfaceGone` report, or `null` if it isn't the requested instance.
     */
    fun gone(gone: SurfaceInstance?): (() -> Boolean)? {
        if (!requested || instance !== gone) return null
        requested = false
        instance = null
        return current()
    }

    private fun current(): () -> Boolean {
        val reported = generation
        return { generation == reported }
    }
}
