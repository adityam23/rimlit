package app.rimlit.ui.glow

import app.rimlit.model.EdgeRegion
import app.rimlit.model.GlowSpec

/** The gap between the cutout and the ring round it. */
private const val CUTOUT_GAP_DP = 4f

/**
 * Everything the outlines and their halos depend on. Colors, style, speed, brightness, duration and the lit sides
 * (masked apart, see [SideMaskKey]) don't change them, so editing those (e.g. dragging a preview slider) reuses the
 * built [GlowLayout].
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
) {
    /** The halo's line: twice the glow's thickness, so the halo rises from beneath the whole stroke. */
    val haloStrokePx: Float get() = thicknessPx * 2f

    companion object {
        /** The layout [spec] draws in a [width] × [height] window of [shape], at [density] pixels per dp. */
        fun of(spec: GlowSpec, width: Float, height: Float, shape: DisplayShape, density: Float) = GlowLayoutKey(
            width = width,
            height = height,
            shape = shape,
            region = spec.region,
            thicknessPx = spec.thicknessDp * density,
            cutoutGapPx = CUTOUT_GAP_DP * density,
            glowPx = spec.glowWidthDp * density,
        )
    }
}

/**
 * Data an effect derives from one outline and its layout (e.g. wave samples), built on first use and kept as long as
 * the layout: see [GlowOutline.part]. Parts are compared by identity, so each is one top-level value.
 */
internal fun interface OutlinePart<T : Any> {
    fun build(outline: GlowOutline): T
}

/**
 * One closed outline the glow is drawn along, in the layout of [key]. Its pre-blurred halo is built on the first draw
 * that asks for it (`null` when the glow width is 0), so a style that draws none never blurs.
 */
internal class GlowOutline(val geometry: GlowGeometry, val key: GlowLayoutKey) {
    /**
     * Whether the open screen lies inside the outline (the screen edge) rather than outside it (the cutout ring):
     * the side an effect may move or grow toward.
     */
    val opensInward: Boolean get() = geometry.shape.opensInward

    val bloom: BloomMask? by lazy(LazyThreadSafetyMode.NONE) {
        // A zero-radius blur is invalid.
        if (key.glowPx > 0f) {
            BloomMask(geometry, key.width, key.height, strokePx = key.haloStrokePx, blurPx = key.glowPx)
        } else {
            null
        }
    }

    private val parts = HashMap<OutlinePart<*>, Any>()

    /** This outline's [part], built on first use. Main thread only. */
    fun <T : Any> part(part: OutlinePart<T>): T {
        // Each part maps only to what its own build returned.
        @Suppress("UNCHECKED_CAST")
        return parts.getOrPut(part) { part.build(this) } as T
    }
}

/** The outlines (one or two) and their halos for one [GlowLayoutKey]. */
internal class GlowLayout(key: GlowLayoutKey) {
    val outlines: List<GlowOutline> = outlines(key)

    private fun outlines(key: GlowLayoutKey): List<GlowOutline> {
        val cutout = key.shape.cutout?.takeIf { key.region.ringsCutout }?.let {
            val geometry = cutoutGeometry(it, gapPx = key.cutoutGapPx, strokePx = key.thicknessPx)
            GlowOutline(geometry, key)
        }
        val edge = if (key.region.tracesEdge || cutout == null) {
            screenEdgeGeometry(key.width, key.height, key.shape, key.thicknessPx)?.let { GlowOutline(it, key) }
        } else {
            null
        }
        return listOfNotNull(edge, cutout)
    }
}
