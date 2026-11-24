package com.lakefront.tvlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import com.lakefront.tvlauncher.logic.AppEntry
import com.lakefront.tvlauncher.logic.ComponentKey
import com.lakefront.tvlauncher.logic.KnownHandlers
import com.lakefront.tvlauncher.logic.LaunchableSelector
import com.lakefront.tvlauncher.logic.RawLaunchable
import com.lakefront.tvlauncher.logic.ShortcutDecision
import com.lakefront.tvlauncher.logic.ShortcutMatch

data class AppCatalog(
    val apps: List<AppEntry>,
    val files: ShortcutDecision = ShortcutDecision(),
    val store: ShortcutDecision = ShortcutDecision(),
)

class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    fun load(
        rememberedFile: ComponentKey? = null,
        rememberedStore: ComponentKey? = null,
    ): AppCatalog {
        val leanback = query(Intent.CATEGORY_LEANBACK_LAUNCHER, leanback = true)
        val launcher = query(Intent.CATEGORY_LAUNCHER, leanback = false)
        val settings = query("android.intent.category.LEANBACK_SETTINGS", leanback = true)
            .map { "${it.packageName}/${it.activityName}" }
            .toSet()
        val apps = LaunchableSelector.select(
            entries = leanback + launcher,
            selfPackage = appContext.packageName,
            excludedPackages = KnownHandlers.settingsPackages,
            excludedComponents = settings,
        ).sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }, { it.activityName }))

        val market = marketPackages()
        val files = ShortcutMatch.choose(
            scored = apps.map { entry ->
                entry to ShortcutMatch.fileManagerScore(entry.packageName, entry.activityName, entry.label)
            },
            remembered = rememberedFile,
        )
        val stores = ShortcutMatch.stores(
            candidates = apps.map { entry ->
                entry to ShortcutMatch.storeScore(
                    packageName = entry.packageName,
                    label = entry.label,
                    handlesMarket = entry.packageName in market,
                )
            },
            remembered = rememberedStore,
        )
        return AppCatalog(apps = apps, files = files, store = stores)
    }

    private fun query(category: String, leanback: Boolean): List<RawLaunchable> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        val resolved = try {
            packageManager.queryIntentActivities(intent, 0)
        } catch (_: Exception) {
            emptyList()
        }
        return resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            if (!activity.enabled || !activity.exported) return@mapNotNull null
            val appInfo = activity.applicationInfo ?: return@mapNotNull null
            if (!appInfo.enabled) return@mapNotNull null
            val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                .ifEmpty { activity.packageName }
            val flags = appInfo.flags
            RawLaunchable(
                packageName = activity.packageName,
                activityName = activity.name,
                label = label,
                leanback = leanback,
                system = flags and ApplicationInfo.FLAG_SYSTEM != 0,
                updatedSystem = flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
            )
        }
    }

    private fun marketPackages(): List<String> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=android"))
        return try {
            packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun RawLaunchable.toEntry(): AppEntry = AppEntry(
        packageName = packageName,
        activityName = activityName,
        label = label,
        leanback = leanback,
        system = system,
        updatedSystem = updatedSystem,
    )
}
