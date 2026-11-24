package com.lakefront.tvlauncher.logic

data class RawLaunchable(
    val packageName: String,
    val activityName: String,
    val label: String,
    val leanback: Boolean,
    val system: Boolean = false,
    val updatedSystem: Boolean = false,
)

data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val leanback: Boolean,
    val system: Boolean,
    val updatedSystem: Boolean,
) {
    val key: ComponentKey get() = ComponentKey(packageName, activityName)
    val id: String get() = key.flatten()
}

object LaunchableSelector {
    /**
     * One sensible entry per package when the app only has a phone launcher activity.
     * When an app publishes one or more leanback activities, those are kept and the
     * phone launcher alias is dropped. This launcher and settings packages are removed
     * so Settings is not duplicated next to the status pill.
     */
    fun select(
        entries: List<RawLaunchable>,
        selfPackage: String,
        excludedPackages: Set<String>,
        excludedComponents: Set<String>,
    ): List<AppEntry> {
        val filtered = entries.filter { entry ->
            entry.packageName.isNotBlank() &&
                entry.activityName.isNotBlank() &&
                entry.packageName != selfPackage &&
                entry.packageName !in excludedPackages &&
                "${entry.packageName}/${entry.activityName}" !in excludedComponents
        }
        return filtered
            .groupBy { it.packageName }
            .flatMap { (_, items) ->
                val leanback = items.filter { it.leanback }.distinctBy { it.activityName }
                val chosen = if (leanback.isNotEmpty()) leanback else listOf(items.first())
                chosen.map { it.toEntry() }
            }
    }
}

private fun RawLaunchable.toEntry(): AppEntry = AppEntry(
    packageName = packageName,
    activityName = activityName,
    label = label.ifBlank { packageName },
    leanback = leanback,
    system = system,
    updatedSystem = updatedSystem,
)

object HandlerPicker {
    /**
     * Orders candidates by a preferred package list, then appends any remaining
     * packages once each. Used for File Manager and App Store shortcuts.
     */
    fun pick(priorityPackages: List<String>, candidates: List<AppEntry>): List<AppEntry> {
        val distinct = candidates.distinctBy { it.id }
        val byPackage = distinct.groupBy { it.packageName }
        val used = HashSet<String>()
        val ordered = ArrayList<AppEntry>()
        for (pkg in priorityPackages) {
            val hit = byPackage[pkg]?.firstOrNull() ?: continue
            if (used.add(pkg)) ordered += hit
        }
        for (candidate in distinct) {
            if (used.add(candidate.packageName)) ordered += candidate
        }
        return ordered
    }
}

object KnownHandlers {
    val fileManagerPackages: List<String> = listOf(
        "com.google.android.apps.nbu.files",
        "com.android.documentsui",
        "com.google.android.documentsui",
        "nextapp.fx",
        "com.estrongs.android.pop",
        "com.alphainventor.filemanager",
        "com.ghisler.android.TotalCommander",
        "pl.solidexplorer2",
        "com.amaze.filemanager",
        "com.mixplorer.silver",
        "com.speedsoftware.rootexplorer",
        "com.cvinfo.filemanager",
        "com.newlink.filemanager",
    )

    val documentActivities: Set<String> = setOf(
        "com.android.documentsui.files.FilesActivity",
        "com.android.documentsui.LauncherActivity",
        "com.android.documentsui.FilesActivity",
    )

    val storePackages: List<String> = listOf(
        "com.android.vending",
        "com.amazon.venezia",
        "org.fdroid.fdroid",
        "com.aurora.store",
        "cm.aptoide.pt",
        "zeasn.open.technical.tv.store",
    )

    val settingsPackages: Set<String> = setOf(
        "com.android.settings",
        "com.android.tv.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.systemui",
        "android",
    )
}
