package app.rimlit.ui

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LookActionSaverTest {

    private val scope = SaverScope { true }

    private fun roundTrip(action: LookAction?): LookAction? {
        val saved = with(LookAction.Saver) { scope.save(action) } ?: return null
        return LookAction.Saver.restore(saved)
    }

    @Test
    fun everyActionSurvivesAConfigurationChange() {
        val actions = listOf(
            LookAction.Add("work", duplicate = false),
            LookAction.Add("work", duplicate = true),
            LookAction.Rename("work"),
            LookAction.Delete("work"),
        )
        for (action in actions) assertEquals(action, roundTrip(action))
        assertNull(roundTrip(null))
    }

    @Test
    fun aMalformedSavedActionRestoresAsNone() {
        assertNull(LookAction.Saver.restore(listOf("unknown", "work")))
        assertNull(LookAction.Saver.restore(listOf("rename")))
    }
}
