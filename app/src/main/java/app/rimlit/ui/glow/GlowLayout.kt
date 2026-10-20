package app.rimlit.ui.glow

/**
 * Everything the outline and its halo depend on. Colors, style animation, speed, brightness and duration
 * don't change them, so editing those (e.g. dragging a preview slider) reuses the built [GlowLayout].
 */
internal data class GlowLayoutKey(
    val width: Float,
    val height: Float,
    val shape: DisplayShape,
    /** Ring the camera cutout instead of the screen edge; only set when [shape] has a cutout. */
    val ringCutout: Boolean,
    val thicknessPx: Float,
    val cutoutGapPx: Float,
    /** Halo blur radius; 0 when the style draws no halo. */
    val bloomPx: Float,
)

/** The outline and its pre-blurred halo for one [GlowLayoutKey]. */
internal class GlowLayout(key: GlowLayoutKey) {
    val geometry: GlowGeometry = if (key.ringCutout && key.shape.cutout != null) {
        cutoutGeometry(key.shape.cutout, gapPx = key.cutoutGapPx, strokePx = key.thicknessPx)
    } else {
        screenEdgeGeometry(key.width, key.height, key.shape, key.thicknessPx)
    }

    /** `null` when there is no halo (a zero-radius blur is invalid). */
    val bloom: BloomMask? = if (key.bloomPx > 0f) {
        BloomMask(geometry, key.width, key.height, strokePx = key.thicknessPx * 2f, blurPx = key.bloomPx)
    } else {
        null
    }
}

/** Holds the last built [GlowLayout] and rebuilds it only when its key changes. Main thread only. */
internal class GlowLayoutCache {
    private var key: GlowLayoutKey? = null
    private var layout: GlowLayout? = null

    fun get(key: GlowLayoutKey): GlowLayout =
        layout?.takeIf { this.key == key } ?: GlowLayout(key).also {
            this.key = key
            layout = it
        }
}
