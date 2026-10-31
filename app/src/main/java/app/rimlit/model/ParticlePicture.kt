package app.rimlit.model

import java.util.UUID

/** A look's particles draw at most this many of the user's own images. */
const val MAX_PARTICLE_IMAGES = 5

/** What a particle looks like. */
sealed interface ParticlePicture {

    /** One of the built-in images. */
    data class Builtin(val image: ParticleImage) : ParticlePicture

    /**
     * The user's own images, imported into app storage and named by [ids] (see [isValidParticleImageId]); the
     * particles spread across them. 1 to [MAX_PARTICLE_IMAGES] distinct ids.
     */
    data class Custom(val ids: List<String>) : ParticlePicture {
        init {
            require(ids.size in 1..MAX_PARTICLE_IMAGES) { "Custom particles need 1–$MAX_PARTICLE_IMAGES images" }
            require(ids.distinct().size == ids.size) { "Repeated particle image" }
            require(ids.all(::isValidParticleImageId)) { "Invalid particle image id" }
        }
    }

    /** The notifying app's icon, in its own colours; a preview draws Rimlit's own. */
    data object AppIcon : ParticlePicture
}

/** A new id for an imported particle image. */
fun newParticleImageId(): String = UUID.randomUUID().toString()

/**
 * Whether [id] is a particle image id: a lowercase UUID, as [newParticleImageId] makes. An id names a file, so
 * anything else (a path, a separator, `..`) is never one.
 */
fun isValidParticleImageId(id: String): Boolean = ID_PATTERN.matches(id)

private val ID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

/** Every image id any look's particles draw: the imported files to keep. */
val GlowSettings.particleImageIds: Set<String>
    get() = looks.flatMapTo(mutableSetOf()) { (it.particles?.picture as? ParticlePicture.Custom)?.ids.orEmpty() }

/**
 * These particles drawing [added] images too: after the look's own images, without repeats, up to
 * [MAX_PARTICLE_IMAGES]. Particles with another picture (or none) switch to [added], in their own colours, keeping
 * their motion and tuning. Unchanged when nothing is [added].
 */
fun ParticleSpec?.withImages(added: List<String>): ParticleSpec? {
    if (added.isEmpty()) return this
    val own = (this?.picture as? ParticlePicture.Custom)?.ids.orEmpty()
    val picture = ParticlePicture.Custom((own + added).distinct().take(MAX_PARTICLE_IMAGES))
    return this?.copy(picture = picture, tint = if (own.isEmpty()) ParticleTint.IMAGE else tint)
        ?: ParticleSpec(picture, ParticleMotion.FALL, tint = ParticleTint.IMAGE)
}

/** These particles drawing the app's icon, keeping their motion and tuning; falling, for no particles. */
fun ParticleSpec?.withAppIcon(): ParticleSpec =
    this?.copy(picture = ParticlePicture.AppIcon) ?: ParticleSpec(ParticlePicture.AppIcon, ParticleMotion.FALL)

/** These particles without the image [id]; unchanged when it is their only one (a picture needs one). */
fun ParticleSpec.withoutImage(id: String): ParticleSpec {
    val ids = (picture as? ParticlePicture.Custom)?.ids ?: return this
    val left = ids - id
    return if (left.isEmpty()) this else copy(picture = ParticlePicture.Custom(left))
}
