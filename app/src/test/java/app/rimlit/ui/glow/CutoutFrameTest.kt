package app.rimlit.ui.glow

import app.rimlit.model.CustomCutout
import app.rimlit.model.CutoutShape
import org.junit.Test
import kotlin.test.assertEquals

class CutoutFrameTest {

    /** Lays [cutout] out at 2 px per dp around a camera centred at (100, 30). */
    private fun frame(cutout: CustomCutout) = cutout.frame(centreX = 100f, centreY = 30f, pxPerDp = 2f)

    @Test
    fun aCircleIsAsTallAsItIsWideAndFullyRound() {
        val frame = frame(CustomCutout(CutoutShape.CIRCLE, widthDp = 10f, heightDp = 40f, radiusDp = 1f))
        assertEquals(CutoutFrame(left = 90f, top = 20f, right = 110f, bottom = 40f, edge = 10f, end = 10f), frame)
    }

    @Test
    fun aPillTakesItsRadiusUpToHalfItsShorterSide() {
        val pill = CustomCutout(CutoutShape.PILL, widthDp = 30f, heightDp = 10f, radiusDp = 3f)
        assertEquals(CutoutFrame(left = 70f, top = 20f, right = 130f, bottom = 40f, edge = 6f, end = 6f), frame(pill))
        assertEquals(10f, frame(pill.copy(radiusDp = 40f)).end, "capped at half the height")
    }

    @Test
    fun floatingShapesMoveByBothOffsets() {
        val frame = frame(CustomCutout(CutoutShape.CIRCLE, widthDp = 10f, offsetXDp = -5f, offsetYDp = 4f))
        assertEquals(80f, frame.left)
        assertEquals(28f, frame.top)
    }

    @Test
    fun notchesHangFromTheEdgeSquareThereAndIgnoreTheVerticalOffset() {
        val notch = CustomCutout(CutoutShape.NOTCH, widthDp = 40f, heightDp = 10f, radiusDp = 4f, offsetYDp = 20f)
        assertEquals(CutoutFrame(left = 60f, top = 0f, right = 140f, bottom = 20f, edge = 0f, end = 8f), frame(notch))
        assertEquals(20f, frame(notch.copy(radiusDp = 40f)).end, "capped at the height")
    }

    @Test
    fun aTeardropHasAFullyRoundEnd() {
        val drop = frame(CustomCutout(CutoutShape.TEARDROP, widthDp = 10f, heightDp = 20f, radiusDp = 0f))
        assertEquals(CutoutFrame(left = 90f, top = 0f, right = 110f, bottom = 40f, edge = 0f, end = 10f), drop)
    }

    @Test
    fun aShallowTeardropRoundsOnlyAsFarAsItsHeight() {
        assertEquals(6f, frame(CustomCutout(CutoutShape.TEARDROP, widthDp = 20f, heightDp = 3f)).end)
    }

    @Test
    fun theHorizontalOffsetMovesHangingShapesAlongTheEdge() {
        val frame = frame(CustomCutout(CutoutShape.TEARDROP, widthDp = 10f, offsetXDp = 7f))
        assertEquals(104f, frame.left)
    }
}
