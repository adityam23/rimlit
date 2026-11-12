package app.rimlit.ui.glow

import android.graphics.Path
import kotlin.math.min

/**
 * The shape of a glow outline in plain floats, so what effects derive from it is testable off-device: the screen
 * edge's [RoundRect], or the [CutoutRing]. An outline opens toward the side the screen lies on, inside the screen edge
 * and outside the cutout ring; effects move and grow only that way.
 */
internal sealed interface OutlineShape {
    /** Whether the open screen lies inside the outline (the screen edge) rather than outside it (the cutout ring). */
    val opensInward: Boolean

    /** How far its corners curve in from its bounds, at most. */
    val cornerRadius: Float

    /** Size of its bounds. */
    val width: Float
    val height: Float

    /**
     * [px], capped at [share] of this outline's shorter side: how an effect sizes what it draws on the outline, so a
     * small outline (the cutout ring, a preview) isn't swamped.
     */
    fun fit(px: Float, share: Float): Float = min(px, min(width, height) * share)

    /**
     * Sets [path] to this outline moved [by] (0 or more) toward its open side, exactly, traced clockwise on screen;
     * reuses the path, allocating nothing. Returns `false`, leaving the path empty, when it moves away to nothing.
     */
    fun trace(path: Path, by: Float): Boolean

    /** Length of the outline. */
    val perimeter: Float

    /**
     * Writes the outline point at arc [distance] (any real; wraps), traced clockwise on screen, into [point]: its x
     * and y, then its unit normal toward the open side, x and y.
     */
    fun pointAt(distance: Float, point: FloatArray)

    /**
     * This outline with its corners rounded enough that any point moved up to [reach] along its normal toward the
     * open side never crosses another: the line an effect bends along, however sharp the outline's own corners.
     */
    fun smoothedFor(reach: Float): OutlineShape
}
