package app.rimlit.ui.glow.effects

import app.rimlit.ui.glow.CutoutRing
import app.rimlit.ui.glow.RoundRect
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaveEffectTest {

    private val length = 5_900f
    private val amplitude = 22f

    @Test
    fun theWaveJoinsUpAcrossThePathStart() {
        val waves = waveCount(length, wavelength = 350f)
        for (phase in listOf(0f, 0.13f, 0.5f, 2.71f, 1_000.4f)) {
            val start = waveOffset(0f, length, waves, phase, amplitude)
            val end = waveOffset(length, length, waves, phase, amplitude)
            assertEquals(start, end, 1e-2f, "phase $phase")
            // Just either side of the start, too: no step in the line.
            val before = waveOffset(length - 0.5f, length, waves, phase, amplitude)
            val after = waveOffset(0.5f, length, waves, phase, amplitude)
            assertTrue(abs(before - after) < amplitude * 0.1f, "phase $phase")
        }
    }

    @Test
    fun theWaveStaysOnTheOpenSideWithinTwoAmplitudes() {
        val waves = waveCount(length, wavelength = 350f)
        for (step in 0..1_000) {
            val offset = waveOffset(length * step / 1_000, length, waves, phase = 0.3f, amplitude)
            assertTrue(offset in 0f..2 * amplitude + 1e-3f, "offset $offset")
        }
    }

    @Test
    fun anOutlineHoldsAWholeNumberOfWavesAtLeastTheMinimum() {
        assertEquals(17, waveCount(length, wavelength = 350f))
        assertEquals(4, waveCount(300f, wavelength = 350f), "a small ring still ripples")
        assertEquals(300, waveCount(300f, wavelength = 0f), "a zero wavelength counts as 1 px, not a division by zero")
    }

    @Test
    fun theWaveNeverLoopsRoundASquareCorner() {
        // A screen edge with square corners and the largest wave: it swings up to 2 × 43 px in from the edge.
        val square = RoundRect(0f, 0f, 400f, 700f, FloatArray(4) { 0f })
        val track = WaveTrack(square, amplitude = 43f)
        for (phase in listOf(0f, 0.125f, 0.25f, 0.5f, 0.75f, 0.9f)) {
            assertSimple(bentPoints(track, phase), "phase $phase")
        }
    }

    @Test
    fun theWaveRipplesOutwardRoundTheCutoutRing() {
        val ring = CutoutRing(cutoutPolygon(RoundRect(150f, 20f, 250f, 120f, FloatArray(4) { 50f })), radius = 6f)
        val track = WaveTrack(ring, amplitude = 4f)
        for (i in 0 until track.count) {
            // Outward: away from the camera's centre.
            val out = (track.x[i] - 200f) * track.normalX[i] + (track.y[i] - 70f) * track.normalY[i]
            assertTrue(out > 0f, "point $i faces out")
        }
        for (phase in listOf(0f, 0.3f, 0.6f)) assertSimple(bentPoints(track, phase), "phase $phase")
    }

    @Test
    fun theWaveNeverLoopsRoundATeardropOrANotch() {
        // Both hang from the top edge, square there, rounded at their end (as drawn by hand).
        val teardrop = RoundRect(90f, 0f, 110f, 40f, floatArrayOf(0f, 0f, 10f, 10f))
        val notch = RoundRect(60f, 0f, 140f, 20f, floatArrayOf(0f, 0f, 8f, 8f))
        for (cutout in listOf(teardrop, notch)) {
            val track = WaveTrack(CutoutRing(cutoutPolygon(cutout), radius = 6f), amplitude = 8f)
            for (phase in listOf(0f, 0.2f, 0.45f, 0.7f)) assertSimple(bentPoints(track, phase), "phase $phase")
        }
    }

    @Test
    fun theWaveNeverLoopsRoundANotchWithFlaredShoulders() {
        // A real notch's outline: shoulders flaring out along the edge, curving in to a narrower notch.
        val flared = floatArrayOf(0f, 0f, 100f, 0f, 90f, 2f, 82f, 8f, 78f, 30f, 22f, 30f, 18f, 8f, 10f, 2f)
        val track = WaveTrack(CutoutRing(flared, radius = 6f), amplitude = 8f)
        for (phase in listOf(0f, 0.2f, 0.45f, 0.7f)) assertSimple(bentPoints(track, phase), "phase $phase")
    }

    /** [shape] flattened to a polygon, as a cutout outline is stored: a point every 2 px. */
    private fun cutoutPolygon(shape: RoundRect): FloatArray {
        val count = (shape.perimeter / 2f).toInt()
        val point = FloatArray(4)
        val polygon = FloatArray(2 * count)
        for (i in 0 until count) {
            shape.pointAt(shape.perimeter * i / count, point)
            polygon[2 * i] = point[0]
            polygon[2 * i + 1] = point[1]
        }
        return polygon
    }

    private fun bentPoints(track: WaveTrack, phase: Float): FloatArray {
        val points = FloatArray(2 * track.count)
        track.bend(phase) { i, x, y ->
            points[2 * i] = x
            points[2 * i + 1] = y
        }
        return points
    }

    /** Fails unless the closed polyline through [points] (x, y pairs) never crosses itself. */
    private fun assertSimple(points: FloatArray, message: String) {
        val n = points.size / 2
        for (i in 0 until n) {
            for (j in i + 2 until n) {
                if (i == 0 && j == n - 1) continue // Neighbours across the start.
                assertFalse(crosses(points, i, (i + 1) % n, j, (j + 1) % n), "$message: segments $i and $j cross")
            }
        }
    }

    /** Whether segment [a]–[b] properly crosses segment [c]–[d] (indices of points). */
    private fun crosses(points: FloatArray, a: Int, b: Int, c: Int, d: Int): Boolean {
        fun side(p: Int, q: Int, r: Int): Double {
            val px = points[2 * p].toDouble()
            val py = points[2 * p + 1].toDouble()
            return (points[2 * q] - px) * (points[2 * r + 1] - py) - (points[2 * q + 1] - py) * (points[2 * r] - px)
        }
        return side(a, b, c) * side(a, b, d) < 0 && side(c, d, a) * side(c, d, b) < 0
    }
}
