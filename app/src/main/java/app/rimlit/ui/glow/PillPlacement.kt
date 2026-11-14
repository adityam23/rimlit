package app.rimlit.ui.glow

import androidx.compose.ui.geometry.Rect
import kotlin.math.max
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
 * Where the pill's window sits, in window pixels: from the top-left corner at [x], or centred when [x] is `null`,
 * [y] from the top, [height] tall and at most [maxWidth] wide, with [gap] between the icon and the name.
 */
internal data class PillPlacement(val x: Int?, val y: Int, val height: Int, val gap: Int, val maxWidth: Int)

/**
 * Places the pill in a window [windowWidth] wide. Around a camera on the top edge ([cutout]: the bounds of its real
 * outline, not the display's bounding rect, which can run from the edge down to the hole) it grows out of it, island
 * style: the camera sits in the gap between the icon (before it) and the name (after it), centred in the pill's
 * height. Without one, or when the icon or a [PillMetrics.minLabel]-wide name wouldn't fit beside it (a corner
 * camera), it sits at the top centre.
 */
internal fun pillPlacement(windowWidth: Int, cutout: Rect?, metrics: PillMetrics): PillPlacement =
    cutout?.let { island(windowWidth, it, metrics) } ?: with(metrics) {
        PillPlacement(
            x = null,
            y = margin.roundToInt(),
            height = height.roundToInt(),
            gap = spacing.roundToInt(),
            maxWidth = (windowWidth - 2 * margin).roundToInt(),
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
        y = max(0f, cutout.center.y - height / 2).roundToInt(),
        height = height.roundToInt(),
        gap = gap.roundToInt(),
        maxWidth = maxWidth.roundToInt(),
    )
}
