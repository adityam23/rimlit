package app.rimlit.ui.glow

import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min

/**
 * The closed outline a glow is drawn along, plus precomputed samples for painting colors by
 * arc length. Built once per size/shape (never per frame).
 */
internal class GlowGeometry(val path: Path) {

    val measure = PathMeasure(path, true)
    val length: Float = measure.length

    /** For each of [SAMPLES] evenly spaced points along the outline: its angle around the centre, in [0, 1). */
    val sampleAngles = FloatArray(SAMPLES)

    init {
        val bounds = RectF().also { path.computeBounds(it, true) }
        val pos = FloatArray(2)
        for (i in 0 until SAMPLES) {
            measure.getPosTan(length * i / SAMPLES, pos, null)
            val angle = atan2(pos[1] - bounds.centerY(), pos[0] - bounds.centerX()) / (2 * PI).toFloat()
            sampleAngles[i] = (angle + 1f) % 1f
        }
    }

    /** Sample indices ordered by angle, as a sweep gradient needs monotonic positions. */
    val angleOrder: IntArray = (0 until SAMPLES).sortedBy { sampleAngles[it] }.toIntArray()

    companion object {
        /** Resolution of the arc-length → angle mapping used for gradients. */
        const val SAMPLES = 96
    }
}

/** Physical display shape the glow follows, in window pixels. */
internal data class DisplayShape(
    /** Top-left, top-right, bottom-right, bottom-left corner radii. */
    val cornerRadii: FloatArray,
    /** Camera cutout bounds, or `null` when the display has none. */
    val cutout: RectF?,
) {
    companion object {
        /** Reads the real rounded corners (API 31+) and cutout from the window [view] is attached to. */
        fun of(view: View, fallbackCornerPx: Float): DisplayShape {
            val insets = view.rootWindowInsets
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
            val cutout = insets?.displayCutout?.let { c ->
                listOf(c.boundingRectTop, c.boundingRectLeft, c.boundingRectRight, c.boundingRectBottom)
                    .firstOrNull { !it.isEmpty }
                    ?.let(::RectF)
            }
            return DisplayShape(radii, cutout)
        }

        /** Scaled stand-in for in-app previews: proportional corners and a simulated punch-hole camera. */
        fun preview(width: Float, height: Float): DisplayShape {
            val corner = min(width, height) * 0.12f
            val hole = min(width, height) * 0.06f
            val cx = width / 2f
            val cy = hole * 2.2f
            return DisplayShape(FloatArray(4) { corner }, RectF(cx - hole, cy - hole, cx + hole, cy + hole))
        }
    }

    override fun equals(other: Any?) =
        other is DisplayShape && cornerRadii.contentEquals(other.cornerRadii) && cutout == other.cutout

    override fun hashCode() = 31 * cornerRadii.contentHashCode() + (cutout?.hashCode() ?: 0)
}

/** Border following the screen's rounded corners, inset so the full stroke stays on-screen. */
internal fun screenEdgeGeometry(width: Float, height: Float, shape: DisplayShape, strokePx: Float): GlowGeometry {
    val inset = strokePx / 2f
    val radii = FloatArray(8) { i -> (shape.cornerRadii[i / 2] - inset).coerceAtLeast(0f) }
    val path = Path().apply {
        addRoundRect(RectF(inset, inset, width - inset, height - inset), radii, Path.Direction.CW)
    }
    return GlowGeometry(path)
}

/** Pill/ring hugging the camera cutout (a circle for punch-holes, a pill for notches). */
internal fun cutoutGeometry(cutout: RectF, gapPx: Float, strokePx: Float): GlowGeometry {
    val pad = gapPx + strokePx / 2f
    val rect = RectF(cutout.left - pad, cutout.top - pad, cutout.right + pad, cutout.bottom + pad)
    val radius = min(rect.width(), rect.height()) / 2f
    val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
    return GlowGeometry(path)
}
