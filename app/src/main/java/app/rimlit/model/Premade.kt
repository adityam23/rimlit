package app.rimlit.model

/**
 * The ready-made looks a look starts from: one per edge style but [GlowStyle.NONE], which shows nothing without
 * particles and is reached by turning the edge off instead. A look remembers the premade it is based on
 * ([Look.basedOn]) and is custom wherever its values differ from it (see [Look.changedParts]).
 *
 * A shipped premade's values never change: every untouched look based on it would turn custom after an update.
 * Improvements ship as new premades. Their values are written out here, not taken from [Look]'s defaults, so changing
 * a default can't change a premade.
 */
enum class Premade(val style: GlowStyle) {
    BASIC(GlowStyle.BASIC),
    GLOW(GlowStyle.GLOW),
    BLADE(GlowStyle.BLADE),
    WAVE(GlowStyle.WAVE),
    GLITTER(GlowStyle.GLITTER),
    ECHO(GlowStyle.ECHO),
    LOOP(GlowStyle.LOOP),
    MIRROR(GlowStyle.MIRROR),
    BOOMERANG(GlowStyle.BOOMERANG),
    FIREWORKS(GlowStyle.FIREWORKS),
    ;

    /** This premade's values as a look named [name] under [id]. */
    fun look(id: String, name: String): Look = Look(
        id = id,
        name = name,
        basedOn = this,
        style = style,
        colorMode = ColorMode.AUTO,
        autoColors = AutoColors(source = AutoColorSource.APP_ICON, count = 1),
        customColors = GlowPalette.Colors(listOf(DEFAULT_GLOW_ARGB)),
        region = EdgeRegion.FULL_SCREEN,
        thicknessDp = 4f,
        glowWidthDp = 24f,
        speed = 1f,
        brightness = 1f,
        particles = null,
    )

    companion object {
        /**
         * The premade a look with [style] and no stored premade is based on: that style's, and [BASIC] for
         * [GlowStyle.NONE].
         */
        fun of(style: GlowStyle): Premade = entries.firstOrNull { it.style == style } ?: BASIC
    }
}

/** The parts of a look the editor shows apart, each compared with and reset to the look's premade on its own. */
enum class LookPart {
    /** The edge style and the region it is drawn in. */
    BASE,
    COLOUR,
    PARTICLES,
    TUNING,
}

/**
 * A look's settings, the editor's controls, each in its [part] and defined once by its [field], so comparing and
 * resetting can't disagree. Every value but the id, the name and the premade belongs to exactly one part.
 */
enum class LookControl(val part: LookPart, private val field: LookField<*>) {
    STYLE(LookPart.BASE, LookField(Look::style) { copy(style = it) }),
    REGION(LookPart.BASE, LookField(Look::region) { copy(region = it) }),
    COLOR_MODE(LookPart.COLOUR, LookField(Look::colorMode) { copy(colorMode = it) }),
    AUTO_SOURCE(
        LookPart.COLOUR,
        LookField({ it.autoColors.source }, shows = { it.colorMode == ColorMode.AUTO }) {
            copy(autoColors = autoColors.copy(source = it))
        },
    ),
    AUTO_COUNT(
        LookPart.COLOUR,
        LookField({ it.autoColors.count }, shows = { it.colorMode == ColorMode.AUTO }) {
            copy(autoColors = autoColors.copy(count = it))
        },
    ),
    CUSTOM_COLORS(
        LookPart.COLOUR,
        LookField(Look::customColors, shows = { it.colorMode == ColorMode.CUSTOM }) { copy(customColors = it) },
    ),
    PARTICLES(LookPart.PARTICLES, LookField(Look::particles) { copy(particles = it) }),
    THICKNESS(LookPart.TUNING, LookField(Look::thicknessDp) { copy(thicknessDp = it) }),
    GLOW_WIDTH(LookPart.TUNING, LookField(Look::glowWidthDp) { copy(glowWidthDp = it) }),
    SPEED(LookPart.TUNING, LookField(Look::speed) { copy(speed = it) }),
    BRIGHTNESS(LookPart.TUNING, LookField(Look::brightness) { copy(brightness = it) }),
    ;

    internal fun differs(look: Look, premade: Look): Boolean = field.differs(look, premade)

    internal fun copied(to: Look, from: Look): Look = field.copied(to, from)
}

/** The settings where this look differs from its premade, the editor's marks; empty while it is untouched. */
fun Look.changedControls(): Set<LookControl> {
    val premade = premadeLook()
    return LookControl.entries.filterTo(mutableSetOf()) { it.differs(this, premade) }
}

/** The parts where this look differs from its premade; empty while it is the premade untouched. */
fun Look.changedParts(): Set<LookPart> = changedControls().mapTo(mutableSetOf()) { it.part }

/** Whether this look differs from its premade anywhere. */
val Look.isCustom: Boolean get() = changedParts().isNotEmpty()

/** This look with [part] set back to its premade's. */
fun Look.reset(part: LookPart): Look {
    val premade = premadeLook()
    return LookControl.entries.filter { it.part == part }.fold(this) { look, control ->
        control.copied(to = look, from = premade)
    }
}

/** This look replaced by [premade]'s values, keeping its id and name: everything it changed is lost. */
fun Look.switchedTo(premade: Premade): Look = premade.look(id, name)

private fun Look.premadeLook(): Look = basedOn.look(id, name)

/**
 * One value of a look: [get] reads it and [set] writes it. It counts as changed only where it [shows], so colours a
 * mode doesn't use (the palette kept while on auto colours) never make a look custom; a reset sets it all the same.
 */
private class LookField<T>(
    private val get: (Look) -> T,
    private val shows: (Look) -> Boolean = { true },
    private val set: Look.(T) -> Look,
) {
    /**
     * Whether [look] shows this value other than [premade] does. A value hidden by another (the colour mode) needs
     * no check of its own: the two show it alike unless that other value differs.
     */
    fun differs(look: Look, premade: Look): Boolean = shows(look) && get(look) != get(premade)

    /** [to] with this value copied [from] another look. */
    fun copied(to: Look, from: Look): Look = to.set(get(from))
}
