package com.lakefront.tvlauncher.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusAndStorageTest {
    @Test
    fun iconCacheStaysWithinAFixedByteBudget() {
        assertTrue(IconCacheBudget.maxIcons() in 2..80)
        assertEquals(160 * 160 * 4, IconCacheBudget.bytesForEdge(160))
        assertTrue(IconCacheBudget.maxIcons() * IconCacheBudget.bytesForEdge(IconCacheBudget.ICON_PX) <= IconCacheBudget.MAX_BYTES)
        assertTrue(IconCacheBudget.WARM_LIMIT < IconCacheBudget.maxIcons())
    }

    @Test
    fun usbIconRequiresMountedRemovableStorageNotPeripherals() {
        assertFalse(
            UsbVolumeRules.showIcon(
                removable = false,
                primary = false,
                state = "mounted",
                description = "USB",
                usbMassStorageConnected = true,
            ),
        )
        assertFalse(
            UsbVolumeRules.showIcon(
                removable = true,
                primary = true,
                state = "mounted",
                description = "USB",
                usbMassStorageConnected = true,
            ),
        )
        assertFalse(
            UsbVolumeRules.showIcon(
                removable = true,
                primary = false,
                state = "unmounted",
                description = "USB drive",
                usbMassStorageConnected = true,
            ),
        )
        assertFalse(
            UsbVolumeRules.showIcon(
                removable = true,
                primary = false,
                state = "mounted",
                description = "SD card",
                usbMassStorageConnected = false,
            ),
        )
        assertFalse(
            UsbVolumeRules.showIcon(
                removable = true,
                primary = false,
                state = "mounted",
                description = null,
                usbMassStorageConnected = false,
            ),
        )
        assertTrue(
            UsbVolumeRules.showIcon(
                removable = true,
                primary = false,
                state = "mounted",
                description = "USB drive",
                usbMassStorageConnected = false,
            ),
        )
        assertTrue(
            UsbVolumeRules.showIcon(
                removable = true,
                primary = false,
                state = "mounted_ro",
                description = "Removable",
                usbMassStorageConnected = true,
            ),
        )
    }

    @Test
    fun removingFocusedUsbMovesToTheFollowingControl() {
        val row = listOf("status:wifi", "status:usb", "status:wallpaper", "status:settings")
        assertEquals("status:wallpaper", StatusRowFocus.afterUsbRemoved(row, "status:usb"))
        assertEquals("status:wifi", StatusRowFocus.afterUsbRemoved(row, "status:wifi"))
        assertNull(StatusRowFocus.afterUsbRemoved(listOf("status:usb"), "status:usb"))
    }

    @Test
    fun nudgeOnlyAtTrueRowEnds() {
        assertTrue(EdgeFeedback.shouldNudge(index = 0, count = 4, moveForward = false))
        assertFalse(EdgeFeedback.shouldNudge(index = 0, count = 4, moveForward = true))
        assertTrue(EdgeFeedback.shouldNudge(index = 3, count = 4, moveForward = true))
        assertFalse(EdgeFeedback.shouldNudge(index = 2, count = 4, moveForward = true))
        assertFalse(EdgeFeedback.shouldNudge(index = 5, count = 4, moveForward = true))
    }
}
