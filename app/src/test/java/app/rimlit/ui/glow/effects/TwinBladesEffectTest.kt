package app.rimlit.ui.glow.effects

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TwinBladesEffectTest {

    private val length = 5_900f
    private val tail = BLADE_TAIL * length
    private val laps = listOf(0f, 0.13f, 0.5f, 0.99f, 2.71f, 1_000.4f)

    /** [distance] wrapped onto the outline, in [0, length). */
    private fun wrap(distance: Float) = clockwiseArc(0f, distance, length)

    @Test
    fun loopBladesStayHalfALapApartOnTheOutline() {
        for (lap in laps) {
            val first = loopHead(lap, blade = 0, length)
            val second = loopHead(lap, blade = 1, length)
            assertTrue(first in 0f..<length && second in 0f..<length, "lap $lap")
            assertEquals(length / 2, clockwiseArc(first, second, length), 0.5f, "lap $lap")
        }
    }

    @Test
    fun loopsFirstBladeRacesLikeBlade() {
        for (lap in laps) assertEquals(lapHead(lap, length), loopHead(lap, blade = 0, length), "lap $lap")
    }

    @Test
    fun mirrorBladesRunSymmetricallyAboutTheStart() {
        // The bottom centre of an outline traced from its left edge, and the top centre half a lap on.
        for (start in listOf(1_200f, 5_800f)) {
            val run = clockwiseArc(start, start + length / 2, length)
            assertEquals(length / 2, run, 0.5f)
            for (step in 0..100) {
                val progress = step / 100f
                val clockwise = mirrorHead(progress, start, run, tail)
                val counterclockwise = mirrorHead(progress, start, run - length, tail)
                assertEquals(clockwise - start, start - counterclockwise, 0.5f, "start $start, progress $progress")
            }
        }
    }

    @Test
    fun mirrorBladesMeetAtTheTopThenDrainIntoIt() {
        val start = 5_800f
        val meet = wrap(start + length / 2)
        val run = clockwiseArc(start, meet, length)
        val meeting = run / (run + tail)
        val clockwise = mirrorHead(meeting, start, run, tail)
        val counterclockwise = mirrorHead(meeting, start, run - length, tail)
        assertEquals(meet, wrap(clockwise), 0.5f)
        assertEquals(meet, wrap(counterclockwise), 0.5f)
        // At the end of the trip each head is a whole tail past the meeting point, so nothing is left showing.
        assertEquals(start + run + tail, mirrorHead(1f, start, run, tail), 0.5f)
        assertEquals(start + run - length - tail, mirrorHead(1f, start, run - length, tail), 0.5f)
        assertEquals(start, mirrorHead(0f, start, run, tail))
    }

    @Test
    fun mirrorBladesOnAnUnevenRingMeetTogether() {
        // An asymmetric ring: the short way round is 0.3 of the outline, the long way 0.7 — a real cutout ring need
        // not split evenly either side of the anchor. Both legs must still reach the meeting point at the same
        // moment, derived (not hardcoded) from the symmetric case's own meeting fraction.
        val start = 2_200f
        val halfLap = length / 2f
        val meetingFraction = halfLap / (halfLap + tail)
        val run = 0.3f * length
        val meet = wrap(start + run)
        val clockwise = mirrorHead(meetingFraction, start, run, tail)
        val counterclockwise = mirrorHead(meetingFraction, start, run - length, tail)
        assertEquals(meet, wrap(clockwise), 0.5f)
        assertEquals(meet, wrap(counterclockwise), 0.5f)
    }

    @Test
    fun mirrorTripsRepeatAtABladesSpeed() {
        // A trip is half a lap plus a tail, run at a blade's speed.
        val trip = 0.5f + BLADE_TAIL
        assertEquals(0f, mirrorProgress(0f))
        assertEquals(0.5f, mirrorProgress(trip / 2), 1e-5f)
        assertEquals(mirrorProgress(0.2f), mirrorProgress(0.2f + 3 * trip), 1e-4f)
    }

    @Test
    fun clockwiseArcWrapsPastThePathStart() {
        assertEquals(200f, clockwiseArc(5_800f, 100f, length), 0.5f)
        assertEquals(length - 200f, clockwiseArc(100f, 5_800f, length), 0.5f)
        assertEquals(0f, clockwiseArc(300f, 300f, length))
    }
}
