package app.rimlit.model

/**
 * The shape of a camera cutout drawn by hand. One that [hangsFromEdge] reaches in from the screen edge, so it only
 * moves along it; the others float around the camera. [usesHeight] and [usesRadius] say which sizes it reads.
 */
enum class CutoutShape(val hangsFromEdge: Boolean, val usesHeight: Boolean, val usesRadius: Boolean) {
    /** A round punch-hole; its width is its diameter. */
    CIRCLE(hangsFromEdge = false, usesHeight = false, usesRadius = false),

    /** An elongated punch-hole, e.g. two cameras side by side. */
    PILL(hangsFromEdge = false, usesHeight = true, usesRadius = true),

    /** A notch with a fully round end. */
    TEARDROP(hangsFromEdge = true, usesHeight = true, usesRadius = false),

    /** A notch with rounded corners at its end. */
    NOTCH(hangsFromEdge = true, usesHeight = true, usesRadius = true),
}

/**
 * A camera cutout drawn by hand, for displays that report theirs wrongly. Sizes are in dp, in the frame of the
 * screen edge the cutout is on, seen in portrait: width along the edge, height away from it. The offsets move it
 * from the detected cutout's centre; [offsetYDp] applies only to shapes that don't hang from the edge.
 */
data class CustomCutout(
    val shape: CutoutShape = CutoutShape.CIRCLE,
    val widthDp: Float = 12f,
    val heightDp: Float = 12f,
    /** Corner radius, for shapes that [CutoutShape.usesRadius]; capped when drawn at what the shape can take. */
    val radiusDp: Float = 6f,
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
)
