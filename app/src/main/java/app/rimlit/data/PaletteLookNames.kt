package app.rimlit.data

import android.content.Context
import android.content.pm.PackageManager
import app.rimlit.R
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.fitLookName

/** How the looks older per-app palettes migrate into are named (see `PaletteLooks.kt`), from their apps' names. */
internal interface PaletteLookNames {

    /** The name [packageName]'s app is shown by: its label, or the package name when it has none. */
    fun label(packageName: String): String

    /** The name of a look used by the apps named [labels] (at least one, in order): a valid look name. */
    fun lookName(labels: List<String>): String
}

/** Names migrated palette looks after the apps' labels, as the app shows them. */
internal class AppPaletteLookNames(context: Context) : PaletteLookNames {
    private val packageManager = context.packageManager
    private val resources = context.resources

    override fun label(packageName: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            .ifBlank { packageName }
    } catch (_: PackageManager.NameNotFoundException) {
        packageName // Uninstalled since: its package name still says which app it was.
    }

    /** "WhatsApp colors", or "WhatsApp + 2 colors" when shared, the app's name cut so the rest always fits. */
    override fun lookName(labels: List<String>): String {
        val others = labels.size - 1
        fun named(label: String) = if (others == 0) {
            resources.getString(R.string.palette_look_name, label)
        } else {
            resources.getQuantityString(R.plurals.palette_look_shared_name, others, label, others)
        }
        // At least a character, however long a translation's words: the whole name is cut to fit after.
        val room = (Ranges.lookNameLength.last - named("").length).coerceAtLeast(1)
        return fitLookName(named(fitLookName(labels.first(), room)))
    }
}
