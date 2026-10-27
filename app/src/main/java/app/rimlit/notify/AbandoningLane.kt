package app.rimlit.notify

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs blocking loads on [dispatcher] one at a time, each within [timeoutMs] (waiting for the lane included). A load
 * that overruns is abandoned: it is interrupted, its caller gets `null` at once, and later loads go to a fresh lane.
 * A read stuck in another app's provider may ignore the interrupt, and would otherwise hold the lane for good.
 */
class AbandoningLane(private val dispatcher: CoroutineDispatcher, private val timeoutMs: Long) {

    /** Loads run outside their caller's job, so a caller that times out stops waiting for one that won't stop. */
    private val scope = CoroutineScope(SupervisorJob())

    @Volatile
    private var lane = dispatcher.limitedParallelism(1)

    /** [block]'s result, or `null` if it didn't finish within the timeout. A cancelled caller cancels its load. */
    suspend fun <T> load(block: () -> T): T? {
        val current = lane
        val load = scope.async(current) { runInterruptible { block() } }
        try {
            val finished = withTimeoutOrNull(timeoutMs) { load.join() } != null
            if (!finished) {
                if (lane === current) lane = dispatcher.limitedParallelism(1)
                return null
            }
            return load.await()
        } finally {
            load.cancel()
        }
    }
}
