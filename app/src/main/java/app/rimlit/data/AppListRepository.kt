package app.rimlit.data

import android.content.Context
import android.content.Intent

/** A launchable app shown in the blocklist. Icons are rendered as letter avatars, not stored. */
data class AppInfo(val packageName: String, val label: String)

/** The apps whose name holds [query], ignoring case; all of them for a blank one. The one app search. */
fun List<AppInfo>.matching(query: String): List<AppInfo> =
    if (query.isBlank()) this else filter { it.label.contains(query.trim(), ignoreCase = true) }

class AppListRepository(private val context: Context) {

    fun launchableApps(): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName) return@mapNotNull null
                val label = resolveInfo.loadLabel(pm).toString().ifEmpty { packageName }
                AppInfo(packageName = packageName, label = label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
