package app.rimlit.ui.glow.effects

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EchoEffectTest {

    @Test
    fun aRingTravelsTheWholeReachOverItsJourney() {
        assertEquals(0f, echoDistance(0f, reach = 150f))
        assertEquals(75f, echoDistance(0.5f, reach = 150f))
        assertEquals(150f, echoDistance(1f, reach = 150f))
    }

    @Test
    fun aRingFadesAsItGoesAndIsGoneOnArrival() {
        var previous = Float.MAX_VALUE
        for (step in 0..100) {
            val alpha = echoAlpha(step / 100f)
            assertTrue(alpha < previous, "fades monotonically")
            previous = alpha
        }
        assertEquals(0f, echoAlpha(1f))
    }
}
