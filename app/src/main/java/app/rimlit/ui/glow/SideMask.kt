package app.rimlit.ui.glow

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import app.rimlit.model.ALL_SIDES
import app.rimlit.model.ScreenSide
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.max

/**
 * Window pixels per mask pixel each way. The light fades smoothly everywhere (see [SideLight]), so a coarse mask drawn
 * filtered loses nothing, at a sixteenth of the memory and the work.
 */
private const val MASK_SCALE = 4f

/** A side's fade toward an unlit neighbour is at least this many line thicknesses long, however narrow its halo. */
private const val MIN_TAPER_THICKNESSES = 8f

/**
 * What a [SideMask] keeps to the lit [sides]: the screen-edge [outline], compared by identity. The cached layout keeps
 * its outlines as long as their geometry holds, and the mask takes its window and taper from the outline's layout, so
 * these two decide it. It is cached apart from the [GlowLayout], so lighting a side rebuilds only the mask, not the
 * halos.
 */
internal data class SideMaskKey(val outline: GlowOutline, val sides: Set<ScreenSide>) {
    companion object {
        /** What keeps the drawing of [outline], halo and all, to the lit [sides]; `null` when [masks] says none. */
        fun of(outline: GlowOutline, sides: Set<ScreenSide>): SideMaskKey? =
            if (masks(outline.geometry.shape, sides)) SideMaskKey(outline, sides) else null

        /**
         * Whether an outline of [shape] lit on [sides] is masked: not when every side is lit, nor for the cutout
         * ring, which sides don't touch.
         */
        fun masks(shape: OutlineShape, sides: Set<ScreenSide>): Boolean =
            shape is RoundRect && !sides.containsAll(ALL_SIDES)
    }
}

/**
 * How far a side of an outline in [layout] fades out toward an unlit one: over its halo's reach, so the halo's end is
 * as soft as its edges, and at least [MIN_TAPER_THICKNESSES] lines, so a line with no halo still fades rather than
 * stops (a line is never thinner than [app.rimlit.model.GlowSettings.Ranges]' least, so neither is a fade).
 */
internal fun sideTaper(layout: GlowLayoutKey): Float =
    max(haloReach(layout.haloStrokePx, layout.glowPx), layout.thicknessPx * MIN_TAPER_THICKNESSES)

/**
 * The mask's pixels over a [width] × [height] window: [columns] × [rows], each [scaleX] × [scaleY] window pixels, so
 * the grid spans the window exactly and each mask pixel's centre lies where [SideLight.render] samples its light.
 */
internal class MaskGrid(val width: Float, val height: Float) {
    val columns = ceil(width / MASK_SCALE).toInt().coerceAtLeast(1)
    val rows = ceil(height / MASK_SCALE).toInt().coerceAtLeast(1)
    val scaleX = width / columns
    val scaleY = height / rows
}

/**
 * The [SideLight] of [key] over its window, rendered once into an alpha-only bitmap and held as a [shader] in window
 * coordinates, which an outline's brush cuts each of its paints' colors to (see
 * [app.rimlit.ui.glow.effects.OutlineBrush]): everything a style draws, halo and all, is kept to the lit sides with no
 * layer. Sampled filtered, as the brush's paints are.
 */
internal class SideMask(key: SideMaskKey) {
    val shader: Shader

    init {
        val layout = key.outline.key
        // Only a screen edge is masked (see SideMaskKey.masks).
        val edge = key.outline.geometry.shape as RoundRect
        val grid = MaskGrid(layout.width, layout.height)
        val bitmap = createBitmap(grid.columns, grid.rows, Bitmap.Config.ALPHA_8)
        // Rows may be padded: one byte per pixel, rowBytes per row.
        val alphas = SideLight(edge, key.sides, sideTaper(layout))
            .render(grid.width, grid.height, grid.columns, grid.rows, bitmap.rowBytes)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(alphas))
        // From mask pixels to window pixels.
        shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(grid.scaleX, grid.scaleY) })
        }
    }
}
