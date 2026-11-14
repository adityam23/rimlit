package app.rimlit.ui.glow

import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.RectF
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import app.rimlit.model.CustomCutout
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min

/**
 * The closed outline a glow is drawn along, [path], plus precomputed samples for painting colors by arc length, and
 * its [shape] in plain floats, which effects move and sample. [path] is [shape] traced clockwise unless given: the
 * cutout ring draws its exact outline (see [cutoutGeometry]). Built once per size/shape (never per frame).
 */
internal class GlowGeometry(val shape: OutlineShape, val path: Path = Path().also { shape.trace(it, by = 0f) }) {

    /** How far the corners of [path] curve in from its bounds, at most. */
    val cornerRadius: Float get() = shape.cornerRadius

    private val measure = PathMeasure(path, true)
    val length: Float = measure.length

    /** Bounds of [path]. Shared: copy it before modifying. */
    val bounds: RectF = RectF().also { path.computeBounds(it, true) }

    /** Centre that angles (and sweep gradients) are measured around. */
    val center = PointF(bounds.centerX(), bounds.centerY())

    private val pos = FloatArray(2)

    /** For each of [SAMPLES] evenly spaced points along the outline: its angle around the centre, in [0, 1). */
    val sampleAngles = FloatArray(SAMPLES) { angleAt(length * it / SAMPLES) }

    /** Angle around [center], in [0, 1), of the outline point at arc [distance] (any real; wraps). */
    fun angleAt(distance: Float): Float {
        pointAt(distance, pos)
        val angle = atan2(pos[1] - center.y, pos[0] - center.x) / (2 * PI).toFloat()
        return (angle + 1f) % 1f
    }

    /**
     * Arc distance, in [0, [length]), of the outline point at angle [turn] around [center] (see [CutoutSide.turn]): the
     * inverse of [angleAt]. Angles grow along the outline, wrapping once, so this finds the two samples [turn] lies
     * between, then bisects. For building painters, not for every frame.
     */
    fun distanceAt(turn: Float): Float {
        // How far an angle lies past [turn], in [0, 1): it wraps from near 1 to near 0 where the outline meets [turn].
        fun past(angle: Float) = ((angle - turn) % 1f + 1f) % 1f
        val sample = (0 until SAMPLES).first { past(sampleAngles[(it + 1) % SAMPLES]) < past(sampleAngles[it]) }
        var low = length * sample / SAMPLES
        var high = length * (sample + 1) / SAMPLES
        repeat(BISECTIONS) {
            val middle = (low + high) / 2f
            if (past(angleAt(middle)) > 0.5f) low = middle else high = middle
        }
        return high % length
    }

    /**
     * Writes the outline point at arc [distance] (any real; wraps) into [position] as (x, y), and its unit tangent,
     * clockwise on screen, into [tangent] unless `null`.
     */
    fun pointAt(distance: Float, position: FloatArray, tangent: FloatArray? = null) {
        measure.getPosTan((distance % length + length) % length, position, tangent)
    }

    companion object {
        /** Resolution of the arc-length → angle mapping used for gradients. */
        const val SAMPLES = 96

        /** Halvings that refine [distanceAt] within a sample's span: well under a pixel on any screen. */
        private const val BISECTIONS = 16
    }
}

/** Direction toward this side of the screen, as the turn [GlowGeometry.angleAt] measures: clockwise from the right. */
internal val CutoutSide.turn: Float
    get() = when (this) {
        CutoutSide.RIGHT -> 0f
        CutoutSide.BOTTOM -> 0.25f
        CutoutSide.LEFT -> 0.5f
        CutoutSide.TOP -> 0.75f
    }

/** Corner radius assumed where the system doesn't report the display's own (before API 31). */
private const val FALLBACK_CORNER_DP = 24f

/** Physical display shape the glow follows, in window pixels. */
internal data class DisplayShape(
    /** Top-left, top-right, bottom-right, bottom-left corner radii. */
    val cornerRadii: FloatArray,
    /** The camera cutout, or `null` when the display has none. */
    val cutout: Cutout?,
) {
    companion object {
        /**
         * Reads the real rounded corners (API 31+) and cutout from the window [view] is attached to. Corners the
         * system doesn't report are [FALLBACK_CORNER_DP].
         */
        fun of(view: View): DisplayShape {
            val insets = view.rootWindowInsets
            val fallbackCornerPx = FALLBACK_CORNER_DP * view.resources.displayMetrics.density
            val radii = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && insets != null) {
                listOf(
                    RoundedCorner.POSITION_TOP_LEFT,
                    RoundedCorner.POSITION_TOP_RIGHT,
                    RoundedCorner.POSITION_BOTTOM_RIGHT,
                    RoundedCorner.POSITION_BOTTOM_LEFT,
                ).map { insets.getRoundedCorner(it)?.radius?.toFloat() ?: fallbackCornerPx }.toFloatArray()
            } else {
                FloatArray(4) { fallbackCornerPx }
            }
            return DisplayShape(radii, view.displayCutout())
        }

        /** Scaled stand-in for in-app previews: proportional corners and a simulated punch-hole camera. */
        fun preview(width: Float, height: Float): DisplayShape {
            val corner = min(width, height) * 0.12f
            val hole = min(width, height) * 0.06f
            val cx = width / 2f
            val cy = hole * 2.2f
            val camera = pill(RectF(cx - hole, cy - hole, cx + hole, cy + hole))
            return DisplayShape(FloatArray(4) { corner }, Cutout(CutoutSide.TOP, camera.polygon()))
        }
    }

    /** This shape with all four corners rounded to [radiusPx] instead. */
    fun withCornerRadius(radiusPx: Float) = copy(cornerRadii = FloatArray(4) { radiusPx })

    /** This shape with its cutout, if it has one, drawn as [custom] instead (see [Cutout.redrawn]). */
    fun withCustomCutout(custom: CustomCutout, pxPerDp: Float, width: Float, height: Float) =
        copy(cutout = cutout?.redrawn(custom, pxPerDp, width, height))

    override fun equals(other: Any?) =
        other is DisplayShape && cornerRadii.contentEquals(other.cornerRadii) && cutout == other.cutout

    override fun hashCode() = 31 * cornerRadii.contentHashCode() + (cutout?.hashCode() ?: 0)
}

/**
 * Border following the screen's rounded corners, inset so the full stroke stays on-screen (see [RoundRect.offset]);
 * `null` when the stroke is wider than the window.
 */
internal fun screenEdgeGeometry(width: Float, height: Float, shape: DisplayShape, strokePx: Float): GlowGeometry? =
    RoundRect(0f, 0f, width, height, shape.cornerRadii).offset(by = -strokePx / 2f)?.let { GlowGeometry(it) }
