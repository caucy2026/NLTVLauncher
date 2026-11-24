package com.lakefront.tvlauncher.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeArrangementTest {
    @Test
    fun emptyFeedKeepsAppsAtBottomAndGuidanceOutOfTheStatusBand() {
        assertTrue(HomeArrangement.APPS_ANCHORED_BOTTOM)
        assertFalse(HomeArrangement.guidanceInTopBand())
        assertTrue(HomeArrangement.guidanceSitsAboveApps(loading = false, posterRowCount = 0))
        assertTrue(HomeArrangement.TOP_CLEARANCE_DP >= 64)
        assertTrue(HomeArrangement.STATUS_END_GUARD_DP >= 240)
    }

    @Test
    fun wallpaperDismissReturnsToTheStatusControlNotAllApps() {
        val key = HomeArrangement.focusAfterOverlay(
            restoreKey = "status:wallpaper",
            screenIsAllApps = false,
            allAppsKey = null,
            homeKey = "tile:all",
        )
        assertEquals("status:wallpaper", key)
        val other = HomeArrangement.focusAfterOverlay(
            restoreKey = null,
            screenIsAllApps = false,
            allAppsKey = null,
            homeKey = "tile:add",
        )
        assertEquals("tile:add", other)
    }

    @Test
    fun realRowsAndLoadingDoNotShowTheEmptyNote() {
        assertFalse(HomeArrangement.guidanceSitsAboveApps(loading = true, posterRowCount = 0))
        assertFalse(HomeArrangement.guidanceSitsAboveApps(loading = false, posterRowCount = 1))
        assertFalse(HomeArrangement.guidanceSitsAboveApps(loading = false, posterRowCount = 3))
    }
}
