package app.rimlit.ui.glow

import app.rimlit.model.EdgeRegion

/**
 * Everything the outlines and their halos depend on. Colors, style, speed, brightness and duration don't change them,
 * so editing those (e.g. dragging a preview slider) reuses the built [GlowLayout].
 */
internal data class GlowLayoutKey(
    val width: Float,
    val height: Float,
    val shape: DisplayShape,
    /** Which outlines to draw; one that rings the cutout draws the screen edge instead when [shape] has none. */
    val region: EdgeRegion,
    val thicknessPx: Float,
    val cutoutGapPx: Float,
    /** Glow width: the halo's blur radius. */
    val glowPx: Float,
)

/**
 * One closed outline the glow is drawn along, and its pre-blurred halo, built on the first draw that asks for it
 * (`null` when the glow width is 0), so a style that draws none never blurs.
 */
internal class GlowOutline(val geometry: GlowGeometry, key: GlowLayoutKey) {
    val bloom: BloomMask? by lazy(LazyThreadSafetyMode.NONE) {
        // A zero-radius blur is invalid.
        if (key.glowPx > 0f) {
            BloomMask(geometry, key.width, key.height, strokePx = key.thicknessPx * 2f, blurPx = key.glowPx)
        } else {
            null
        }
    }
}

/** The outlines (one or two) and their halos for one [GlowLayoutKey]. */
internal class GlowLayout(key: GlowLayoutKey) {
    val outlines: List<GlowOutline> = geometries(key).map { GlowOutline(it, key) }

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
