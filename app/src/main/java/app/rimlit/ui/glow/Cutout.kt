package app.rimlit.ui.glow

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.Build
import android.view.View
import app.rimlit.model.CustomCutout
import kotlin.math.min

/** Largest distance, in pixels, between a cutout's outline and the polygon it is stored as. */
private const val FLATTEN_ERROR_PX = 0.5f

/** The screen edge a camera cutout is on. */
internal enum class CutoutSide { TOP, LEFT, RIGHT, BOTTOM }

/**
 * A camera cutout in window pixels: the [side] it is on and its [outline], a closed polygon of x, y pairs. Unlike a
 * [Path], a polygon compares by value, so a layout built for a cutout is reused until the cutout really changes.
 */
internal class Cutout(val side: CutoutSide, val outline: FloatArray) {
    init {
        require(outline.size >= MIN_POLYGON_FLOATS && outline.size % 2 == 0) { "A cutout outline needs 3+ points" }
    }

    val bounds = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE).apply {
        for (i in outline.indices step 2) union(outline[i], outline[i + 1])
    }

    fun path() = Path().apply {
        moveTo(outline[0], outline[1])
        for (i in 2 until outline.size step 2) lineTo(outline[i], outline[i + 1])
        close()
    }

    /**
     * This cutout drawn as [custom] instead, at [pxPerDp], centred on this one's centre in a [width] × [height]
     * window: laid out in the frame of [side], then turned into the window.
     */
    fun redrawn(custom: CustomCutout, pxPerDp: Float, width: Float, height: Float): Cutout {
        val toWindow = side.edgeToWindow(width, height)
        val centre = floatArrayOf(bounds.centerX(), bounds.centerY())
        Matrix().also(toWindow::invert).mapPoints(centre)
        val frame = custom.frame(centreX = centre[0], centreY = centre[1], pxPerDp = pxPerDp)
        val radii = with(frame) { floatArrayOf(edge, edge, edge, edge, end, end, end, end) }
        val path = Path().apply {
            addRoundRect(RectF(frame.left, frame.top, frame.right, frame.bottom), radii, Path.Direction.CW)
            transform(toWindow)
        }
        return Cutout(side, path.polygon())
    }

    override fun equals(other: Any?) = other is Cutout && side == other.side && outline.contentEquals(other.outline)

    override fun hashCode() = 31 * side.hashCode() + outline.contentHashCode()

    private companion object {
        const val MIN_POLYGON_FLOATS = 6
    }
}

/**
 * The camera cutout of the window [this] is attached to, or `null` when the display has none. On API 31+ it is the
 * cutout's real outline, clipped to the first side that has one; before that, or when the system gives no usable
 * outline, it is that side's bounding rect, rounded into a pill. Some displays report a bounding rect much larger
 * than the camera (from the screen edge down to the hole), so the outline is preferred.
 */
internal fun View.displayCutout(): Cutout? {
    val cutout = rootWindowInsets?.displayCutout ?: return null
    val (side, rect) = listOf(
        CutoutSide.TOP to cutout.boundingRectTop,
        CutoutSide.LEFT to cutout.boundingRectLeft,
        CutoutSide.RIGHT to cutout.boundingRectRight,
        CutoutSide.BOTTOM to cutout.boundingRectBottom,
    ).firstOrNull { !it.second.isEmpty } ?: return null
    val bounds = RectF(rect)
    val outline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cutout.cutoutPath?.let { outlineWithin(it, bounds) }
    } else {
        null
    }
    return Cutout(side, (outline ?: pill(bounds)).polygon())
}

/**
 * [displayPath], in display coordinates, moved into this view's window and clipped to [sideBounds]; `null` unless
 * that leaves exactly one contour (a ring is drawn around one outline).
 */
