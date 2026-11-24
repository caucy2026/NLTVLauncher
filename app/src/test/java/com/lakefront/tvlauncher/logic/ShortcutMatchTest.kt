package com.lakefront.tvlauncher.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutMatchTest {
    private fun app(pkg: String, label: String, activity: String = "$pkg.Main") = AppEntry(
        packageName = pkg,
        activityName = activity,
        label = label,
        leanback = true,
        system = false,
        updatedSystem = false,
    )

    @Test
    fun chineseFileManagerMatchesAndPickerDoesNot() {
        val oem = app("com.softwinner.TvdFileManager", "文件管理器", "com.softwinner.TvdFileManager.Main")
        val picker = app(
            "com.android.documentsui",
            "Documents",
            "com.android.documentsui.picker.PickActivity",
        )
        assertTrue(ShortcutMatch.fileManagerScore(oem.packageName, oem.activityName, oem.label) >= 80)
        assertEquals(0, ShortcutMatch.fileManagerScore(picker.packageName, picker.activityName, picker.label))
        val decision = ShortcutMatch.choose(
            listOf(
                oem to ShortcutMatch.fileManagerScore(oem.packageName, oem.activityName, oem.label),
                picker to 0,
            ),
        )
        assertEquals(oem.packageName, decision.launch?.packageName)
        assertTrue(decision.choices.isEmpty())
    }

    @Test
    fun ambiguousManagersAskOnceAndRememberedChoiceLaunches() {
        val a = app("com.oem.filemanager", "文件管理器")
        val b = app("com.other.filemanager", "File Manager")
        val scored = listOf(a, b).map { it to 80 }
        val first = ShortcutMatch.choose(scored)
        assertNull(first.launch)
        assertEquals(2, first.choices.size)
        val again = ShortcutMatch.choose(scored, remembered = b.key)
        assertEquals(b.packageName, again.launch?.packageName)
    }

    @Test
    fun playStoreLaunchesDirectlyEvenIfOemStoreExists() {
        val play = app(ShortcutMatch.PLAY_STORE, "Play Store")
        val oem = app("com.oem.appstore", "应用商店")
        val decision = ShortcutMatch.stores(
            listOf(
                play to ShortcutMatch.storeScore(play.packageName, play.label, true),
                oem to ShortcutMatch.storeScore(oem.packageName, oem.label, true),
            ),
        )
        assertEquals(play.packageName, decision.launch?.packageName)
        assertEquals(play.label, decision.tile?.label)
        assertFalse(decision.needsChoice)
    }

    @Test
    fun storeWithoutPlayUsesOneOemOrRemembers() {
        val oem = app("com.oem.appstore", "应用商店")
        val only = ShortcutMatch.stores(
            listOf(oem to ShortcutMatch.storeScore(oem.packageName, oem.label, false)),
        )
        assertEquals("应用商店", only.tile?.label)
        val other = app("com.second.market", "App Market")
        val many = ShortcutMatch.stores(
            listOf(
                oem to 75,
                other to 75,
            ),
        )
        assertTrue(many.needsChoice)
        assertNull(many.tile)
        val remembered = ShortcutMatch.stores(
            listOf(oem to 75, other to 75),
            remembered = other.key,
        )
        assertEquals(other.packageName, remembered.launch?.packageName)
    }

    @Test
    fun pinToggleAndFixedShortcuts() {
        val doc = ComponentKey("com.epic.docubay", "com.epic.docubay.Main")
        val added = PinPolicy.toggle(emptyList(), doc)
        assertEquals(listOf(doc), added)
        assertEquals(emptyList<ComponentKey>(), PinPolicy.toggle(added, doc))
        assertTrue(PinPolicy.isFixedHomePackage("com.android.vending", "files", "com.android.vending"))
        assertFalse(PinPolicy.isFixedHomePackage("com.epic.docubay", "files", "com.android.vending"))
        val hidden = PinPolicy.withoutFixed(
            listOf(doc, ComponentKey("com.android.vending", "Store")),
            filePackage = null,
            storePackage = "com.android.vending",
        )
        assertEquals(listOf(doc), hidden)
    }

    @Test
    fun newlinkFileManagerAndPackageUri() {
        val score = ShortcutMatch.fileManagerScore(
            "com.newlink.filemanager",
            "com.newlink.filemanager.activity.SelectActivity",
            "文件管理器",
        )
        assertTrue(score >= 80)
        assertEquals("com.newlink.startfilemanager", ShortcutMatch.preferredLaunchAction("com.newlink.filemanager"))
        assertEquals("package:com.epic.docubay", PackageUris.opaquePackage("com.epic.docubay"))
        assertNull(PackageUris.opaquePackage("  "))
        assertNull(PackageUris.opaquePackage(""))
    }

    @Test
    fun menuUninstallAndSettingsClassification() {
        assertTrue(MenuKeyPolicy.shouldConsume(MenuKeyPolicy.KEYCODE_MENU))
        assertTrue(MenuKeyPolicy.shouldConsume(MenuKeyPolicy.KEYCODE_TV_CONTENTS_MENU))
        assertFalse(MenuKeyPolicy.shouldConsume(4))
        assertTrue(UninstallTargets.isUninstallerClass("com.android.packageinstaller.UninstallerActivity"))
        assertTrue(UninstallTargets.allowsSilentExtra(emptySet()))
        assertFalse(UninstallTargets.allowsSilentExtra(setOf("android.intent.extra.RETURN_RESULT")))
        assertTrue(SettingsTargets.isAppDetailsClass("com.android.settings.applications.InstalledAppDetails"))
        assertTrue(SettingsTargets.isSettingsHomeClass("com.android.tv.settings.TvSettingsActivity"))
        assertFalse(SettingsTargets.isSettingsHomeClass("com.android.settings.applications.InstalledAppDetails"))
    }
}
