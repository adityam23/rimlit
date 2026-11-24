package app.rimlit.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.rimlit.R
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.GlowStyle
import app.rimlit.model.LookControl
import app.rimlit.model.MAX_PARTICLE_IMAGES
import app.rimlit.model.ParticleControl
import app.rimlit.model.ParticleMotion
import app.rimlit.model.ParticlePicture
import app.rimlit.model.ParticlePreset
import app.rimlit.model.ParticleSpec
import app.rimlit.model.ParticleTint
import app.rimlit.model.imageRoom
import app.rimlit.model.resetToPreset
import app.rimlit.model.tweaks
import app.rimlit.model.withAppIcon
import app.rimlit.model.withoutImage
import app.rimlit.ui.ParticleImagesCard
import app.rimlit.ui.components.RadioCard
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.components.SettingSlider
import app.rimlit.ui.components.SwitchCard
import kotlin.math.roundToInt

/**
 * The look's particles: none, a preset, the user's own images or the app's icon. With particles, the edge can be
 * turned off ("Particles only"), and a preset tuned away from its own reads as tweaked, with a reset. Tinkering,
 * their motion (a preset sets its own), tuning and colours (an app's icon keeps its own), each marked once it differs
 * from the preset's. A look with no edge can't turn its particles off, so it always shows something.
 */
internal fun LazyListScope.particlesTab(editor: LookEditorState, onAddImages: () -> Unit) {
    val look = editor.look
    val particles = look.particles
    val noEdge = look.style == GlowStyle.NONE
    item(key = "title") {
        SectionTitle(
            stringResource(R.string.particles_section),
            changed = LookControl.PARTICLES in editor.changed,
        )
    }
    item(key = "none") {
        RadioCard(
            title = stringResource(R.string.particles_none_title),
            selected = particles == null,
            onSelect = { editor.edit(look.copy(particles = null)) },
            subtitle = stringResource(
                if (noEdge) R.string.particles_none_unavailable else R.string.particles_none_desc,
            ),
            enabled = !noEdge,
        )
    }
    presetItems(editor)
    ownPictureItems(editor, onAddImages)
    if (particles == null) return
    item(key = "no_edge") {
        SwitchCard(
            title = stringResource(R.string.particles_only_title),
            checked = noEdge,
            // Off, the edge comes back as the premade draws it.
            onCheckedChange = { only ->
                editor.edit(
                    look.copy(style = if (only) GlowStyle.NONE else look.basedOn.style),
                )
            },
            subtitle = stringResource(R.string.particles_only_desc),
        )
    }
    if (!editor.tinkering) return
    if (particles.picture !is ParticlePicture.Builtin) particleMotionItems(editor, particles)
    particleTuningItems(editor, particles)
}

/** The presets, the picked one reading as tweaked once tuned away from its own, with a reset to it. */
private fun LazyListScope.presetItems(editor: LookEditorState) {
    val particles = editor.look.particles
    val picked = ParticlePreset.of(particles)
    val tweaked = particles != null && particles.tweaks().isNotEmpty()
    items(ParticlePreset.entries, key = { it.name }) { preset ->
        val title = stringResource(preset.titleRes)
        RadioCard(
            title = if (picked == preset && tweaked) stringResource(R.string.particles_tweaked, title) else title,
            selected = picked == preset,
            // Picking it again keeps the tuning.
            onSelect = { if (picked != preset) editor.edit(editor.look.copy(particles = preset.spec)) },
            subtitle = stringResource(preset.descRes),
        )
    }
    if (picked == null || particles == null || !tweaked) return
    item(key = "reset_preset") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = { editor.edit(editor.look.copy(particles = particles.resetToPreset())) }) {
                Text(stringResource(R.string.editor_reset_to, stringResource(picked.titleRes)))
            }
        }
    }
}

