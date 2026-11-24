package app.rimlit.model

/** A side of the screen: the one a camera cutout is on, or one a look's edge glows along (see [Look.sides]). */
enum class ScreenSide { TOP, LEFT, RIGHT, BOTTOM }

/** Every side: a look glows all round unless it chooses fewer. */
val ALL_SIDES: Set<ScreenSide> = ScreenSide.entries.toSet()

/** These sides with [side] lit or not, as asked; the last lit side stays lit, as a look always glows somewhere. */
fun Set<ScreenSide>.withSide(side: ScreenSide, lit: Boolean): Set<ScreenSide> =
    (if (lit) this + side else this - side).ifEmpty { this }
