package app.rimlit.model

/**
 * The particles a look can pick, each a picture with its motion and a starting size, speed, amount, spin and tint the
 * user tunes from. A look saves its whole [ParticleSpec], not the preset: [of] says which preset it was picked from.
 */
enum class ParticlePreset(val spec: ParticleSpec) {
    // Picture, motion, size (dp), speed, amount, fastest spin (degrees per second), tint.
    SNOW(builtin(ParticleImage.SNOWFLAKE, ParticleMotion.FALL, 14f, 0.6f, 0.5f, 60f, ParticleTint.IMAGE)),
    PETALS(builtin(ParticleImage.PETAL, ParticleMotion.FALL, 18f, 0.7f, 0.35f, 90f, ParticleTint.IMAGE)),
    RAIN(builtin(ParticleImage.RAINDROP, ParticleMotion.FALL, 12f, 2f, 0.6f, 0f, ParticleTint.IMAGE)),
    HEARTS(builtin(ParticleImage.HEART, ParticleMotion.RISE, 20f, 0.8f, 0.3f, 0f, ParticleTint.IMAGE)),
    BUBBLES(builtin(ParticleImage.BUBBLE, ParticleMotion.RISE, 22f, 0.6f, 0.3f, 0f, ParticleTint.IMAGE)),
    CONFETTI(builtin(ParticleImage.CONFETTI, ParticleMotion.FALL, 12f, 1.2f, 0.7f, 240f, ParticleTint.PALETTE)),
    STARS(builtin(ParticleImage.STAR, ParticleMotion.DRIFT, 14f, 0.8f, 0.4f, 30f, ParticleTint.PALETTE)),
    SPARKS(builtin(ParticleImage.SPARK, ParticleMotion.POP, 12f, 1f, 0.5f, 0f, ParticleTint.PALETTE)),
    ;

    companion object {
        /** The preset [spec] was picked from (the same picture and motion, however tuned since); `null` for none. */
        fun of(spec: ParticleSpec?): ParticlePreset? =
            entries.firstOrNull { it.spec.picture == spec?.picture && it.spec.motion == spec.motion }
    }
}

/** A preset's particles: a built-in [image], with [motion] and its starting tuning. */
private fun builtin(
    image: ParticleImage,
    motion: ParticleMotion,
    sizeDp: Float,
    speed: Float,
    amount: Float,
    rotationDegPerSecond: Float,
    tint: ParticleTint = ParticleTint.PALETTE,
) = ParticleSpec(ParticlePicture.Builtin(image), motion, sizeDp, speed, amount, rotationDegPerSecond, tint)
