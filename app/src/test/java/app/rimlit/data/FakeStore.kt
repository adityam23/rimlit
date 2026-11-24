package app.rimlit.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.io.IOException

/**
 * A settings store holding its preferences in memory, emitting each change as DataStore does (a write that changes
 * nothing emits nothing), whose reads and writes fail while [failing], as a disk error would make them.
 */
internal class FakeStore(stored: Preferences = emptyPreferences()) : DataStore<Preferences> {
    var failing = false

    private val stored = MutableStateFlow(stored)

    override val data: Flow<Preferences> = flow {
        check()
        emitAll(this@FakeStore.stored)
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        check()
        return transform(stored.value).also { stored.value = it }
    }

    private fun check() {
        if (failing) throw IOException("Disk error")
    }
}
