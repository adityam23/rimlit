package app.rimlit.ui.glow.effects

import app.rimlit.ui.glow.CutoutSide
import app.rimlit.ui.glow.turn
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.assertEquals

class BladeAnchorTest {

    @Test
    fun theScreenEdgeMeetsAtTheTop() {
        for (side in listOf(null) + CutoutSide.entries) assertEquals(CutoutSide.TOP, nearSide(opensInward = true, side))
    }

    @Test
    fun aCutoutRingMeetsTowardTheScreenEdgeItSitsOn() {
        // A punch-hole at the top of a portrait screen, and the same screen turned each way.
        for (side in CutoutSide.entries) assertEquals(side, nearSide(opensInward = false, side))
    }

    @Test
    fun eachSideLiesThatWayFromTheCentre() {
        // On screen, y grows downward, so a turn clockwise from the right points (cos, sin).
        fun direction(side: CutoutSide): Pair<Int, Int> {
            val angle = 2 * PI * side.turn
            return cos(angle).roundToInt() to sin(angle).roundToInt()
        }
        assertEquals(0 to -1, direction(CutoutSide.TOP))
        assertEquals(1 to 0, direction(CutoutSide.RIGHT))
        assertEquals(0 to 1, direction(CutoutSide.BOTTOM))
        assertEquals(-1 to 0, direction(CutoutSide.LEFT))
    }
}
