package app.rimlit.ui.glow

import app.rimlit.model.ALL_SIDES
import app.rimlit.model.GlowSettings
import app.rimlit.model.ScreenSide.LEFT
import app.rimlit.model.ScreenSide.TOP
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The side mask is keyed apart from the layout: lighting a side rebuilds the mask alone, never the halos. It masks only
 * a screen edge with some side unlit, and fades a side over the layout's taper.
 */
class SideMaskKeyTest {

    private val display = DisplayShape(floatArrayOf(100f, 100f, 60f, 60f), cutout = null)
    private val spec = GlowSettings().previewSpec()
    private val edge = RoundRect(6f, 6f, 1074f, 1914f, floatArrayOf(94f, 94f, 54f, 54f))

    private fun layout(thicknessDp: Float = spec.thicknessDp, glowWidthDp: Float = spec.glowWidthDp) =
        GlowLayoutKey.of(spec.copy(thicknessDp = thicknessDp, glowWidthDp = glowWidthDp), 1080f, 1920f, display, 3f)

    @Test
    fun theLayoutDoesNotDependOnTheSides() {
        val top = spec.copy(sides = setOf(TOP))
        val topAndLeft = spec.copy(sides = setOf(TOP, LEFT))
        assertEquals(
            GlowLayoutKey.of(top, 1080f, 1920f, display, 3f),
            GlowLayoutKey.of(topAndLeft, 1080f, 1920f, display, 3f),
        )
    }

    @Test
    fun aScreenEdgeWithASideUnlitIsMasked() {
        assertTrue(SideMaskKey.masks(edge, setOf(TOP)))
        assertTrue(SideMaskKey.masks(edge, ALL_SIDES - LEFT))
    }

    @Test
    fun allFourSidesNeedNoMask() {
        assertFalse(SideMaskKey.masks(edge, ALL_SIDES))
    }

    @Test
    fun theCutoutRingIsNeverMasked() {
        val ring = CutoutRing(floatArrayOf(500f, 20f, 580f, 20f, 580f, 100f, 500f, 100f), radius = 10f)
        assertFalse(SideMaskKey.masks(ring, setOf(TOP)))
    }

    @Test
    fun aSideFadesOverItsHalosReachAndNoLessThanEightLines() {
        // No halo: eight lines of 2 dp at 3 px per dp.
        assertEquals(48f, sideTaper(layout(thicknessDp = 2f, glowWidthDp = 0f)))
        // A wide halo reaches farther: half its stroke of two 6 px lines plus twice its 120 px blur.
        assertEquals(6f + 240f, sideTaper(layout(thicknessDp = 2f, glowWidthDp = 40f)))
    }
}
