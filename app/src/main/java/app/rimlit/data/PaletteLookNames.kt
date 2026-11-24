package app.rimlit.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import app.rimlit.R
import app.rimlit.model.GlowSettings.Ranges
import app.rimlit.model.fitLookName
import app.rimlit.model.isValidLookName
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "PaletteLookNames"

/**
 * How the looks older per-app palettes migrate into are named (see `PaletteLooks.kt`), from their apps' names: only
 * the lookups, the label and the translated name; [fittedLookName] cuts the name to fit.
 */
internal interface PaletteLookNames {

    /**
     * The name [packageName]'s app is shown by: its label, or the package name when it has none. It may throw, as a
     * system call does when the system is dying; read it through [labelOrPackageName].
     */
    fun label(packageName: String): String

    /** Reports that looking up [packageName]'s label failed with [failure]: its look is named by the package. */
    fun labelFailed(packageName: String, failure: Exception)

    /**
     * The name, uncut, of a look in the colours of the app named [label], shared with [others] more apps: "WhatsApp
     * colors", or "WhatsApp + 2 colors".
     */
    fun lookName(label: String, others: Int): String
}

/**
 * [packageName]'s [PaletteLookNames.label], or the package name when the lookup fails, reported: the migration runs
 * before the settings are first read, so a failure there would keep them from ever loading. Cancellation and errors
 * still propagate.
 */
internal fun PaletteLookNames.labelOrPackageName(packageName: String): String =
    runCatching { label(packageName) }.getOrElse { failure ->
        if (failure !is Exception || failure is CancellationException) throw failure
        labelFailed(packageName, failure)
        packageName
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

    override fun labelFailed(packageName: String, failure: Exception) {
        Log.w(TAG, "Couldn't look up $packageName's label; naming its palette look by the package", failure)
    }

    override fun lookName(label: String, others: Int): String = if (others == 0) {
        resources.getString(R.string.palette_look_name, label)
    } else {
        resources.getQuantityString(R.plurals.palette_look_shared_name, others, label, others)
    }
}
