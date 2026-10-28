package app.rimlit.ui.glow.effects

import app.rimlit.ui.glow.CutoutSide
import app.rimlit.ui.glow.GlowOutline
import app.rimlit.ui.glow.OutlinePart
import app.rimlit.ui.glow.turn

/**
 * Where the blade styles set out from and turn on an outline, as arc distances along it: [start], on its far side
 * (the bottom centre of the screen edge; the side of the cutout ring away from the screen edge it sits on), and
 * [across], straight across from it (the top centre; the ring's side toward its screen edge).
 */
internal class BladeAnchor(outline: GlowOutline) {
    private val geometry = outline.geometry
    private val near = nearSide(outline.opensInward, outline.key.shape.cutout?.side)

    val start = geometry.distanceAt((near.turn + 0.5f) % 1f)
    val across = geometry.distanceAt(near.turn)
}

/**
 * The side of the screen an outline's [BladeAnchor.across] faces: the top for the screen edge (which [opensInward]),
 * the cutout's own [cutoutSide] for the cutout ring.
 */
internal fun nearSide(opensInward: Boolean, cutoutSide: CutoutSide?): CutoutSide =
    if (opensInward) CutoutSide.TOP else checkNotNull(cutoutSide) { "A cutout ring needs its cutout" }

internal val BladeAnchors = OutlinePart(::BladeAnchor)
