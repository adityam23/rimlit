package app.rimlit.ui.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.model.EdgeRegion
import app.rimlit.model.GlowSpec
import app.rimlit.model.Premade
import app.rimlit.model.canAddLook
import app.rimlit.model.changedParts
import app.rimlit.ui.LookNameDialog
import app.rimlit.ui.glow.EdgeGlow
import app.rimlit.ui.glow.playback
import app.rimlit.ui.glow.rememberDeviceConditions
import app.rimlit.ui.rememberAddParticleImages
import app.rimlit.ui.rememberCutoutSeed

/** How much of the screen the sheet takes when pulled up: the glow's top edge and sides stay in sight. */
private const val SHEET_HEIGHT = 0.85f

/** The dim over the wallpaper while the screen is on, so the glow reads as it does over an app. */
private const val WALLPAPER_DIM = 0.35f

/**
 * The full-screen look editor: the draft glows round this screen's real edge, over the wallpaper dimmed (screen on)
 * or black (screen off), and is changed in a sheet of tabs below. Nothing is written until Save; leaving with changes
 * asks first. It edits the look [lookId], or a new one from [premade] that exists only once saved. [onClose] leaves.
 */
@Composable
fun LookEditorScreen(
    viewModel: MainViewModel,
    lookId: String?,
    premade: Premade?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val close by rememberUpdatedState(onClose)
    val editor = rememberLookEditorState(viewModel, lookId, premade) ?: run {
        // The look is gone (deleted elsewhere), or nothing was asked for: nothing to edit.
        LaunchedEffect(Unit) { close() }
        return
    }
    val context = LocalContext.current
    val hasCutout = rememberCutoutSeed() != null
    // What this device will really show: without a cutout, the renderer falls back to the full edge.
    val spec = editor.settings.previewSpec(editor.look)
        .let { if (hasCutout) it else it.copy(region = EdgeRegion.FULL_SCREEN) }
    val addImages = rememberAddParticleImages(viewModel, editor.look, editor::addImages)
    val save = {
        editor.save { saved ->
            if (saved) close() else Toast.makeText(context, R.string.editor_save_failed, Toast.LENGTH_SHORT).show()
        }
    }
    val leave = { editor.leave(close) }
    BackHandler(onBack = leave)

    EditorWindow(showWallpaper = !editor.offScreen)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (editor.offScreen) Color.Black else Color.Black.copy(alpha = WALLPAPER_DIM)),
    ) {
        EdgeGlow(
            spec = spec,
            playback = spec.playback(lockScreen = editor.offScreen, rememberDeviceConditions()),
            preview = true,
            scaled = false,
            modifier = Modifier.fillMaxSize(),
        )
        EditorSheet(editor, spec, hasCutout, onBack = leave, onSave = save, onAddImages = addImages)
    }
    EditorDialogs(editor, onSave = save, onLeave = close)
}

/** The top bar over the glow, and the sheet of tabs pulled up from below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSheet(
    editor: LookEditorState,
    spec: GlowSpec,
    hasCutout: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onAddImages: () -> Unit,
) {
    BottomSheetScaffold(
        scaffoldState = rememberBottomSheetScaffoldState(),
        sheetPeekHeight = 360.dp,
        containerColor = Color.Transparent,
        topBar = { EditorTopBar(editor, onBack = onBack, onSave = onSave, onSendTest = { editor.sendTest(spec) }) },
        sheetContent = {
            Column(modifier = Modifier.fillMaxHeight(SHEET_HEIGHT).navigationBarsPadding()) {
                EditorTabRow(editor)
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    tabContent(editor, hasCutout, onAddImages)
                }
            }
        },
    ) {}
}

/** The tabs, each with a dot while its part of the look differs from the premade. */
@Composable
private fun EditorTabRow(editor: LookEditorState) {
    val changed = editor.look.changedParts()
    PrimaryScrollableTabRow(selectedTabIndex = editor.tab.ordinal, edgePadding = 8.dp) {
        for (tab in EditorTab.entries) {
            Tab(
                selected = tab == editor.tab,
                onClick = { editor.tab = tab },
                text = {
                    BadgedBox(badge = { if (tab.part in changed) Badge() }) {
                        Text(stringResource(tab.labelRes))
                    }
                },
            )
        }
    }
}

/** The editor's open dialog, if any: leaving with changes, renaming, or switching premade. */
@Composable
private fun EditorDialogs(editor: LookEditorState, onSave: () -> Unit, onLeave: () -> Unit) {
    when (val dialog = editor.dialog) {
        null -> Unit

        EditorDialog.Leave -> LeaveDialog(
            canSave = !editor.isNew || editor.settings.canAddLook,
            onSave = onSave,
            onDiscard = { editor.discard(onLeave) },
            onDismiss = { editor.dialog = null },
        )

        EditorDialog.Rename -> LookNameDialog(
            title = stringResource(R.string.look_rename_title),
            initial = editor.look.name,
            settings = editor.settings,
            exceptId = editor.look.id,
            onConfirm = editor::rename,
            onDismiss = { editor.dialog = null },
        )

        is EditorDialog.Switch -> SwitchPremadeDialog(
            from = editor.look.basedOn,
            to = dialog.premade,
            onConfirm = { editor.switchTo(dialog.premade) },
            onDismiss = { editor.dialog = null },
        )
    }
}

private val EditorTab.labelRes
    get() = when (this) {
        EditorTab.BASE -> R.string.editor_tab_base
        EditorTab.COLOUR -> R.string.editor_tab_color
        EditorTab.PARTICLES -> R.string.editor_tab_particles
        EditorTab.TUNING -> R.string.editor_tab_tuning
        EditorTab.APPS -> R.string.editor_tab_apps
    }
