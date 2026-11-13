package app.rimlit.model

/**
 * The particles a look can pick, each a picture with its motion and a starting size, speed, amount, spin and tint the
 * user tunes from. A look saves its whole [ParticleSpec], not the preset: [of] says which preset it was picked from.
 */
enum class ParticlePreset(val spec: ParticleSpec) {
    // Picture, motion, size (dp), speed, amount, fastest spin (degrees per second), tint.
    SNOW(ParticleSpec(ParticleImage.SNOWFLAKE, ParticleMotion.FALL, 14f, 0.6f, 0.5f, 60f, ParticleTint.IMAGE)),
    PETALS(ParticleSpec(ParticleImage.PETAL, ParticleMotion.FALL, 18f, 0.7f, 0.35f, 90f, ParticleTint.IMAGE)),
    RAIN(ParticleSpec(ParticleImage.RAINDROP, ParticleMotion.FALL, 12f, 2f, 0.6f, 0f, ParticleTint.IMAGE)),
    HEARTS(ParticleSpec(ParticleImage.HEART, ParticleMotion.RISE, 20f, 0.8f, 0.3f, 0f, ParticleTint.IMAGE)),
    BUBBLES(ParticleSpec(ParticleImage.BUBBLE, ParticleMotion.RISE, 22f, 0.6f, 0.3f, 0f, ParticleTint.IMAGE)),
    CONFETTI(ParticleSpec(ParticleImage.CONFETTI, ParticleMotion.FALL, 12f, 1.2f, 0.7f, 240f, ParticleTint.PALETTE)),
    STARS(ParticleSpec(ParticleImage.STAR, ParticleMotion.DRIFT, 14f, 0.8f, 0.4f, 30f, ParticleTint.PALETTE)),
    SPARKS(ParticleSpec(ParticleImage.SPARK, ParticleMotion.POP, 12f, 1f, 0.5f, 0f, ParticleTint.PALETTE)),
    ;

    companion object {
        /** The preset [spec] was picked from (the same picture and motion, however tuned since); `null` for none. */
        fun of(spec: ParticleSpec?): ParticlePreset? =
            entries.firstOrNull { it.spec.image == spec?.image && it.spec.motion == spec.motion }
    }
}
