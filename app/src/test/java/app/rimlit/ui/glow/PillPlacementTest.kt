package app.rimlit.ui.glow

import androidx.compose.ui.geometry.Rect
import org.junit.Test
import kotlin.test.assertEquals

class PillPlacementTest {

    /** At one pixel per dp: 36 tall, icon 24 after 6, 8 between, 14 after the name, 6 round the camera, 8 margins. */
    private val metrics = PillMetrics.at(pxPerDp = 1f)

    private val topCentre = PillPlacement(x = null, y = 8, height = 36, gap = 8, maxWidth = 384)

    @Test
    fun withoutACutoutItSitsAtTheTopCentre() {
        assertEquals(topCentre, pillPlacement(windowWidth = 400, cutout = null, metrics))
    }

    @Test
    fun itGrowsOutOfAPunchHoleWithTheCameraBetweenIconAndName() {
        val camera = Rect(190f, 10f, 210f, 30f)
        val placement = pillPlacement(windowWidth = 400, camera, metrics)
        assertEquals(PillPlacement(x = 154, y = 2, height = 36, gap = 32, maxWidth = 238), placement)

        // The gap starts and ends one clearance either side of the camera, centred on it vertically.
        val gapStart = placement.x!! + metrics.startPadding + metrics.icon
        assertEquals(camera.left - metrics.clearance, gapStart)
        assertEquals(camera.right + metrics.clearance, gapStart + placement.gap)
        assertEquals(camera.center.y, placement.y + placement.height / 2f)
    }

    @Test
    fun itWrapsACutoutTallerThanItselfWithoutLeavingTheScreen() {
        val placement = pillPlacement(windowWidth = 400, Rect(180f, 0f, 220f, 40f), metrics)
        assertEquals(52, placement.height, "the camera plus its clearance")
        assertEquals(0, placement.y, "clamped to the top edge")
    }

    @Test
    fun aCornerCameraWithoutRoomBesideItLeavesItAtTheTopCentre() {
        assertEquals(topCentre, pillPlacement(400, Rect(10f, 10f, 30f, 30f), metrics), "no room for the icon")
        assertEquals(topCentre, pillPlacement(400, Rect(370f, 10f, 390f, 30f), metrics), "no room for the name")
    }

    @Test
    fun theNameGetsTheRestOfTheWidthUpToTheMargin() {
        val placement = pillPlacement(windowWidth = 1000, Rect(490f, 10f, 510f, 30f), metrics)
        assertEquals(1000 - 8, placement.x!! + placement.maxWidth)
    }
}
