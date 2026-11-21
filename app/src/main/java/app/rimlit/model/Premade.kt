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
 * A look's settings, each in its [part], compared with the premade's by [value]; only while the look [shows] it, so
 * colours a mode doesn't use (the palette kept while on auto colours) never make a look custom.
 */
enum class LookControl(
    val part: LookPart,
    private val value: (Look) -> Any?,
    private val shows: (Look) -> Boolean = { true },
) {
    STYLE(LookPart.BASE, { it.style }),
    REGION(LookPart.BASE, { it.region }),
    COLOR_MODE(LookPart.COLOUR, { it.colorMode }),
    AUTO_SOURCE(LookPart.COLOUR, { it.autoColors.source }, { it.colorMode == ColorMode.AUTO }),
    AUTO_COUNT(LookPart.COLOUR, { it.autoColors.count }, { it.colorMode == ColorMode.AUTO }),
    CUSTOM_COLORS(LookPart.COLOUR, { it.customColors }, { it.colorMode == ColorMode.CUSTOM }),
    PARTICLES(LookPart.PARTICLES, { it.particles }),
    THICKNESS(LookPart.TUNING, { it.thicknessDp }),
    GLOW_WIDTH(LookPart.TUNING, { it.glowWidthDp }),
    SPEED(LookPart.TUNING, { it.speed }),
    BRIGHTNESS(LookPart.TUNING, { it.brightness }),
    ;

    internal fun differs(look: Look, premade: Look) = shows(look) && value(look) != value(premade)
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
    return when (part) {
        LookPart.BASE -> copy(style = premade.style, region = premade.region)

        LookPart.COLOUR -> copy(
            colorMode = premade.colorMode,
            autoColors = premade.autoColors,
            customColors = premade.customColors,
        )

        LookPart.PARTICLES -> copy(particles = premade.particles)

        LookPart.TUNING -> copy(
            thicknessDp = premade.thicknessDp,
            glowWidthDp = premade.glowWidthDp,
            speed = premade.speed,
            brightness = premade.brightness,
        )
    }
}

/** This look replaced by [premade]'s values, keeping its id and name: everything it changed is lost. */
fun Look.switchedTo(premade: Premade): Look = premade.look(id, name)

private fun Look.premadeLook(): Look = basedOn.look(id, name)
