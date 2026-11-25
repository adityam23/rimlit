package app.rimlit.ui.glow

import android.view.View
import androidx.compose.ui.geometry.Rect
import app.rimlit.model.GlowSettings
import app.rimlit.model.ScreenSide
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The pill's fixed sizes, in pixels at one density. Build with [PillMetrics.at]. */
internal data class PillMetrics(
    /** Height of the pill without a cutout to wrap. */
    val height: Float,
    /** Before the icon. */
    val startPadding: Float,
    val icon: Float,
    /** Between the icon and the name, without a cutout between them. */
    val spacing: Float,
    /** After the name. */
    val endPadding: Float,
    /** Kept clear of the camera all round. */
    val clearance: Float,
    /** Kept clear of the window's edges. */
    val margin: Float,
    /** The narrowest the name may get before the pill leaves the cutout for the top centre. */
    val minLabel: Float,
) {
    /** The narrowest pill without a cutout to wrap: its icon and the narrowest name. */
    val minWidth: Float get() = startPadding + icon + spacing + minLabel + endPadding

    companion object {
        fun at(pxPerDp: Float) = PillMetrics(
            height = 36f * pxPerDp,
            startPadding = 6f * pxPerDp,
            icon = 24f * pxPerDp,
            spacing = 8f * pxPerDp,
            endPadding = 14f * pxPerDp,
            clearance = 6f * pxPerDp,
            margin = 8f * pxPerDp,
            minLabel = 48f * pxPerDp,
        )
    }
}

/**
 * Where the pill's window sits, in window pixels: from the left edge at [x], or, when [centred], [x] from the centre;
 * [y] from the top, [height] tall and at most [maxWidth] wide, with [gap] between the icon and the name. [x] and
 * [centred] mean what a window's x and its gravity do, left or centred horizontally.
 */
internal data class PillPlacement(
    val x: Int,
    val centred: Boolean,
    val y: Int,
    val height: Int,
    val gap: Int,
    val maxWidth: Int,
)

/**
 * Places the pill in a window [windowWidth] by [windowHeight], then moves it by [offsetX] and [offsetY] (the user's,
 * in pixels), kept on screen. Around a camera on the top edge ([cutout]: the bounds of its real outline, not the
 * display's bounding rect, which can run from the edge down to the hole) it grows out of it, island style: the camera
 * sits in the gap between the icon (before it) and the name (after it), centred in the pill's height. Without one, or
 * when the icon or a [PillMetrics.minLabel]-wide name wouldn't fit beside it (a corner camera, a wide notch), it sits
 * at the top centre, never over the camera: narrowed to stay clear of a camera to the side, or below one it can't.
 */
internal fun pillPlacement(
    windowWidth: Int,
    windowHeight: Int,
    cutout: Rect?,
    metrics: PillMetrics,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
): PillPlacement {
    val placed = cutout?.let { island(windowWidth, it, metrics) } ?: topCentre(windowWidth, cutout, metrics)
    return placed.moved(offsetX, offsetY, windowWidth, windowHeight, metrics)
}

/**
 * Where the pill sits in this view's window, which fills the screen, as [settings] place it: around the camera's real
 * outline when it is on the top edge (some displays report a bounding rect from the edge down to the hole), or around
 * the cutout the user drew by hand, as the glow's own ring does; then moved by the user's offsets. The one placement
 * of the real pill and its preview.
 */
internal fun View.pillPlacement(settings: GlowSettings?, metrics: PillMetrics): PillPlacement {
    val density = resources.displayMetrics.density
    val custom = settings?.customCutout
    val detected = displayCutout()
    val cutout = (custom?.let { detected?.redrawn(it, density, width.toFloat(), height.toFloat()) } ?: detected)
        ?.takeIf { it.side == ScreenSide.TOP }?.bounds
        ?.let { Rect(it.left, it.top, it.right, it.bottom) }
    return pillPlacement(
        windowWidth = width,
        windowHeight = height,
        cutout = cutout,
        metrics = metrics,
        offsetX = (settings?.pillOffsetXDp ?: 0f) * density,
        offsetY = (settings?.pillOffsetYDp ?: 0f) * density,
    )
}

/** The pill around [cutout], or `null` if it doesn't fit there. */
private fun island(windowWidth: Int, cutout: Rect, metrics: PillMetrics): PillPlacement? = with(metrics) {
    val leading = startPadding + icon
    val gap = cutout.width + 2 * clearance
    val x = cutout.left - clearance - leading
    val maxWidth = windowWidth - margin - x
    if (x < margin || maxWidth - leading - gap - endPadding < minLabel) return null
    val height = max(height, cutout.height + 2 * clearance)
    PillPlacement(
        x = x.roundToInt(),
        centred = false,
        y = max(0f, cutout.center.y - height / 2).roundToInt(),
        height = height.roundToInt(),
        gap = gap.roundToInt(),
        maxWidth = maxWidth.roundToInt(),
    )
}

/**
 * The pill at the top centre, kept clear of [cutout] and its clearance: as wide as the window allows when the camera
 * is out of its way; narrowed to the room between the centre and the camera when the camera is to one side and the
 * narrowest pill still fits there; else just below the camera.
 */
private fun topCentre(windowWidth: Int, cutout: Rect?, metrics: PillMetrics): PillPlacement = with(metrics) {
    val full = windowWidth - 2 * margin
    val clear = cutout?.inflate(clearance)
    val centre = windowWidth / 2f
    val (y, maxWidth) = when {
        clear == null || clear.bottom <= margin || clear.top >= margin + height -> margin to full

        // The camera is to one side of the centre: the pill may be twice as wide as the room from the centre to it.
        clear.left >= centre || clear.right <= centre -> {
            val room = 2 * min(abs(clear.left - centre), abs(clear.right - centre))
            if (room >= minWidth) margin to min(full, room) else clear.bottom to full
        }

        else -> clear.bottom to full
    }
    PillPlacement(
        x = 0,
        centred = true,
        y = y.roundToInt(),
        height = height.roundToInt(),
        gap = spacing.roundToInt(),
        maxWidth = maxWidth.roundToInt(),
    )
}

/**
 * This placement moved by [dx] and [dy], kept on screen with room for the narrowest pill: the island keeps its icon
 * and the narrowest name inside the margins, the centred pill narrows as it nears an edge, and neither leaves the top
 * or the bottom.
 */
private fun PillPlacement.moved(
    dx: Float,
    dy: Float,
    windowWidth: Int,
    windowHeight: Int,
    metrics: PillMetrics,
): PillPlacement {
    val movedY = (y + dy).coerceIn(0f, max(0f, (windowHeight - height).toFloat())).roundToInt()
    val margin = metrics.margin
    return if (centred) {
        val reach = max(0f, (windowWidth - 2 * margin - metrics.minWidth) / 2)
        val movedX = dx.coerceIn(-reach, reach)
        val width = min(maxWidth.toFloat(), windowWidth - 2 * margin - 2 * abs(movedX))
        copy(x = movedX.roundToInt(), y = movedY, maxWidth = width.roundToInt())
    } else {
        val narrowest = metrics.startPadding + metrics.icon + gap + metrics.minLabel + metrics.endPadding
        val movedX = (x + dx).coerceIn(margin, max(margin, windowWidth - margin - narrowest))
        copy(x = movedX.roundToInt(), y = movedY, maxWidth = (windowWidth - margin - movedX).roundToInt())
    }
}
