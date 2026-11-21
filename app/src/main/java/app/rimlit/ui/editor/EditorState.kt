package app.rimlit.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rimlit.MainViewModel
import app.rimlit.data.AppInfo
import app.rimlit.data.decodeLooks
import app.rimlit.data.encodeLooks
import app.rimlit.engine.PreviewTarget
import app.rimlit.model.GlowSettings
import app.rimlit.model.GlowSpec
import app.rimlit.model.Look
import app.rimlit.model.LookPart
import app.rimlit.model.Premade
import app.rimlit.model.UseFor
import app.rimlit.model.fitLookName
import app.rimlit.model.isCustom
import app.rimlit.model.numberedName
import app.rimlit.model.switchedTo
import app.rimlit.model.useFor
import app.rimlit.model.withImages
import app.rimlit.ui.title
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
 * The look editor's state, and what it does: the draft [look], where it applies ([applies]), the images imported for
 * it, the open tab and dialog. Nothing is written until [save]; [discard] lets go of the draft's images. Kept across
 * configuration changes and process death by [rememberLookEditorState]. Main thread only.
 */
@Stable
internal class LookEditorState(
    private val viewModel: MainViewModel,
    private val settingsState: State<GlowSettings>,
    private val appsState: State<List<AppInfo>>,
    look: Look,
    isNew: Boolean,
) {
    var look by mutableStateOf(look)
        private set

    /** Not saved yet: a new look, or one carried on from a copy of the look opened. */
    var isNew by mutableStateOf(isNew)
        private set

    /** Where the user set the look to apply; `null` while unchanged (as saved, or nowhere for a new look). */
    private var useFor by mutableStateOf<UseFor?>(null)

    /** The images imported for this draft, kept from the clean-up until it is saved or discarded. */
    private var draftImages by mutableStateOf(emptySet<String>())

    /** A save is in flight: another is ignored meanwhile. */
    var saving by mutableStateOf(false)
        private set

    /** The draft was saved or discarded: the editor is gone, and images imported after that are let go of. */
    private var left = false

    var tab by mutableStateOf(EditorTab.BASE)
    var offScreen by mutableStateOf(false)
    var dialog by mutableStateOf<EditorDialog?>(null)
    var appQuery by mutableStateOf("")

    val settings: GlowSettings get() = settingsState.value
    val apps: List<AppInfo> get() = appsState.value

    /** Where the look applies once saved. */
    var applies: UseFor
        get() = useFor ?: if (isNew) UseFor(isDefault = false, apps = emptySet()) else settings.useFor(look.id)
        set(value) {
            useFor = value
        }

    val unsaved: Boolean
        get() = isNew || look != settings.look(look.id) || applies != settings.useFor(look.id)

    /** The open tab shows every control: its "Tinker" is on. */
    val tinkering: Boolean get() = tab.part in settings.tinkering

    fun edit(look: Look) {
        this.look = look
    }

    /**
     * Draws the images [ids], imported for this draft, among the look's particles; once the editor is left (the import
     * outlived it), they are let go of instead.
     */
    fun addImages(ids: List<String>) {
        if (left) return viewModel.drafts.discard(ids.toSet())
        draftImages = draftImages + ids
        look = look.copy(particles = look.particles.withImages(ids))
    }

    /** Switches the look to [premade]: at once while untouched, else after asking, since its changes are lost. */
    fun pickPremade(premade: Premade) {
        if (look.isCustom) dialog = EditorDialog.Switch(premade) else look = look.switchedTo(premade)
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
        val name = fitLookName(copyName)
        look = look.copy(id = UUID.randomUUID().toString(), name = settings.numberedName(name) ?: name)
        isNew = true
        useFor = null
    }

    /** Turns the open tab's "Tinker" on or off, remembered at once (it isn't part of the look). */
    fun tinker(on: Boolean) {
        val part = tab.part ?: return
        viewModel.update { it.copy(tinkering = if (on) it.tinkering + part else it.tinkering - part) }
    }

    /** Plays [spec], the draft as this device draws it, on the real surface the editor shows: on screen, or off. */
    fun sendTest(spec: GlowSpec) {
        viewModel.preview(if (offScreen) PreviewTarget.LOCK_SCREEN else PreviewTarget.ON_SCREEN, spec)
    }

    /**
     * Saves the draft and where it applies; [onDone] says whether it was saved. One at a time: a save asked for while
     * another is in flight is ignored, so the editor is never left twice.
     */
    fun save(onDone: (saved: Boolean) -> Unit) {
        if (saving) return
        dialog = null
        saving = true
        viewModel.drafts.save(look, applies, draftImages) { saved ->
            saving = false
            left = saved
            onDone(saved)
        }
    }

    /** Leaves: at once with nothing unsaved ([onLeave]), else after asking ([EditorDialog.Leave]). */
    fun leave(onLeave: () -> Unit) {
        if (unsaved) dialog = EditorDialog.Leave else discard(onLeave)
    }

    /** Drops the draft and its images, then [onLeave]. */
    fun discard(onLeave: () -> Unit) {
        dialog = null
        left = true
        viewModel.drafts.discard(draftImages)
        onLeave()
    }

    companion object {
        /** Keeps the draft as the settings store a look, decoded as tolerantly, so a restored draft is always valid. */
        fun saver(
            viewModel: MainViewModel,
            settings: State<GlowSettings>,
            apps: State<List<AppInfo>>,
        ): Saver<Held, Any> = Saver(
            save = { held ->
                held.editor?.run {
                    arrayListOf(
                        encodeLooks(listOf(look)),
                        isNew,
                        useFor?.let { arrayListOf(it.isDefault) + it.apps },
                        ArrayList(draftImages),
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
                val editor = LookEditorState(viewModel, settings, apps, look, isNew = fields[1] as Boolean).apply {
                    useFor = (fields[2] as List<*>?)?.let { applied ->
                        UseFor(applied.first() as Boolean, applied.drop(1).map(Any?::toString).toSet())
                    }
                    // The library let go of them with the process: keep them again until saved or discarded.
                    draftImages = (fields[3] as List<*>).map(Any?::toString).toSet()
                    viewModel.drafts.keep(draftImages)
                    tab = EditorTab.entries[fields[4] as Int]
                    offScreen = fields[5] as Boolean
                    dialog = (fields[6] as String?)?.let(::restored)
                    appQuery = fields[7] as String
                }
                Held(editor)
            },
        )

        /**
         * The state for the saved look [lookId], or a new one from [premade] named [name] (numbered when taken), held
         * for saveable state; holding `null` when that look is gone or neither was asked for.
         */
        fun held(
            viewModel: MainViewModel,
            settings: State<GlowSettings>,
            apps: State<List<AppInfo>>,
            lookId: String?,
            premade: Premade?,
            name: String?,
        ): Held {
            val look = when {
                lookId != null -> settings.value.look(lookId)

                premade != null && name != null -> {
                    val fitted = fitLookName(name)
                    premade.look(
                        id = UUID.randomUUID().toString(),
                        name = settings.value.numberedName(fitted) ?: fitted,
                    )
                }

                else -> null
            }
            return Held(look?.let { LookEditorState(viewModel, settings, apps, it, isNew = lookId == null) })
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
            else -> Premade.entries.firstOrNull { it.name == saved }?.let(EditorDialog::Switch)
        }
    }
}

/**
 * The editor's state for the saved look [lookId], or a new one from [premade] (named after it, numbered when taken);
 * `null` when that look is gone or neither was asked for.
 */
@Composable
internal fun rememberLookEditorState(viewModel: MainViewModel, lookId: String?, premade: Premade?): LookEditorState? {
    val settings = viewModel.settings.collectAsStateWithLifecycle()
    val apps = viewModel.apps.collectAsStateWithLifecycle()
    val name = premade?.title()
    val saver = remember(viewModel) { LookEditorState.saver(viewModel, settings, apps) }
    return rememberSaveable(saver = saver) {
        LookEditorState.held(viewModel, settings, apps, lookId, premade, name)
    }.editor
}

/** The editor's state, or `null` for none, as saveable state holds it (never `null` itself). */
internal class Held(val editor: LookEditorState?)
