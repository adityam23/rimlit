package app.rimlit.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.rimlit.model.GlowSettings
import app.rimlit.model.LookPart
import app.rimlit.model.UiPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import kotlin.test.assertEquals

/** The settings and the UI preferences share one store, each with its own flow and update, neither waking the other. */
class SettingsRepositoryTest {

    private val store = FakeStore()
    private val repository = SettingsRepository(store)

    private val prefs =
        UiPrefs(tinkering = setOf(LookPart.COLOUR), alwaysTinker = true, dismissedPermissions = setOf("BATTERY"))

    /** Runs [block] while every emission of [flow] is collected, as they come, into the list it gets. */
    private fun <T> collecting(flow: Flow<T>, block: suspend CoroutineScope.(List<T>) -> Unit) = runBlocking {
        val emitted = mutableListOf<T>()
        val collector = launch { flow.collect { emitted += it } }
        yield()
        block(emitted)
        collector.cancel()
    }

    /** Lets the collectors take what was just written. */
    private suspend fun settle() = repeat(3) { yield() }

    @Test
    fun eachReadsBackWhatItsOwnUpdateWrote() = runBlocking {
        val settings = repository.update { it.copy(durationMs = 9_000L) }
        assertEquals(prefs, repository.updateUi { prefs })
        assertEquals(settings, repository.stored(), "a UI write keeps the settings")
        repository.update { it.copy(showPill = false) }
        assertEquals(prefs, repository.uiPrefs.first(), "a settings write keeps the UI preferences")
        assertEquals(settings.copy(showPill = false), repository.settings.first())
    }

    @Test
    fun aUiWriteDoesNotEmitTheSettingsAgain() = collecting(repository.settings) { emitted ->
        repository.updateUi { prefs }
        settle()
        repository.updateUi { it.copy(tinkering = emptySet()) }
        settle()
        assertEquals(listOf(GlowSettings()), emitted)
        repository.update { it.copy(durationMs = 9_000L) }
        settle()
        assertEquals(listOf(GlowSettings(), GlowSettings(durationMs = 9_000L)), emitted, "a settings write does")
    }

    @Test
    fun aSettingsWriteDoesNotEmitTheUiPrefsAgain() = collecting(repository.uiPrefs) { emitted ->
        repository.update { it.copy(durationMs = 9_000L) }
        settle()
        assertEquals(listOf(UiPrefs()), emitted)
        repository.updateUi { prefs }
        settle()
        assertEquals(listOf(UiPrefs(), prefs), emitted, "a UI write does")
    }

    @Test
    fun theTinkeringTabsReadBackTheSameAndUnknownOnesAreDropped() {
        val stored = mutablePreferencesOf().apply { write(prefs) }
        assertEquals(prefs, stored.toUiPrefs())
        stored[stringSetPreferencesKey("tinkering")] = setOf("COLOUR", "SOMETHING_NEWER")
        assertEquals(setOf(LookPart.COLOUR), stored.toUiPrefs().tinkering)
    }

    @Test
    fun alwaysTinkerStoredAsASettingReadsBackAsAUiPreference() {
        val older = mutablePreferencesOf(booleanPreferencesKey("always_tinker") to true)
        assertEquals(UiPrefs(alwaysTinker = true), older.toUiPrefs())
    }

    @Test
    fun alwaysTinkerShowsEveryTabsControlsAndKeepsEachTabsOwnChoice() {
        val tinkering = UiPrefs(tinkering = setOf(LookPart.COLOUR))
        assertEquals(listOf(LookPart.COLOUR), LookPart.entries.filter(tinkering::tinkers))
        val always = tinkering.copy(alwaysTinker = true)
        assertEquals(LookPart.entries, LookPart.entries.filter(always::tinkers))
        assertEquals(tinkering, always.copy(alwaysTinker = false), "turning it off brings back each tab's choice")
    }
}
