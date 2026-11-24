package app.rimlit.ui.glow

import androidx.compose.ui.geometry.Rect
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PillPlacementTest {

    /** At one pixel per dp: 36 tall, icon 24 after 6, 8 between, 14 after the name, 6 round the camera, 8 margins. */
    private val metrics = PillMetrics.at(pxPerDp = 1f)

    private val width = 400
    private val height = 800

    private val topCentre = PillPlacement(x = 0, centred = true, y = 8, height = 36, gap = 8, maxWidth = 384)

    private fun place(cutout: Rect?, offsetX: Float = 0f, offsetY: Float = 0f, windowWidth: Int = width) =
        pillPlacement(windowWidth, height, cutout, metrics, offsetX, offsetY)

    @Test
    fun withoutACutoutItSitsAtTheTopCentre() {
        assertEquals(topCentre, place(cutout = null))
    }

    @Test
    fun itGrowsOutOfAPunchHoleWithTheCameraBetweenIconAndName() {
        val camera = Rect(190f, 10f, 210f, 30f)
        val placement = place(camera)
        assertEquals(PillPlacement(x = 154, centred = false, y = 2, height = 36, gap = 32, maxWidth = 238), placement)

        // The gap starts and ends one clearance either side of the camera, centred on it vertically.
        val gapStart = placement.x + metrics.startPadding + metrics.icon
        assertEquals(camera.left - metrics.clearance, gapStart)
        assertEquals(camera.right + metrics.clearance, gapStart + placement.gap)
        assertEquals(camera.center.y, placement.y + placement.height / 2f)
    }

    @Test
    fun itWrapsAPixel6asPunchHole() {
        // The Pixel 6a: 1080 × 2400 at 2.225 px per dp, its camera's real outline (not the display's bounding rect).
        val pxPerDp = 2.225f
        val camera = Rect(507f, 33f, 573f, 99f)
        val placement = pillPlacement(1080, 2400, camera, PillMetrics.at(pxPerDp))
        assertEquals(PillPlacement(x = 427, centred = false, y = 20, height = 93, gap = 93, maxWidth = 635), placement)
        val gapStart = placement.x + (6 + 24) * pxPerDp
        assertTrue(gapStart <= camera.left && gapStart + placement.gap >= camera.right, "the camera sits in the gap")
    }

    @Test
    fun itWrapsACutoutTallerThanItselfWithoutLeavingTheScreen() {
        val placement = place(Rect(180f, 0f, 220f, 40f))
        assertEquals(52, placement.height, "the camera plus its clearance")
        assertEquals(0, placement.y, "clamped to the top edge")
    }

    @Test
    fun aCornerCameraNarrowsTheTopCentrePillToStayClearOfIt() {
        // The room between the centre and the camera's clearance, twice over: 2 × (200 - 36).
        val narrowed = topCentre.copy(maxWidth = 328)
        assertEquals(narrowed, place(Rect(10f, 10f, 30f, 30f)), "no room for the icon")
        assertEquals(narrowed, place(Rect(370f, 10f, 390f, 30f)), "no room for the name")
    }

    @Test
    fun aWideNotchTheIslandCantFitPutsThePillBelowIt() {
        assertEquals(topCentre.copy(y = 36), place(Rect(60f, 0f, 340f, 30f)), "the notch's bottom plus clearance")
    }

    @Test
    fun aCentredPunchHoleTheIslandCantFitPutsThePillBelowIt() {
        val placement = place(Rect(70f, 10f, 90f, 30f), windowWidth = 160)
        assertEquals(PillPlacement(x = 0, centred = true, y = 36, height = 36, gap = 8, maxWidth = 144), placement)
    }

    @Test
    fun theNameGetsTheRestOfTheWidthUpToTheMargin() {
        val placement = place(Rect(490f, 10f, 510f, 30f), windowWidth = 1000)
        assertEquals(1000 - 8, placement.x + placement.maxWidth)
    }

    @Test
    fun theIconAndNameNeverSitOverTheCamera() {
        val sizes = listOf(8f, 20f, 40f, 120f, 280f)
        val tops = listOf(0f, 10f, 30f)
        val lefts = (0..width step 10).map(Int::toFloat)
        for (size in sizes) for (top in tops) for (left in lefts) {
            val camera = Rect(left, top, left + size, top + size.coerceAtMost(60f))
            val clear = camera.inflate(metrics.clearance)
            val placement = place(camera)
            for (part in contentOf(placement)) {
                assertNull(
                    part.intersect(clear).takeIf { it.width > 1 && it.height > 1 },
                    "$placement over $camera",
                )
            }
        }
    }

    @Test
    fun theUsersOffsetsMoveTheTopCentrePill() {
        assertEquals(topCentre.copy(x = 50, y = 28, maxWidth = 284), place(null, offsetX = 50f, offsetY = 20f))
        assertEquals(topCentre.copy(x = -50, maxWidth = 284), place(null, offsetX = -50f))
    }

    @Test
    fun theTopCentrePillStaysOnScreenHoweverFarItIsMoved() {
        // Its centre may move (400 - 2 × 8 - 100) / 2 = 142 either way, where it is the narrowest pill.
        assertEquals(topCentre.copy(x = 142, maxWidth = 100), place(null, offsetX = 500f))
        assertEquals(topCentre.copy(x = -142, maxWidth = 100), place(null, offsetX = -500f))
        assertEquals(topCentre.copy(y = 0), place(null, offsetY = -100f))
        assertEquals(topCentre.copy(y = height - 36), place(null, offsetY = 5_000f))
    }

    @Test
    fun theUsersOffsetsMoveTheIslandAndItStaysOnScreen() {
        val camera = Rect(190f, 10f, 210f, 30f)
        val island = PillPlacement(x = 154, centred = false, y = 2, height = 36, gap = 32, maxWidth = 238)
        assertEquals(island.copy(x = 174, y = 12, maxWidth = 218), place(camera, offsetX = 20f, offsetY = 10f))
        assertEquals(island.copy(x = 8, maxWidth = 384), place(camera, offsetX = -1_000f), "kept to the left margin")
        // The icon, its gap and the narrowest name: 6 + 24 + 32 + 48 + 14 = 124 inside the right margin.
        assertEquals(island.copy(x = 268, maxWidth = 124), place(camera, offsetX = 1_000f), "kept to the right one")
        assertEquals(island.copy(y = 0), place(camera, offsetY = -100f))
    }

    /**
     * Where [placement] draws its icon and name, in window pixels, at its widest: an island's gap is left out, since
     * the camera is meant to sit there.
     */
    private fun contentOf(placement: PillPlacement): List<Rect> {
        val top = placement.y.toFloat()
        val bottom = top + placement.height
        if (placement.centred) {
            val left = width / 2f + placement.x - placement.maxWidth / 2f
            return listOf(Rect(left, top, left + placement.maxWidth, bottom))
        }
        val gapStart = placement.x + metrics.startPadding + metrics.icon
        return listOf(
            Rect(placement.x.toFloat(), top, gapStart, bottom),
            Rect(gapStart + placement.gap, top, (placement.x + placement.maxWidth).toFloat(), bottom),
        )
    }
}
