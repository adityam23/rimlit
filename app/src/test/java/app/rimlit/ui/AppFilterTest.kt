package app.rimlit.ui

import app.rimlit.data.AppInfo
import app.rimlit.model.GlowSettings
import app.rimlit.model.Look
import app.rimlit.model.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** The Apps list: customised apps first, each part in name order, and the filters by the mode. */
class AppFilterTest {

    private val apps = listOf("Alpha", "Bravo", "Charlie", "Delta").map { AppInfo("com.example.${it.lowercase()}", it) }
    private val work = Look(id = "work", name = "Work")
    private val settings = GlowSettings(
        looks = listOf(Look(), work),
        appRules = mapOf("com.example.delta" to Rule(work.id), "com.example.alpha" to Rule("gone")),
        blockedPackages = setOf("com.example.charlie"),
        allowedPackages = setOf("com.example.bravo"),
    )

    private fun List<AppInfo>.labels() = map { it.label }

    @Test
    fun customisedAppsComeFirstThenTheRestEachInNameOrder() {
        assertEquals(listOf("Charlie", "Delta", "Alpha", "Bravo"), apps.arranged(settings, AppFilter.ALL).labels())
        assertEquals(
            listOf("Bravo", "Delta", "Alpha", "Charlie"),
            apps.arranged(settings.copy(onlyChosenApps = true), AppFilter.ALL).labels(),
            "while only chosen apps glow, the ones switched on are the customised ones",
        )
    }

    @Test
    fun theFiltersFollowTheMode() {
        assertEquals(listOf("Delta"), apps.arranged(settings, AppFilter.OWN_LOOK).labels(), "a missing look is none")
        assertEquals(listOf("Charlie"), apps.arranged(settings, AppFilter.SWITCHED).labels(), "switched off")
        assertEquals(
            listOf("Bravo"),
            apps.arranged(settings.copy(onlyChosenApps = true), AppFilter.SWITCHED).labels(),
            "switched on",
        )
    }
}
