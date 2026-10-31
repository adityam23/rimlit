package app.rimlit.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.stringResource
import app.rimlit.R
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.GlowStyle
import app.rimlit.model.Look
import app.rimlit.model.MAX_PARTICLE_IMAGES
import app.rimlit.model.ParticleMotion
import app.rimlit.model.ParticlePicture
import app.rimlit.model.ParticlePreset
import app.rimlit.model.ParticleSpec
import app.rimlit.model.ParticleTint
import app.rimlit.model.withAppIcon
import app.rimlit.model.withoutImage
import app.rimlit.ui.components.RadioCard
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.SettingSlider
import kotlin.math.roundToInt

/**
 * [look]'s particles: none, a preset, the user's own images ([onAddImages] picks more) or the app's icon, then, with
 * particles, their motion (a preset sets its own), tuning and colours (an app's icon keeps its own). Picking a preset
 * starts from its own tuning; the other pictures keep the current one. A look with no edge ([GlowStyle.NONE]) can't
 * turn its particles off, so it always shows something.
 */
internal fun LazyListScope.particlesSection(look: Look, editor: SettingsDraft, onAddImages: () -> Unit) {
    item { SectionTitle(stringResource(R.string.particles_section)) }
    item {
        val available = look.style != GlowStyle.NONE
        RadioCard(
            title = stringResource(R.string.particles_none_title),
            selected = look.particles == null,
            onSelect = { editor.commit(look.edit { it.copy(particles = null) }) },
            subtitle = stringResource(
                if (available) R.string.particles_none_desc else R.string.particles_none_unavailable,
            ),
            enabled = available,
        )
    }
    val picked = ParticlePreset.of(look.particles)
    items(ParticlePreset.entries, key = { it.name }) { preset ->
        RadioCard(
            title = stringResource(preset.titleRes),
            selected = picked == preset,
            // Picking it again keeps the tuning.
            onSelect = { if (picked != preset) editor.commit(look.edit { it.copy(particles = preset.spec) }) },
            subtitle = stringResource(preset.descRes),
        )
    }
    ownPictureItems(look, editor, onAddImages)
    val particles = look.particles ?: return
    if (particles.picture !is ParticlePicture.Builtin) particleMotionItems(look, editor)
    particleTuningItems(look, particles, editor)
}

/** The user's own images (picking them opens the picker; once picked, they show to add and remove) and the app icon. */
private fun LazyListScope.ownPictureItems(look: Look, editor: SettingsDraft, onAddImages: () -> Unit) {
    val picture = look.particles?.picture
    item(key = "particles_custom") {
        RadioCard(
            title = stringResource(R.string.particles_custom_title),
            selected = picture is ParticlePicture.Custom,
            // The look switches to them once they are imported.
            onSelect = { if (picture !is ParticlePicture.Custom) onAddImages() },
            subtitle = stringResource(R.string.particles_custom_desc, MAX_PARTICLE_IMAGES),
        )
    }
    if (picture is ParticlePicture.Custom) {
        item(key = "particles_images") {
            ParticleImagesCard(
                ids = picture.ids,
                onRemove = { id -> editor.commit(look.edit { it.copy(particles = it.particles?.withoutImage(id)) }) },
                onAdd = onAddImages.takeIf { picture.ids.size < MAX_PARTICLE_IMAGES },
            )
        }
    }
    item(key = "particles_app_icon") {
        val selected = picture == ParticlePicture.AppIcon
        RadioCard(
            title = stringResource(R.string.particles_app_icon_title),
            selected = selected,
            onSelect = { if (!selected) editor.commit(look.edit { it.copy(particles = it.particles.withAppIcon()) }) },
            subtitle = stringResource(R.string.particles_app_icon_desc),
        )
    }
}

/** How the particles move: a preset sets its own, the user's images and the app icon pick one here. */
private fun LazyListScope.particleMotionItems(look: Look, editor: SettingsDraft) {
    item { SectionTitle(stringResource(R.string.particle_motion_section)) }
    items(ParticleMotion.entries, key = { "motion_${it.name}" }) { motion ->
        RadioCard(
            title = stringResource(motion.titleRes),
            selected = look.particles?.motion == motion,
            onSelect = { editor.commit(look.edit { it.copy(particles = it.particles?.copy(motion = motion)) }) },
            subtitle = stringResource(motion.descRes),
        )
    }
}

