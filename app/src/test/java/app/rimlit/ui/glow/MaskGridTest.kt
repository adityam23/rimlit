package app.rimlit.ui.glow

import org.junit.Test
import kotlin.math.ceil
import kotlin.test.assertEquals

/** The side mask's grid: quarter scale, spanning the window exactly, each pixel's centre where its light is sampled. */
class MaskGridTest {

    @Test
    fun aQuarterScaleGridSpansTheWindowExactly() {
        for ((width, height) in listOf(1080f to 2400f, 1081f to 2401f, 99.5f to 158.5f, 3f to 2f)) {
            val grid = MaskGrid(width, height)
            assertEquals(ceil(width / 4f).toInt(), grid.columns, "columns of $width")
            assertEquals(ceil(height / 4f).toInt(), grid.rows, "rows of $height")
            // The shader's matrix scales the last mask pixel's far edge onto the window's.
            assertEquals(width, grid.columns * grid.scaleX, 1e-3f)
            assertEquals(height, grid.rows * grid.scaleY, 1e-3f)
        }
    }

    @Test
    fun aMaskPixelsCentreLandsWhereItsLightIsSampled() {
        val grid = MaskGrid(1081f, 2401f)
        for (column in listOf(0, 1, grid.columns / 2, grid.columns - 1)) {
            // SideLight.render samples pixel column c at (c + 0.5) × width / columns.
            assertEquals((column + 0.5f) * grid.width / grid.columns, (column + 0.5f) * grid.scaleX, 1e-3f)
        }
        for (row in listOf(0, 1, grid.rows / 2, grid.rows - 1)) {
            assertEquals((row + 0.5f) * grid.height / grid.rows, (row + 0.5f) * grid.scaleY, 1e-3f)
        }
    }

    @Test
    fun aTinyWindowStillHasAPixel() {
        val grid = MaskGrid(0f, 0f)
        assertEquals(1, grid.columns)
        assertEquals(1, grid.rows)
    }
}
