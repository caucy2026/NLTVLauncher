package com.lakefront.tvlauncher.logic

/**
 * Bounds for decoded launcher icons. Each bitmap is [ICON_PX] square ARGB (4 bytes per pixel).
 * The cache holds at most [MAX_BYTES], about 60 icons, instead of one bitmap per installed app.
 */
object IconCacheBudget {
    const val ICON_PX = 160
    const val MAX_BYTES = 6 * 1024 * 1024
    const val WARM_LIMIT = 12

    fun bytesForEdge(px: Int): Int = px * px * 4

    fun maxIcons(): Int = MAX_BYTES / bytesForEdge(ICON_PX)
}

object UsbVolumeRules {
    /**
     * True only for a mounted, removable, non-primary volume that looks like USB storage.
     * An SD card description is excluded. A USB keyboard or ADB connection has no mounted volume.
     * When the label is ambiguous, [usbMassStorageConnected] is required.
     */
    fun showIcon(
        removable: Boolean,
        primary: Boolean,
        state: String?,
        description: String?,
        usbMassStorageConnected: Boolean,
    ): Boolean {
        if (!removable || primary) return false
        val mounted = state == "mounted" || state == "mounted_ro"
        if (!mounted) return false
        val text = description?.lowercase().orEmpty()
        val saysUsb = listOf("usb", "u盘", "u 盘", "闪存").any { it in text }
        val saysSd = listOf("sd card", "sdcard", "sd 卡", "sd卡", "存储卡").any { it in text }
        if (saysSd && !saysUsb) return false
        return saysUsb || usbMassStorageConnected
    }
}

object StatusRowFocus {
    fun afterUsbRemoved(order: List<String>, focused: String): String? {
        if (focused != "status:usb") return focused
        val index = order.indexOf("status:usb")
        val remaining = order.filter { it != "status:usb" }
        if (remaining.isEmpty()) return null
        if (index < 0) return remaining.last()
        return remaining.getOrNull(index) ?: remaining.last()
    }
}

object EdgeFeedback {
    /** True only at a real end of the focusable row, not merely the visible edge of a longer list. */
    fun shouldNudge(index: Int, count: Int, moveForward: Boolean): Boolean {
        if (count <= 0 || index !in 0 until count) return false
        return if (moveForward) index == count - 1 else index == 0
    }
}
