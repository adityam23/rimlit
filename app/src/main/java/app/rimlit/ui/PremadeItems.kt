package app.rimlit.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.rimlit.model.GlowSettings
import app.rimlit.model.Premade
import app.rimlit.ui.components.RadioCard

/**
 * One radio row per premade, each with its description and its edge as these [settings] play it, [selected] checked;
 * [onSelect] gets the one tapped. The edges are still but the selected one's, which moves when [animatesSelected]: one
 * moving glow at a time.
 */
internal fun LazyListScope.premadeItems(
    settings: GlowSettings,
    selected: Premade?,
    onSelect: (Premade) -> Unit,
    animatesSelected: Boolean,
) {
    items(Premade.entries, key = { "premade_${it.name}" }) { premade ->
        RadioCard(
            title = premade.title(),
            selected = selected == premade,
            onSelect = { onSelect(premade) },
            subtitle = stringResource(premade.style.descRes),
            leading = {
                PremadeGlow(premade, settings, width = 32.dp, moving = animatesSelected && selected == premade)
            },
        )
    }
}
