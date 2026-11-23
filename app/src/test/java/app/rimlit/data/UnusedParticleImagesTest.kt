package app.rimlit.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.rimlit.model.GlowSettings
import app.rimlit.model.Look
import app.rimlit.model.ParticleMotion
import app.rimlit.model.ParticlePicture
import app.rimlit.model.ParticleSpec
import app.rimlit.model.newParticleImageId
import app.rimlit.model.particleImageIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The clean-up of particle image files deletes only by settings that were read: never by a fallback. */
class UnusedParticleImagesTest {

    /** A settings store whose reads fail while [failing], as a disk error would make them. */
    private class FakeStore(private var stored: Preferences = emptyPreferences()) : DataStore<Preferences> {
        var failing = false

        override val data: Flow<Preferences> = flow { emit(read()) }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(read()).also { stored = it }

        private fun read(): Preferences = if (failing) throw IOException("Disk error") else stored
    }

    /** The image files, by id, as [ParticleImageStore.retainOnly] deletes them. */
    private class FakeImages(vararg ids: String) {
        var files = ids.toSet()
            private set

        fun retainOnly(keep: Set<String>) {
            files = files intersect keep
        }
    }

    private val used = List(2) { newParticleImageId() }
    private val unused = newParticleImageId()
    private val settings = GlowSettings(
        looks = listOf(Look(particles = ParticleSpec(ParticlePicture.Custom(used), ParticleMotion.FALL))),
    )
    private val store = FakeStore(mutablePreferencesOf().apply { write(settings) })
    private val repository = SettingsRepository(store)
    private val images = FakeImages(unused, *used.toTypedArray())

    private fun deleteUnused() = runBlocking { deleteUnusedParticleImages(repository, images::retainOnly) }

    @Test
    fun onlyTheImagesNoStoredLookDrawsAreDeleted() {
        deleteUnused()
        assertEquals(used.toSet(), images.files)
    }

    @Test
    fun aFailedReadDeletesNothing() {
        store.failing = true
        assertFailsWith<IOException> { deleteUnused() }
        assertFailsWith<IOException> { runBlocking { repository.stored() } }
        assertEquals(used.toSet() + unused, images.files, "the looks still draw them: defaults are not what is stored")

        store.failing = false
        deleteUnused()
        assertEquals(used.toSet(), images.files, "once the settings read again, by what they hold")
    }

    @Test
    fun aFailedWriteDeletesNothing() {
        store.failing = true
        assertFailsWith<IOException> { runBlocking { repository.update { GlowSettings() } } }
        store.failing = false
        deleteUnused()
        assertEquals(used.toSet(), images.files)
    }

    @Test
    fun aWriteReturnsTheSettingsWritten() {
        val written = runBlocking { repository.update { it.copy(looks = listOf(Look())) } }
        assertEquals(emptySet(), written.particleImageIds)
        assertEquals(written, runBlocking { repository.stored() })
        deleteUnused()
        assertEquals(emptySet(), images.files, "no look draws any image now")
    }
}
