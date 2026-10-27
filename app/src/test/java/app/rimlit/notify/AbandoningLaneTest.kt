package app.rimlit.notify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AbandoningLaneTest {

    private val lane = AbandoningLane(Dispatchers.IO, TIMEOUT_MS)
    private val release = CountDownLatch(1)

    @After
    fun releaseStuckLoads() = release.countDown()

    /** Blocks until released, ignoring interrupts: a read stuck in another app's provider. */
    private fun stuck(): String {
        while (release.count > 0) {
            try {
                release.await()
            } catch (_: InterruptedException) {
                // Ignored, as a binder or pipe read would.
            }
        }
        return "late"
    }

    @Test
    fun aLoadThatIgnoresInterruptsStillTimesOut() = runBlocking {
        val started = System.nanoTime()
        assertNull(lane.load(::stuck))
        assertTrue((System.nanoTime() - started) / NANOS_PER_MS < TIMEOUT_MS * 3, "waited for the stuck load")
    }

    @Test
    fun aStuckLoadDoesNotHoldBackLaterOnes() = runBlocking {
        assertNull(lane.load(::stuck))
        assertEquals("next", lane.load { "next" })
    }

    @Test
    fun aLoadsFailurePropagates() {
        val failure = runCatching { runBlocking { lane.load { error("undecodable") } } }.exceptionOrNull()
        assertEquals("undecodable", failure?.message)
    }

    private companion object {
        const val TIMEOUT_MS = 200L
        const val NANOS_PER_MS = 1_000_000L
    }
}
