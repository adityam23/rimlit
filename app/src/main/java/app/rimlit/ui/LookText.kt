package app.rimlit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import app.rimlit.R
import app.rimlit.model.GlowStyle
import app.rimlit.model.Look
import app.rimlit.model.Premade
import app.rimlit.model.isCustom

// How looks, their premades and their edge styles read, wherever they are shown.

/** The premade's name: its edge style's. */
@Composable
@ReadOnlyComposable
internal fun Premade.title(): String = stringResource(style.titleRes)

/** What [look] is: its premade's name while untouched, else "Custom · based on Glow". */
@Composable
@ReadOnlyComposable
internal fun lookKind(look: Look): String = if (look.isCustom) {
    stringResource(R.string.look_custom_based_on, look.basedOn.title())
} else {
    look.basedOn.title()
}

internal val GlowStyle.titleRes
    get() = when (this) {
        GlowStyle.BASIC -> R.string.style_basic_title
        GlowStyle.GLOW -> R.string.style_glow_title
        GlowStyle.BLADE -> R.string.style_blade_title
        GlowStyle.WAVE -> R.string.style_wave_title
        GlowStyle.GLITTER -> R.string.style_glitter_title
        GlowStyle.ECHO -> R.string.style_echo_title
        GlowStyle.LOOP -> R.string.style_loop_title
        GlowStyle.MIRROR -> R.string.style_mirror_title
        GlowStyle.BOOMERANG -> R.string.style_boomerang_title
        GlowStyle.FIREWORKS -> R.string.style_fireworks_title
        GlowStyle.NONE -> R.string.style_none_title
    }

internal val GlowStyle.descRes
    get() = when (this) {
        GlowStyle.BASIC -> R.string.style_basic_desc
        GlowStyle.GLOW -> R.string.style_glow_desc
        GlowStyle.BLADE -> R.string.style_blade_desc
        GlowStyle.WAVE -> R.string.style_wave_desc
        GlowStyle.GLITTER -> R.string.style_glitter_desc
        GlowStyle.ECHO -> R.string.style_echo_desc
        GlowStyle.LOOP -> R.string.style_loop_desc
        GlowStyle.MIRROR -> R.string.style_mirror_desc
        GlowStyle.BOOMERANG -> R.string.style_boomerang_desc
        GlowStyle.FIREWORKS -> R.string.style_fireworks_desc
        GlowStyle.NONE -> R.string.style_none_desc
    }
