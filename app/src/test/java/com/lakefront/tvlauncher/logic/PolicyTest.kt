package com.lakefront.tvlauncher.logic

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {
    @Test
    fun uninstallBlocksSelfAndFactorySystemApps() {
        assertNotNull(UninstallPolicy.blockReason(isSelf = true, isSystem = false, isUpdatedSystem = false))
        assertNotNull(UninstallPolicy.blockReason(isSelf = false, isSystem = true, isUpdatedSystem = false))
        assertNull(UninstallPolicy.blockReason(isSelf = false, isSystem = true, isUpdatedSystem = true))
        assertNull(UninstallPolicy.blockReason(isSelf = false, isSystem = false, isUpdatedSystem = false))
        assertTrue(UninstallPolicy.blockReason(true, false, false)!!.contains("Lake TV Launcher"))
    }

    @Test
    fun wallpaperChoice() {
        assertTrue(WallpaperChoice.isDefault(null))
        assertTrue(WallpaperChoice.isDefault("  "))
        assertTrue(!WallpaperChoice.isDefault("content://media/1"))
        assertNull(WallpaperChoice.normalize(" "))
    }
}
