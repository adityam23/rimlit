package app.rimlit.data

import android.content.Context
import android.content.pm.PackageManager
import app.rimlit.R
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.fitLookName
import app.rimlit.model.isValidLookName

/**
 * How the looks older per-app palettes migrate into are named (see `PaletteLooks.kt`), from their apps' names: only
 * the lookups, the label and the translated name; [fittedLookName] cuts the name to fit.
 */
internal interface PaletteLookNames {

    /** The name [packageName]'s app is shown by: its label, or the package name when it has none. */
    fun label(packageName: String): String

    /**
     * The name, uncut, of a look in the colours of the app named [label], shared with [others] more apps: "WhatsApp
     * colors", or "WhatsApp + 2 colors".
     */
    fun lookName(label: String, others: Int): String
}

/**
 * The look name of the app named [label] and [others] more (see [PaletteLookNames.lookName]), the label cut so the
 * rest of the name always fits; `null` when that isn't a valid look name, as when the label holds an unpaired
 * surrogate.
 */
internal fun PaletteLookNames.fittedLookName(label: String, others: Int): String? {
    // At least a character, however long a translation's words: the whole name is cut to fit after.
    val room = (Ranges.lookNameLength.last - lookName("", others).length).coerceAtLeast(1)
    return fitLookName(lookName(fitLookName(label, room), others)).takeIf(::isValidLookName)
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

    override fun lookName(label: String, others: Int): String = if (others == 0) {
        resources.getString(R.string.palette_look_name, label)
    } else {
        resources.getQuantityString(R.plurals.palette_look_shared_name, others, label, others)
    }
}