private fun View.outlineWithin(displayPath: Path, sideBounds: RectF): Path? {
    val origin = IntArray(2).also(rootView::getLocationOnScreen)
    val path = Path(displayPath).apply {
        offset(-origin[0].toFloat(), -origin[1].toFloat())
        op(Path().apply { addRect(sideBounds, Path.Direction.CW) }, Path.Op.INTERSECT)
    }
    return path.takeIf { !it.isEmpty && !PathMeasure(it, true).nextContour() }
}

/** A rounded rect filling [bounds], its ends fully round: a circle for a square, a pill otherwise. */
internal fun pill(bounds: RectF) = Path().apply {
    val radius = min(bounds.width(), bounds.height()) / 2f
    addRoundRect(bounds, radius, radius, Path.Direction.CW)
}

/** This path flattened to a polygon of x, y pairs, within [FLATTEN_ERROR_PX]. */
internal fun Path.polygon(): FloatArray {
    val points = approximate(FLATTEN_ERROR_PX) // (fraction, x, y) per point.
    return FloatArray(points.size / 3 * 2) { i -> points[i / 2 * 3 + 1 + i % 2] }
}

/**
 * The ring around [cutout]: its outline grown by [gapPx] plus half of [strokePx] (the outline united with a stroke
 * that wide on each side), so the glow's stroke sits [gapPx] clear of the camera all the way round.
 */
internal fun cutoutGeometry(cutout: Cutout, gapPx: Float, strokePx: Float): GlowGeometry {
    val pad = gapPx + strokePx / 2f
    val hole = cutout.path()
    val grow = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * pad
        strokeJoin = Paint.Join.ROUND
    }
    val ring = Path().apply {
        grow.getFillPath(hole, this)
        op(hole, Path.Op.UNION)
    }
    val bounds = cutout.bounds
    return GlowGeometry(ring, cornerRadius = min(bounds.width(), bounds.height()) / 2f + pad)
}

/**
 * A hand-drawn cutout in its edge's frame, in pixels: x along the edge (as seen in portrait), y away from it, the
 * edge at y = 0. [edge] rounds the two corners nearer the edge, [end] the two farther ones.
 */
internal data class CutoutFrame(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val edge: Float,
    val end: Float,
)

/**
 * This cutout laid out at [pxPerDp] around the detected cutout's centre ([centreX], [centreY], in the edge's
 * frame), moved by the offsets. A shape that hangs from the edge starts at it, square there, and ignores the
 * vertical offset. Each radius is capped at what the shape can take, so the outline never overlaps itself.
 */
internal fun CustomCutout.frame(centreX: Float, centreY: Float, pxPerDp: Float): CutoutFrame {
    val width = widthDp * pxPerDp
    val height = (if (shape.usesHeight) heightDp else widthDp) * pxPerDp
    val x = centreX + offsetXDp * pxPerDp
    val radius = if (shape.usesRadius) radiusDp * pxPerDp else width / 2f
    return if (shape.hangsFromEdge) {
        val end = min(radius, min(width / 2f, height))
        CutoutFrame(x - width / 2f, 0f, x + width / 2f, height, edge = 0f, end = end)
    } else {
        val y = centreY + offsetYDp * pxPerDp
        val round = min(radius, min(width, height) / 2f)
        CutoutFrame(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f, edge = round, end = round)
    }
}

/** Maps the edge's frame (x along it as seen in portrait, y away from it) into a [width] × [height] window. */
private fun CutoutSide.edgeToWindow(width: Float, height: Float) = Matrix().apply {
    setValues(
        when (this@edgeToWindow) {
            CutoutSide.TOP -> floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

            // Turned a quarter anticlockwise: portrait's left end of the edge is at the bottom.
            CutoutSide.LEFT -> floatArrayOf(0f, 1f, 0f, -1f, 0f, height, 0f, 0f, 1f)

            CutoutSide.RIGHT -> floatArrayOf(0f, -1f, width, 1f, 0f, 0f, 0f, 0f, 1f)

            CutoutSide.BOTTOM -> floatArrayOf(-1f, 0f, width, 0f, -1f, height, 0f, 0f, 1f)
        },
    )
}
