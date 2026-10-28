package app.rimlit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.rimlit.model.GlowSettings
import kotlinx.coroutines.Job

internal typealias SettingsEdit = (GlowSettings) -> GlowSettings

/**
 * The screen's in-memory settings, changed by [preview] (re-renders only, mid-drag), [persist] (writes only, once the
 * drag ends) or [commit] (both, for a tap). They follow the saved settings only while none of the screen's own edits
 * is unsaved (a write in flight, or a drag not committed yet): the emission of an earlier write would otherwise
 * revert newer edits. Skipping an emission is safe, since once the writes land the draft already holds what they
 * saved, and any later emission is followed again. Main thread only.
 */
internal class SettingsDraft(initial: GlowSettings, private val save: (SettingsEdit) -> Job) {
    var value by mutableStateOf(initial)
        private set

    private var writes = 0
    private var dragging = false

    fun preview(edit: SettingsEdit) {
        dragging = true
        value = edit(value)
    }

    fun persist(edit: SettingsEdit) {
        dragging = false
        writes++
        save(edit).invokeOnCompletion { writes-- }
    }

    fun commit(edit: SettingsEdit) {
        value = edit(value)
        persist(edit)
    }

    fun follow(saved: GlowSettings) {
        if (writes == 0 && !dragging) value = saved
    }
}
