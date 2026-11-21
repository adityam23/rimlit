package app.rimlit.data

import app.rimlit.model.ColorMode
import app.rimlit.model.GlowPalette
import app.rimlit.model.GlowSettings
import app.rimlit.model.MAX_LOOKS
import app.rimlit.model.Rule
import app.rimlit.model.addLookNumbered
import java.util.Locale

// Older versions could give an app the default look in colours of its own (a palette rule). Those rules migrate into
// looks: each app gets a copy of the default look in its colours, named after it, and a look rule. Past the look limit
// the apps last by name share one look per palette, and only apps that even that can't fit go back to the default.
// The migration runs on every read until the settings are next written, so it is deterministic: the same stored
// settings and app names always give the same looks, under the same ids. An app renamed or uninstalled between two
// reads may rename or regroup the looks until then, harmlessly: they hold no images, and nothing is kept until the
// settings are written. The looks are named by [PaletteLookNames].

/** These settings with [palettes] (by package name) migrated into looks, as described at the top of this file. */
internal fun GlowSettings.withPaletteLooks(
    palettes: Map<String, GlowPalette.Colors>,
    names: PaletteLookNames,
): GlowSettings {
    if (palettes.isEmpty()) return this
    val labels = palettes.keys.associateWith(names::label)
    val apps = palettes.keys.sortedWith(compareBy({ labels.getValue(it).lowercase(Locale.ROOT) }, { it }))
    val room = MAX_LOOKS - looks.size
    // As many apps as possible get looks of their own, while the rest still fit sharing one look per palette.
    val own = (apps.size downTo 0).firstOrNull { k -> k + sharedPalettes(apps, k, palettes).size <= room } ?: 0

    var settings = this
    val lookOf = mutableMapOf<GlowPalette.Colors, String>()
    fun use(id: String, users: List<String>) {
        settings = settings.copy(appRules = settings.appRules + users.associateWith { Rule.UseLook(id) })
    }
    fun addLook(id: String, palette: GlowPalette.Colors, users: List<String>) {
        val look = defaultLook.copy(colorMode = ColorMode.CUSTOM, customColors = palette)
        val added = settings.addLookNumbered(look, names.lookName(users.map(labels::getValue)), id)
        // Not added (no room, or a look of the user's already has the id): these apps keep the default look.
        if (added.looks.size == settings.looks.size) return
        settings = added
        lookOf.putIfAbsent(palette, id)
        use(id, users)
    }
    for (app in apps.take(own)) addLook(id = "colours:$app", palette = palettes.getValue(app), users = listOf(app))
    for ((palette, users) in apps.drop(own).groupBy(palettes::getValue)) {
        lookOf[palette]?.let { use(it, users) }
            ?: addLook(
                id = "colours:" + palette.argb.joinToString(",") { "%08x".format(Locale.ROOT, it) },
                palette,
                users,
            )
    }
    return settings
}

/** The palettes the apps after the first [own] of [apps] need a shared look for: those none of the first uses. */
private fun sharedPalettes(apps: List<String>, own: Int, palettes: Map<String, GlowPalette.Colors>) =
    apps.drop(own).map(palettes::getValue).toSet() - apps.take(own).map(palettes::getValue).toSet()
