package app.rimlit.model

/** A built-in particle picture. */
enum class ParticleImage { SNOWFLAKE, PETAL, RAINDROP, STAR, HEART, BUBBLE, CONFETTI, SPARK }

/** How particles move across the screen. */
enum class ParticleMotion {
    /** From above the top edge down past the bottom, swaying. */
    FALL,

    /** From below the bottom edge up past the top, swaying. */
    RISE,

    /** In bursts flying out from a point, fading as they go. */
    POP,

    /** Appearing anywhere, floating a short way and fading out. */
    DRIFT,
}

/** Where particles take their colours from. */
enum class ParticleTint {
    /** Each particle is one of the glow's palette colours. */
    PALETTE,

    /** The image's own colours. */
    IMAGE,
}

/**
 * Particles animated across the screen while a glow shows, drawn beneath its edge. They start with the glow and
 * stop spawning in time for the last ones to leave by the end of its fade-out.
 */
data class ParticleSpec(
    val image: ParticleImage,
    val motion: ParticleMotion,
    /** Width and height of a particle (the nearest ones; farther ones are drawn smaller). */
    val sizeDp: Float = 16f,
    /** Movement rate multiplier (1 = default). */
    val speed: Float = 1f,
    /** Share of the most particles a glow draws at once. */
    val amount: Float = 0.5f,
    /** Fastest spin, in degrees per second; 0 keeps every particle upright. */
    val rotationDegPerSecond: Float = 0f,
    val tint: ParticleTint = ParticleTint.PALETTE,
) {
    init {
        val ranges = GlowSettings.Ranges
        require(sizeDp in ranges.particleSizeDp) { "Particle size out of range: $sizeDp" }
        require(speed in ranges.particleSpeed) { "Particle speed out of range: $speed" }
        require(amount in ranges.particleAmount) { "Particle amount out of range: $amount" }
        require(rotationDegPerSecond in ranges.particleRotation) { "Particle rotation out of range" }
    }
}
