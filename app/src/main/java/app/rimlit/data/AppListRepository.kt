package app.rimlit.data

import android.content.Context
import android.content.Intent

/** A launchable app shown in the blocklist. Icons are rendered as letter avatars, not stored. */
data class AppInfo(val packageName: String, val label: String)

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