private fun LazyListScope.particleTuningItems(look: Look, particles: ParticleSpec, editor: SettingsDraft) {
    fun tuned(edit: (ParticleSpec) -> ParticleSpec) = look.edit { it.copy(particles = it.particles?.let(edit)) }
    item {
        SettingSlider(
            label = stringResource(R.string.particle_size_label),
            value = particles.sizeDp,
            range = Ranges.particleSizeDp,
            valueText = { stringResource(R.string.value_dp, it.roundToInt()) },
            onPreview = { v -> editor.preview(tuned { it.copy(sizeDp = v) }) },
            onCommit = { v -> editor.persist(tuned { it.copy(sizeDp = v) }) },
        )
    }
    item {
        SettingSlider(
            label = stringResource(R.string.particle_speed_label),
            value = particles.speed,
            range = Ranges.particleSpeed,
            valueText = { stringResource(R.string.value_multiplier, it) },
            onPreview = { v -> editor.preview(tuned { it.copy(speed = v) }) },
            onCommit = { v -> editor.persist(tuned { it.copy(speed = v) }) },
        )
    }
    item {
        SettingSlider(
            label = stringResource(R.string.particle_amount_label),
            value = particles.amount,
            range = Ranges.particleAmount,
            valueText = { stringResource(R.string.value_percent, (it * 100).roundToInt()) },
            onPreview = { v -> editor.preview(tuned { it.copy(amount = v) }) },
            onCommit = { v -> editor.persist(tuned { it.copy(amount = v) }) },
        )
    }
    item {
        SettingSlider(
            label = stringResource(R.string.particle_rotation_label),
            value = particles.rotationDegPerSecond,
            range = Ranges.particleRotation,
            valueText = { stringResource(R.string.value_degrees_per_second, it.roundToInt()) },
            onPreview = { v -> editor.preview(tuned { it.copy(rotationDegPerSecond = v) }) },
            onCommit = { v -> editor.persist(tuned { it.copy(rotationDegPerSecond = v) }) },
        )
    }
    // An app's icon always keeps its own colours (see ParticleSpec.drawnTint).
    if (particles.picture == ParticlePicture.AppIcon) return
    item { SectionTitle(stringResource(R.string.particle_tint_section)) }
    items(ParticleTint.entries, key = { it.name }) { tint ->
        RadioCard(
            title = stringResource(tint.titleRes),
            selected = particles.tint == tint,
            onSelect = { editor.commit(tuned { it.copy(tint = tint) }) },
            subtitle = stringResource(tint.descRes),
        )
    }
}

private val ParticlePreset.titleRes
    get() = when (this) {
        ParticlePreset.SNOW -> R.string.particles_snow_title
        ParticlePreset.PETALS -> R.string.particles_petals_title
        ParticlePreset.RAIN -> R.string.particles_rain_title
        ParticlePreset.HEARTS -> R.string.particles_hearts_title
        ParticlePreset.BUBBLES -> R.string.particles_bubbles_title
        ParticlePreset.CONFETTI -> R.string.particles_confetti_title
        ParticlePreset.STARS -> R.string.particles_stars_title
        ParticlePreset.SPARKS -> R.string.particles_sparks_title
    }

private val ParticlePreset.descRes
    get() = when (this) {
        ParticlePreset.SNOW -> R.string.particles_snow_desc
        ParticlePreset.PETALS -> R.string.particles_petals_desc
        ParticlePreset.RAIN -> R.string.particles_rain_desc
        ParticlePreset.HEARTS -> R.string.particles_hearts_desc
        ParticlePreset.BUBBLES -> R.string.particles_bubbles_desc
        ParticlePreset.CONFETTI -> R.string.particles_confetti_desc
        ParticlePreset.STARS -> R.string.particles_stars_desc
        ParticlePreset.SPARKS -> R.string.particles_sparks_desc
    }

private val ParticleMotion.titleRes
    get() = when (this) {
        ParticleMotion.FALL -> R.string.particle_motion_fall_title
        ParticleMotion.RISE -> R.string.particle_motion_rise_title
        ParticleMotion.POP -> R.string.particle_motion_pop_title
        ParticleMotion.DRIFT -> R.string.particle_motion_drift_title
    }

private val ParticleMotion.descRes
    get() = when (this) {
        ParticleMotion.FALL -> R.string.particle_motion_fall_desc
        ParticleMotion.RISE -> R.string.particle_motion_rise_desc
        ParticleMotion.POP -> R.string.particle_motion_pop_desc
        ParticleMotion.DRIFT -> R.string.particle_motion_drift_desc
    }

private val ParticleTint.titleRes
    get() = when (this) {
        ParticleTint.PALETTE -> R.string.particle_tint_palette_title
        ParticleTint.IMAGE -> R.string.particle_tint_image_title
    }

private val ParticleTint.descRes
    get() = when (this) {
        ParticleTint.PALETTE -> R.string.particle_tint_palette_desc
        ParticleTint.IMAGE -> R.string.particle_tint_image_desc
    }
