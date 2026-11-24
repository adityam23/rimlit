package app.rimlit.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.LookDrafts
import app.rimlit.MainViewModel
import app.rimlit.R
import app.rimlit.data.AppInfo
import app.rimlit.data.decodeLooks
import app.rimlit.data.encodeLooks
import app.rimlit.data.matching
import app.rimlit.data.toEnumOrNull
import app.rimlit.engine.PreviewTarget
import app.rimlit.lookDrafts
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSpec
import app.rimlit.model.Look
import app.rimlit.model.LookControl
import app.rimlit.model.LookPart
import app.rimlit.model.Premade
import app.rimlit.model.UiPrefs
import app.rimlit.model.UseFor
import app.rimlit.model.canAddLook
import app.rimlit.model.changedControls
import app.rimlit.model.changedParts
import app.rimlit.model.freshName
import app.rimlit.model.showingSomething
import app.rimlit.model.switchedTo
import app.rimlit.model.useFor
import app.rimlit.model.withImages
import app.rimlit.ui.titleRes
import java.util.UUID

/** The editor's tabs: one per [LookPart] of the look, then where it applies. */
internal enum class EditorTab(val part: LookPart?) {
    BASE(LookPart.BASE),
    COLOUR(LookPart.COLOUR),
    PARTICLES(LookPart.PARTICLES),
    TUNING(LookPart.TUNING),
    APPS(null),
}

/** The editor's open dialog. */
internal sealed interface EditorDialog {
    /** Leaving with unsaved changes: save, discard, or stay. */
    data object Leave : EditorDialog

    data object Rename : EditorDialog

    /** Switching the customised look to [premade], which loses its changes. */
    data class Switch(val premade: Premade) : EditorDialog
}

/**
 * What the editor reads and writes through: [preview] (plays a spec on a real surface), [updateUi] (the UI's
 * preferences), the process's [drafts] (saving), and the screen's collected [settings], [uiPrefs] and [apps].
 */
internal class EditorSources(
    val preview: (PreviewTarget, GlowSpec) -> Unit,
    val updateUi: ((UiPrefs) -> UiPrefs) -> Unit,
    val drafts: LookDrafts,
    val settings: State<GlowSettings>,
    val uiPrefs: State<UiPrefs>,
    val apps: State<List<AppInfo>>,
)

/**
 * The look editor's state, and what it does: the draft [look], where it applies ([applies]), the open tab and dialog.
 * Nothing is written until [save]. Images imported for the draft need no holding on to: the clean-up keeps them
 * through their grace. Kept across process death by [rememberLookEditorState]. Main thread only.
 */
@Stable
internal class LookEditorState(private val sources: EditorSources, look: Look, isNew: Boolean) {
    var look by mutableStateOf(look)
        private set

    /** Not saved yet: a new look, or a copy of one. */
    var isNew by mutableStateOf(isNew)
        private set

    /** Where the user set the look to apply; `null` while unchanged (as saved, or nowhere for a new look). */
    private var useFor by mutableStateOf<UseFor?>(null)

    /** A save is in flight: another, or leaving, waits for it. */
    var saving by mutableStateOf(false)
        private set

    var tab by mutableStateOf(EditorTab.BASE)
    var offScreen by mutableStateOf(false)
    var dialog by mutableStateOf<EditorDialog?>(null)
    var appQuery by mutableStateOf("")

    val settings: GlowSettings get() = sources.settings.value

    /** The apps the Apps tab lists: those [appQuery] matches, worked out once per query or app list. */
    val shownApps: List<AppInfo> by derivedStateOf { sources.apps.value.matching(appQuery) }

    /** The settings where the draft differs from its premade, worked out once per edit for every tab to mark. */
    val changed: Set<LookControl> by derivedStateOf { this.look.changedControls() }

    /** The parts the draft differs from its premade in: the tabs with a dot. */
    val changedParts: Set<LookPart> by derivedStateOf { this.look.changedParts() }

    /** Where the look applies once saved. */
    var applies: UseFor
        get() = useFor ?: if (isNew) UseFor(isDefault = false, apps = emptySet()) else settings.useFor(look.id)
        set(value) {
            useFor = value
        }

    val unsaved: Boolean
        get() = isNew || look != settings.look(look.id) || applies != settings.useFor(look.id)

    /** Whether saving is offered: there is something to save, no save is in flight, and a new look still fits. */
    val canSave: Boolean
        get() = unsaved && !saving && (!isNew || settings.canAddLook)

    /** The open tab shows every control: its "Tinker" is on. */
    val tinkering: Boolean get() = tab.part in sources.uiPrefs.value.tinkering

    /** Sets the draft to [look], always showing something (see [showingSomething]): as it will be saved. */
    fun edit(look: Look) {
        this.look = look.showingSomething()
    }

    /** Draws the images [ids], imported for this draft, among the look's particles. */
    fun addImages(ids: List<String>) {
        look = look.copy(particles = look.particles.withImages(ids))
    }

    /** Switches the look to [premade]: at once while untouched, else after asking, since its changes are lost. */
    fun pickPremade(premade: Premade) {
        if (changed.isEmpty()) look = look.switchedTo(premade) else dialog = EditorDialog.Switch(premade)
    }

    fun switchTo(premade: Premade) {
        look = look.switchedTo(premade)
        dialog = null
    }

    fun rename(name: String) {
        look = look.copy(name = name)
        dialog = null
    }

    /** Carries on editing a copy of the draft, named [copyName] (numbered when taken), as a new look used nowhere. */
    fun duplicate(copyName: String) {
        look = look.copy(id = UUID.randomUUID().toString(), name = settings.freshName(copyName))
        isNew = true
        useFor = null
    }

