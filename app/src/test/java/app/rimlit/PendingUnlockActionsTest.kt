package app.rimlit

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingUnlockActionsTest {

    @Test
    fun twoLaunchesEachRunOnlyTheirOwnAction() {
        val pending = PendingUnlockActions()
        var ranA = false
        var ranB = false
        pending.add("a", { ranA = true })
        pending.add("b", { ranB = true })

        // "b" started first (a quick double tap before "a"'s activity exists): its own id still finds its own action.
        pending.take("b")?.invoke()
        pending.take("a")?.invoke()

        assertEquals(true, ranA)
        assertEquals(true, ranB)
    }

    @Test
    fun aConsumedIdIsGone() {
        val pending = PendingUnlockActions()
        pending.add("a", {})

        assertEquals(Unit, pending.take("a")?.invoke())
        assertNull(pending.take("a"))
    }

    @Test
    fun aDroppedIdIsGone() {
        val pending = PendingUnlockActions()
        pending.add("a", {})

        // onDestroy's "drop its own unrun action": taking it without running it still removes it.
        pending.take("a")
        assertNull(pending.take("a"))
    }

    @Test
    fun anUnknownIdHasNoAction() {
        val pending = PendingUnlockActions()
        assertNull(pending.take("missing"))
    }

    @Test
    fun theBoundHoldsByDroppingTheOldestStillPendingId() {
        val pending = PendingUnlockActions(limit = 2)
        pending.add("a", {})
        pending.add("b", {})
        pending.add("c", {}) // over the limit: "a" (oldest, never taken) is dropped first.

        assertNull(pending.take("a"))
        assertEquals(Unit, pending.take("b")?.invoke())
        assertEquals(Unit, pending.take("c")?.invoke())
    }

    @Test
    fun takenIdsDoNotCountTowardsTheBound() {
        val pending = PendingUnlockActions(limit = 2)
        pending.add("a", {})
        pending.take("a") // already consumed, so it shouldn't be the one evicted or needed to make room.
        pending.add("b", {})
        pending.add("c", {})

        assertEquals(Unit, pending.take("b")?.invoke())
        assertEquals(Unit, pending.take("c")?.invoke())
    }
}