/** The user's own images (picking them opens the picker; once picked, they show to add and remove) and the app icon. */
private fun LazyListScope.ownPictureItems(editor: LookEditorState, onAddImages: () -> Unit) {
    val look = editor.look
    val particles = look.particles
    val picture = particles?.picture
    item(key = "particles_custom") {
        RadioCard(
            title = stringResource(R.string.particles_custom_title),
            selected = picture is ParticlePicture.Custom,
            // The look switches to them once they are imported.
            onSelect = { if (picture !is ParticlePicture.Custom) onAddImages() },
            subtitle = stringResource(R.string.particles_custom_desc, MAX_PARTICLE_IMAGES),
        )
    }
    if (particles != null && picture is ParticlePicture.Custom) {
        item(key = "particles_images") {
            ParticleImagesCard(
                ids = picture.ids,
                onRemove = { id -> editor.edit(look.copy(particles = particles.withoutImage(id))) },
                onAdd = onAddImages.takeIf { particles.imageRoom > 0 },
            )
        }
    }
    item(key = "particles_app_icon") {
        val selected = picture == ParticlePicture.AppIcon
        RadioCard(
            title = stringResource(R.string.particles_app_icon_title),
            selected = selected,
            onSelect = { if (!selected) editor.edit(look.copy(particles = particles.withAppIcon())) },
            subtitle = stringResource(R.string.particles_app_icon_desc),
        )
    }
}

/** How the particles move: a preset sets its own, the user's images and the app icon pick one here. */
private fun LazyListScope.particleMotionItems(editor: LookEditorState, particles: ParticleSpec) {
    item(key = "motion_title") { SectionTitle(stringResource(R.string.particle_motion_section)) }
    items(ParticleMotion.entries, key = { "motion_${it.name}" }) { motion ->
        RadioCard(
            title = stringResource(motion.titleRes),
            selected = particles.motion == motion,
            onSelect = { editor.edit(editor.look.copy(particles = particles.copy(motion = motion))) },
            subtitle = stringResource(motion.descRes),
        )
    }
}

/** The particles' tuning, each marked once it differs from their preset's. */
private fun LazyListScope.particleTuningItems(editor: LookEditorState, particles: ParticleSpec) {
    val tweaked = particles.tweaks()
    fun tune(change: ParticleSpec.(Float) -> ParticleSpec) = editor.slide { copy(particles = particles.change(it)) }
    item(key = "size") {
        SettingSlider(
            label = stringResource(R.string.particle_size_label),
            value = particles.sizeDp,
            range = Ranges.particleSizeDp,
            valueText = { stringResource(R.string.value_dp, it.roundToInt()) },
            changed = ParticleControl.SIZE in tweaked,
            onPreview = tune { copy(sizeDp = it) },
        )
    }
    item(key = "particle_speed") {
        SettingSlider(
            label = stringResource(R.string.particle_speed_label),
            value = particles.speed,
            range = Ranges.particleSpeed,
            valueText = { stringResource(R.string.value_multiplier, it) },
            changed = ParticleControl.SPEED in tweaked,
            onPreview = tune { copy(speed = it) },
        )
    }
    item(key = "amount") {
        SettingSlider(
            label = stringResource(R.string.particle_amount_label),
            value = particles.amount,
            range = Ranges.particleAmount,
            valueText = { stringResource(R.string.value_percent, (it * 100).roundToInt()) },
            changed = ParticleControl.AMOUNT in tweaked,
            onPreview = tune { copy(amount = it) },
        )
    }
    item(key = "rotation") {
        SettingSlider(
            label = stringResource(R.string.particle_rotation_label),
            value = particles.rotationDegPerSecond,
            range = Ranges.particleRotation,
            valueText = { stringResource(R.string.value_degrees_per_second, it.roundToInt()) },
            changed = ParticleControl.ROTATION in tweaked,
            onPreview = tune { copy(rotationDegPerSecond = it) },
        )
    }
    // An app's icon always keeps its own colours (see ParticleSpec.drawnTint).
    if (particles.picture == ParticlePicture.AppIcon) return
    item(key = "tint_title") {
        SectionTitle(stringResource(R.string.particle_tint_section), changed = ParticleControl.TINT in tweaked)
    }
    items(ParticleTint.entries, key = { "tint_${it.name}" }) { tint ->
        RadioCard(
            title = stringResource(tint.titleRes),
            selected = particles.tint == tint,
            onSelect = { editor.edit(editor.look.copy(particles = particles.copy(tint = tint))) },
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
