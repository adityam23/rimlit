package app.rimlit.ui.glow.effects

import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoomerangEffectTest {

    @Test
    fun itSweepsOneWholeLapOutThenBack() {
        assertEquals(0f, boomerangLap(0f), 1e-6f)
        assertEquals(0.5f, boomerangLap(0.5f), 1e-6f)
        assertEquals(1f, boomerangLap(0.9999f), 1e-6f)
        assertEquals(1f, boomerangLap(1f), 1e-6f)
        assertEquals(0.5f, boomerangLap(1.5f), 1e-6f)
        assertEquals(0f, boomerangLap(2f), 1e-6f)
        assertEquals(0.5f, boomerangLap(4.5f), 1e-5f, "and again, lap after lap")
    }

    @Test
    fun itNeverJumpsNeitherAtTheTurnsNorWhereTheLapWraps() {
        // The head slows to rest at each turn, so a step of the clock moves it no further than mid-sweep does.
        val step = 1e-3f
        val fastest = abs(boomerangLap(0.5f + step) - boomerangLap(0.5f))
        for (i in 0 until 6_000) {
            val laps = i * step
            val move = abs(boomerangLap(laps + step) - boomerangLap(laps))
            assertTrue(move <= fastest + 1e-5f, "jumped $move at $laps laps")
        }
    }

    @Test
    fun itEasesIntoEachTurnAndOutOfIt() {
        val step = 1e-3f
        val midSweep = abs(boomerangLap(0.5f + step) - boomerangLap(0.5f))
        for (turn in listOf(1f, 2f, 3f)) {
            val into = abs(boomerangLap(turn) - boomerangLap(turn - step))
            val outOf = abs(boomerangLap(turn + step) - boomerangLap(turn))
            assertTrue(into < midSweep / 100, "slows into the turn at $turn laps")
            assertTrue(outOf < midSweep / 100, "speeds out of the turn at $turn laps")
        }
    }

    @Test
    fun itsDirectionFlipsAtEachTurnAndMatchesItsMotion() {
        assertTrue(boomerangClockwise(0f))
        assertTrue(boomerangClockwise(0.999f))
        assertFalse(boomerangClockwise(1f))
        assertFalse(boomerangClockwise(1.999f))
        assertTrue(boomerangClockwise(2f))
        for (i in 1 until 400) {
            val laps = i / 100f
            // Right at a turn the head is at rest, too slow to tell which way it goes.
            if (abs(laps - laps.roundToInt()) < 0.02f) continue
            val moving = boomerangLap(laps + 1e-3f) - boomerangLap(laps)
            assertEquals(moving > 0f, boomerangClockwise(laps), "direction at $laps laps")
        }
    }
}
