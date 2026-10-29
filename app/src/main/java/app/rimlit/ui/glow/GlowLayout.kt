package app.rimlit.ui.glow

import app.rimlit.model.EdgeRegion

/**
 * Everything the outlines and their halos depend on. Colors, style animation, speed, brightness and duration
 * don't change them, so editing those (e.g. dragging a preview slider) reuses the built [GlowLayout].
 */
internal data class GlowLayoutKey(
    val width: Float,
    val height: Float,
    val shape: DisplayShape,
    /** Which outlines to draw; one that rings the cutout draws the screen edge instead when [shape] has none. */
    val region: EdgeRegion,
    val thicknessPx: Float,
    val cutoutGapPx: Float,
    /** Halo blur radius; 0 when the style draws no halo. */
    val bloomPx: Float,
)

/** One closed outline the glow is drawn along, and its pre-blurred halo (`null` when there is none). */
internal class GlowOutline(val geometry: GlowGeometry, val bloom: BloomMask?)

/** The outlines (one or two) and their halos for one [GlowLayoutKey]. */
internal class GlowLayout(key: GlowLayoutKey) {
    val outlines: List<GlowOutline> = geometries(key).map { geometry ->
        // A zero-radius blur is invalid, so a style without a halo gets none.
        val bloom = if (key.bloomPx > 0f) {
            BloomMask(geometry, key.width, key.height, strokePx = key.thicknessPx * 2f, blurPx = key.bloomPx)
        } else {
            null
        }
        GlowOutline(geometry, bloom)
    }

    private fun geometries(key: GlowLayoutKey): List<GlowGeometry> {
        val cutout = key.shape.cutout?.takeIf { key.region.ringsCutout }
            ?.let { cutoutGeometry(it, gapPx = key.cutoutGapPx, strokePx = key.thicknessPx) }
        val edge = if (key.region.tracesEdge || cutout == null) {
            screenEdgeGeometry(key.width, key.height, key.shape, key.thicknessPx)
        } else {
            null
        }
        return listOfNotNull(edge, cutout)
    }
}
