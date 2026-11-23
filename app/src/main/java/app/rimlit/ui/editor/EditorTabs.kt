package app.rimlit.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.rimlit.R
import app.rimlit.model.Look
import app.rimlit.model.LookPart
import app.rimlit.model.reset
import app.rimlit.ui.components.SwitchCard
import app.rimlit.ui.title

/**
 * The open tab's content, then, on a look's tab, its footer. [hasCutout]: the device has a camera cutout to ring;
 * [onAddImages] opens the photo picker for more of the look's own particle images.
 */
internal fun LazyListScope.tabContent(editor: LookEditorState, hasCutout: Boolean, onAddImages: () -> Unit) {
    when (editor.tab) {
        EditorTab.BASE -> baseTab(editor, hasCutout)
        EditorTab.COLOUR -> colourTab(editor)
        EditorTab.PARTICLES -> particlesTab(editor, onAddImages)
        EditorTab.TUNING -> tuningTab(editor)
        EditorTab.APPS -> appsTab(editor)
    }
    editor.tab.part?.let { partFooter(editor, it) }
}

/**
 * A look tab's footer: reset the part to the premade while it differs, and the tab's "Tinker" switch, which "Always
 * tinker" hides (every tab tinkers then; each tab's own choice comes back when it is turned off).
 */
private fun LazyListScope.partFooter(editor: LookEditorState, part: LookPart) {
    item(key = "footer_reset") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(
                onClick = { editor.edit(editor.look.reset(part)) },
                enabled = part in editor.changedParts,
            ) {
                Text(stringResource(R.string.editor_reset_to, editor.look.basedOn.title()))
            }
        }
    }
    if (editor.alwaysTinker) return
    item(key = "footer_tinker") {
        SwitchCard(
            title = stringResource(R.string.editor_tinker_title),
            checked = editor.tinkering,
            onCheckedChange = editor::tinker,
            subtitle = stringResource(R.string.editor_tinker_desc),
        )
    }
}

/** Sets the draft to [change] of it with a slider's value: nothing is written until the look is saved. */
internal fun LookEditorState.slide(change: Look.(Float) -> Look): (Float) -> Unit = { edit(look.change(it)) }