    /** Turns the open tab's "Tinker" on or off, remembered at once (it isn't part of the look). */
    fun tinker(on: Boolean) {
        val part = tab.part ?: return
        sources.updateUi { it.copy(tinkering = if (on) it.tinkering + part else it.tinkering - part) }
    }

    /** Plays [spec], the draft as this device draws it, on the real surface the editor shows: on screen, or off. */
    fun sendTest(spec: GlowSpec) {
        sources.preview(if (offScreen) PreviewTarget.LOCK_SCREEN else PreviewTarget.ON_SCREEN, spec)
    }

    /**
     * Saves the draft and where it applies; [onDone] says whether it was saved. One at a time: a save asked for while
     * another is in flight is ignored, so the editor is never left twice.
     */
    fun save(onDone: (saved: Boolean) -> Unit) {
        if (saving) return
        dialog = null
        saving = true
        sources.drafts.save(look, applies, isNew) { saved ->
            saving = false
            onDone(saved)
        }
    }

    /** Leaves: at once with nothing unsaved ([onLeave]), else after asking ([EditorDialog.Leave]); not mid-save. */
    fun leave(onLeave: () -> Unit) {
        if (saving) return
        if (unsaved) dialog = EditorDialog.Leave else onLeave()
    }

    /** Drops the draft and leaves ([onLeave]); not mid-save, which leaves by itself once done. */
    fun discard(onLeave: () -> Unit) {
        if (saving) return
        dialog = null
        onLeave()
    }

    companion object {
        /** Keeps the draft as the settings store a look, decoded as tolerantly, so a restored draft is always valid. */
        fun saver(sources: EditorSources): Saver<Held, Any> = Saver(
            save = { held ->
                held.editor?.run {
                    arrayListOf(
                        encodeLooks(listOf(look)),
                        isNew,
                        useFor?.let { arrayListOf(it.isDefault) + it.apps },
                        tab.ordinal,
                        offScreen,
                        dialog?.let(::saved),
                        appQuery,
                    )
                }
            },
            restore = { saved ->
                val fields = saved as List<*>
                val look = decodeLooks(fields[0] as String)?.singleOrNull() ?: return@Saver null
                val editor = LookEditorState(sources, look, isNew = fields[1] as Boolean).apply {
                    useFor = (fields[2] as List<*>?)?.let { applied ->
                        UseFor(applied.first() as Boolean, applied.drop(1).map(Any?::toString).toSet())
                    }
                    tab = EditorTab.entries[fields[3] as Int]
                    offScreen = fields[4] as Boolean
                    dialog = (fields[5] as String?)?.let(::restored)
                    appQuery = fields[6] as String
                }
                Held(editor)
            },
        )

        /**
         * The state for [target], from the loaded [settings]: the saved look, a copy of it, or a new look from a
         * premade, each new one named as [names] say and numbered when taken. Holding `null` when that look is gone.
         */
        fun held(sources: EditorSources, target: EditorTarget, names: EditorNames): Held {
            val saved = sources.settings.value
            val look = when (target) {
                is EditorTarget.Edit -> saved.look(target.lookId)

                is EditorTarget.Copy -> saved.look(target.lookId)?.let { original ->
                    original.copy(id = UUID.randomUUID().toString(), name = saved.freshName(names.copy(original.name)))
                }

                is EditorTarget.New -> target.premade.let { premade ->
                    premade.look(id = UUID.randomUUID().toString(), name = saved.freshName(names.premade(premade)))
                }
            }
            return Held(
                look?.let { LookEditorState(sources, it, isNew = target !is EditorTarget.Edit) },
            )
        }

        private const val LEAVE = "leave"
        private const val RENAME = "rename"

        /** A dialog as its kind, a premade's name for a switch. */
        private fun saved(dialog: EditorDialog): String = when (dialog) {
            EditorDialog.Leave -> LEAVE
            EditorDialog.Rename -> RENAME
            is EditorDialog.Switch -> dialog.premade.name
        }

        private fun restored(saved: String): EditorDialog? = when (saved) {
            LEAVE -> EditorDialog.Leave
            RENAME -> EditorDialog.Rename
            else -> saved.toEnumOrNull<Premade>()?.let(EditorDialog::Switch)
        }
    }
}

/** How new looks are named, as the app's strings say: a premade's own name, and "Work copy". */
internal class EditorNames(val premade: (Premade) -> String, val copy: (String) -> String)

/**
 * The editor's state for [target], from the stored settings, which must have been read ([MainViewModel.storedSettings]
 * not `null`: before that its looks are placeholders); `null` when that look is gone.
 */
@Composable
internal fun rememberLookEditorState(viewModel: MainViewModel, target: EditorTarget): LookEditorState? {
    val stored = viewModel.storedSettings.collectAsStateWithLifecycle()
    val settings = remember(stored) { derivedStateOf { stored.value ?: GlowSettings() } }
    val uiPrefs = viewModel.uiPrefs.collectAsStateWithLifecycle()
    val apps = viewModel.apps.collectAsStateWithLifecycle()
    val drafts = LocalContext.current.lookDrafts
    val resources = LocalResources.current
    val sources = remember(viewModel) {
        EditorSources(viewModel::preview, { viewModel.updateUi(it) }, drafts, settings, uiPrefs, apps)
    }
    val saver = remember(sources) { LookEditorState.saver(sources) }
    return rememberSaveable(saver = saver) {
        val names = EditorNames(
            premade = { resources.getString(it.style.titleRes) },
            copy = { resources.getString(R.string.look_copy_name, it) },
        )
        LookEditorState.held(sources, target, names)
    }.editor
}

/** The editor's state, or `null` for none, as saveable state holds it (never `null` itself). */
internal class Held(val editor: LookEditorState?)
