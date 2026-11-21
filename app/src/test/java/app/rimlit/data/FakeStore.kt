package app.rimlit.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

/** A settings store holding [stored] in memory, whose reads fail while [failing], as a disk error would make them. */
internal class FakeStore(var stored: Preferences = emptyPreferences()) : DataStore<Preferences> {
    var failing = false

    override val data: Flow<Preferences> = flow { emit(read()) }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(read()).also { stored = it }

    private fun read(): Preferences = if (failing) throw IOException("Disk error") else stored
}
