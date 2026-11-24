package app.rimlit.ui.editor

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.stringResource
import app.rimlit.R
import app.rimlit.model.EdgeRegion
import app.rimlit.model.LookControl
import app.rimlit.ui.components.RadioCard
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.premadeItems

/**
 * The premade the look is based on, each playing its edge; picking another switches the whole look to it (asking
 * first when that loses changes). Tinkering, the region too, on a device with a camera cutout to ring.
 */
internal fun LazyListScope.baseTab(editor: LookEditorState, hasCutout: Boolean) {
    val look = editor.look
    val changed = editor.changed
    item(key = "premades") {
        SectionTitle(stringResource(R.string.editor_premade_section), changed = LookControl.STYLE in changed)
    }
    // Still: the editor's own glow already plays the one picked, beside them.
    premadeItems(
        settings = editor.settings,
        selected = look.basedOn,
        onSelect = { if (look.basedOn != it) editor.pickPremade(it) },
        animatesSelected = false,
    )
    if (!editor.tinkering || !hasCutout) return
    item(key = "region") {
        SectionTitle(stringResource(R.string.region_section), changed = LookControl.REGION in changed)
    }
    items(EdgeRegion.entries, key = { "region_${it.name}" }) { region ->
        RadioCard(
            title = stringResource(region.titleRes),
            selected = look.region == region,
            onSelect = { editor.edit(look.copy(region = region)) },
            subtitle = stringResource(region.descRes),
        )
    }
}

private val EdgeRegion.titleRes
    get() = when (this) {
        EdgeRegion.FULL_SCREEN -> R.string.region_full_title
        EdgeRegion.CUTOUT -> R.string.region_cutout_title
        EdgeRegion.CUTOUT_AND_EDGE -> R.string.region_both_title
    }

private val EdgeRegion.descRes
    get() = when (this) {
        EdgeRegion.FULL_SCREEN -> R.string.region_full_desc
        EdgeRegion.CUTOUT -> R.string.region_cutout_desc
        EdgeRegion.CUTOUT_AND_EDGE -> R.string.region_both_desc
    }
