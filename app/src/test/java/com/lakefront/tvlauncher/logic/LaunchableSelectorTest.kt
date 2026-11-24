package com.lakefront.tvlauncher.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchableSelectorTest {
    private fun raw(
        pkg: String,
        cls: String = "$pkg.Main",
        leanback: Boolean = false,
        label: String = pkg,
    ) = RawLaunchable(pkg, cls, label, leanback)

    @Test
    fun prefersLeanbackAndDropsPhoneAlias() {
        val selected = LaunchableSelector.select(
            entries = listOf(
                raw("player", leanback = false, label = "Player phone"),
                raw("player", cls = "player.Tv", leanback = true, label = "Player"),
            ),
            selfPackage = "com.lakefront.tvlauncher",
            excludedPackages = emptySet(),
            excludedComponents = emptySet(),
        )
        assertEquals(listOf("player.Tv"), selected.map { it.activityName })
        assertTrue(selected.single().leanback)
    }

    @Test
    fun keepsMultipleLeanbackActivitiesAndOnePhoneFallback() {
        val selected = LaunchableSelector.select(
            entries = listOf(
                raw("studio", cls = "studio.Live", leanback = true, label = "Live"),
                raw("studio", cls = "studio.Library", leanback = true, label = "Library"),
                raw("studio", cls = "studio.Phone", leanback = false),
                raw("notes", cls = "notes.Main", leanback = false, label = "Notes"),
                raw("notes", cls = "notes.Alias", leanback = false, label = "Notes alias"),
            ),
            selfPackage = "self",
            excludedPackages = emptySet(),
            excludedComponents = emptySet(),
        )
        val studio = selected.filter { it.packageName == "studio" }.map { it.activityName }
        assertEquals(listOf("studio.Live", "studio.Library"), studio)
        assertEquals(listOf("notes.Main"), selected.filter { it.packageName == "notes" }.map { it.activityName })
    }

    @Test
    fun excludesSelfSettingsAndAllAppsIsNotSynthesized() {
        val selected = LaunchableSelector.select(
            entries = listOf(
                raw("com.lakefront.tvlauncher", label = "Lake TV Launcher"),
                raw("com.android.settings", label = "Settings"),
                raw("com.android.tv.settings", cls = "settings.Main", leanback = true, label = "Settings"),
                raw("files", label = "Files"),
            ),
            selfPackage = "com.lakefront.tvlauncher",
            excludedPackages = setOf("com.android.settings"),
            excludedComponents = setOf("com.android.tv.settings/settings.Main"),
        )
        assertEquals(listOf("files"), selected.map { it.packageName })
        assertTrue(selected.none { it.label.equals("All Apps", ignoreCase = true) })
        assertTrue(selected.none { it.label.equals("Settings", ignoreCase = true) })
    }

    @Test
    fun handlerPickerUsesPriorityThenExtras() {
        val docs = AppEntry("com.android.documentsui", "com.android.documentsui.FilesActivity", "Files", true, true, false)
        val fx = AppEntry("nextapp.fx", "nextapp.fx.Main", "FX", false, false, false)
        val extra = AppEntry("com.example.files", "com.example.files.Main", "Cabin", true, false, false)
        val picked = HandlerPicker.pick(
            KnownHandlers.fileManagerPackages,
            listOf(extra, fx, docs, fx),
        )
        assertEquals(listOf("com.android.documentsui", "nextapp.fx", "com.example.files"), picked.map { it.packageName })
        assertEquals(emptyList<AppEntry>(), HandlerPicker.pick(KnownHandlers.storePackages, emptyList()))
    }
}
