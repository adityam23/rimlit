package app.rimlit.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.rimlit.R
import app.rimlit.model.EdgeRegion
import app.rimlit.model.LookControl
import app.rimlit.model.ScreenSide
import app.rimlit.model.withSide
import app.rimlit.ui.components.RadioCard
import app.rimlit.ui.components.SectionTitle
import app.rimlit.ui.premadeItems

/**
 * The premade the look is based on, each playing its edge; picking another switches the whole look to it (asking
 * first when that loses changes). Tinkering, the region too, on a device with a camera cutout to ring (or for a look
 * that doesn't trace the edge, to set it back), and the sides of the screen edge it lights, while it traces the edge.
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
    if (!editor.tinkering) return
    // A look not tracing the edge (an imported one) offers its region even with no cutout, to set it back to the edge.
    if (hasCutout || !look.region.tracesEdge) {
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
    if (look.region.tracesEdge) sideChips(editor)
}

/** The sides of the screen edge the look lights, one chip each; the last one lit can't be turned off. */
private fun LazyListScope.sideChips(editor: LookEditorState) {
    val look = editor.look
    item(key = "sides_title") {
        SectionTitle(stringResource(R.string.sides_section), changed = LookControl.SIDES in editor.changed)
    }
    item(key = "sides") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (side in SIDE_CHIPS) {
                val lit = side in look.sides
                val next = look.sides.withSide(side, lit = !lit)
                FilterChip(
                    selected = lit,
                    onClick = { editor.edit(look.copy(sides = next)) },
                    label = { Text(stringResource(side.labelRes)) },
                    enabled = next != look.sides,
                )
            }
        }
    }
}

/** The order the side chips read in. */
private val SIDE_CHIPS = listOf(ScreenSide.TOP, ScreenSide.BOTTOM, ScreenSide.LEFT, ScreenSide.RIGHT)

private val ScreenSide.labelRes
    get() = when (this) {
        ScreenSide.TOP -> R.string.side_top
        ScreenSide.BOTTOM -> R.string.side_bottom
        ScreenSide.LEFT -> R.string.side_left
        ScreenSide.RIGHT -> R.string.side_right
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
