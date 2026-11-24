package com.lakefront.tvlauncher.logic

data class ShortcutDecision(
    val launch: AppEntry? = null,
    val tile: AppEntry? = null,
    val choices: List<AppEntry> = emptyList(),
) {
    val needsChoice: Boolean get() = launch == null && choices.size > 1
}

object ShortcutMatch {
    const val PLAY_STORE = "com.android.vending"
    private const val MIN_SCORE = 50
    private const val CLEAR_GAP = 20

    /** OEM file browsers that publish a custom start action instead of APP_FILES. */
    fun preferredLaunchAction(packageName: String): String? = when (packageName) {
        "com.newlink.filemanager" -> "com.newlink.startfilemanager"
        else -> null
    }

    fun fileManagerScore(packageName: String, activityName: String, label: String): Int {
        if (isDocumentPicker(activityName)) return 0
        val pkg = packageName.lowercase()
        val activity = activityName.lowercase()
        val name = label.lowercase()
        if (pkg == "com.android.documentsui" || pkg == "com.google.android.documentsui") {
            val browse = activity.endsWith("filesactivity") || activity.endsWith("launcheractivity")
            return if (browse) 60 else 0
        }
        if (pkg in KnownHandlers.fileManagerPackages) return 100
        var score = 0
        val packageHints = listOf(
            "filemanager",
            "file_manager",
            "fileexplorer",
            "file_explorer",
            "filebrowser",
            "filebrower",
            "file.manager",
            "file.explorer",
        )
        if (packageHints.any { it in pkg }) score = 85
        val strongLabels = listOf(
            "文件管理",
            "文件浏览器",
            "file manager",
            "file explorer",
            "my files",
            "files by google",
        )
        if (strongLabels.any { name.contains(it) }) score = maxOf(score, 80)
        return score
    }

    fun storeScore(packageName: String, label: String, handlesMarket: Boolean): Int {
        val pkg = packageName.lowercase()
        val name = label.lowercase()
        if (pkg == PLAY_STORE) return 100
        if (pkg in KnownHandlers.storePackages) return 90
        val strong = listOf("应用商店", "应用市场", "app store", "play store", "app market")
        if (strong.any { name.contains(it) }) return 75
        if (handlesMarket) return 70
        return 0
    }

    fun choose(
        scored: List<Pair<AppEntry, Int>>,
        remembered: ComponentKey? = null,
        force: AppEntry? = null,
    ): ShortcutDecision {
        if (force != null) return ShortcutDecision(launch = force, tile = force)
        val ranked = scored
            .filter { it.second >= MIN_SCORE }
            .sortedByDescending { it.second }
            .distinctBy { it.first.packageName }
        if (ranked.isEmpty()) return ShortcutDecision()
        val rememberedHit = remembered?.let { key ->
            ranked.firstOrNull { it.first.packageName == key.packageName }?.first
        }
        if (rememberedHit != null) {
            return ShortcutDecision(launch = rememberedHit, tile = rememberedHit)
        }
        if (ranked.size == 1) {
            return ShortcutDecision(launch = ranked[0].first, tile = ranked[0].first)
        }
        if (ranked[0].second - ranked[1].second >= CLEAR_GAP) {
            return ShortcutDecision(launch = ranked[0].first, tile = ranked[0].first)
        }
        return ShortcutDecision(choices = ranked.map { it.first })
    }

    fun stores(candidates: List<Pair<AppEntry, Int>>, remembered: ComponentKey? = null): ShortcutDecision {
        val play = candidates.firstOrNull { it.first.packageName == PLAY_STORE && it.second >= MIN_SCORE }?.first
        return choose(candidates, remembered, force = play)
    }

    private fun isDocumentPicker(activityName: String): Boolean {
        val activity = activityName.lowercase()
        val picker = listOf("picker", "getcontent", "opendocument", "storageaccess")
        return picker.any { it in activity }
    }
}

object PinPolicy {
    fun isFixedHomePackage(packageName: String, filePackage: String?, storePackage: String?): Boolean =
        packageName.isNotBlank() && (packageName == filePackage || packageName == storePackage)

    /** Second tap on a user pin removes it. Fixed home shortcuts are not in this list. */
    fun toggle(pins: List<ComponentKey>, key: ComponentKey): List<ComponentKey> {
        val without = pins.filterNot { it.packageName == key.packageName }
        return if (without.size == pins.size) PinList.add(pins, key) else without
    }

    fun withoutFixed(pins: List<ComponentKey>, filePackage: String?, storePackage: String?): List<ComponentKey> =
        pins.filterNot { isFixedHomePackage(it.packageName, filePackage, storePackage) }
}

object SettingsTargets {
    fun isAppDetailsClass(className: String): Boolean {
        val name = className.lowercase()
        val hints = listOf(
            "installedappdetails",
            "appmanagement",
            "applicationdetails",
            "appdetails",
            "appinfoactivity",
            "manageapplications",
            "appstorage",
            "installedapp",
        )
        return hints.any { it in name }
    }

    fun isSettingsHomeClass(className: String): Boolean {
        if (isAppDetailsClass(className)) return false
        val simple = className.substringAfterLast('.').lowercase()
        val homes = setOf(
            "settings",
            "tvsettings",
            "tvsettingsactivity",
            "mainsettings",
            "settingsactivity",
            "networksettings",
            "bluetoothsettings",
            "wifisettings",
        )
        if (simple in homes) return true
        return simple == "mainactivity" && "settings" in className.lowercase()
    }
}

object PackageUris {
    /** Opaque `package:<name>` data. Blank names are rejected so uninstall cannot send `package:`. */
    fun opaquePackage(packageName: String): String? {
        val pkg = packageName.trim()
        if (pkg.isEmpty() || pkg.any { it.isWhitespace() || it == '/' || it == ':' }) return null
        return "package:$pkg"
    }
}

object UninstallTargets {
    fun isUninstallerClass(className: String): Boolean {
        val name = className.lowercase()
        return "uninstall" in name || "packageinstaller" in name || "deletedialog" in name
    }

    /** System confirmation only. Do not ask the installer to return a result, which can skip the UI. */
    fun allowsSilentExtra(extras: Set<String>): Boolean = "android.intent.extra.RETURN_RESULT" !in extras
}

object MenuKeyPolicy {
    const val KEYCODE_MENU = 82
    const val KEYCODE_TV_CONTENTS_MENU = 256

    fun shouldConsume(keyCode: Int): Boolean =
        keyCode == KEYCODE_MENU || keyCode == KEYCODE_TV_CONTENTS_MENU
}
