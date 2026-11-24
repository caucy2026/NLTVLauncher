package com.lakefront.tvlauncher.logic

/**
 * Home vertical structure. Poster rows occupy the space under the status pill.
 * The Apps row stays at the bottom of the screen even when Watch Next and
 * recommendations are empty, so the lake is not covered by a top banner.
 */
object HomeArrangement {
    /** Space reserved so the first feed line clears the status pill. */
    const val TOP_CLEARANCE_DP = 72

    /** Right inset so a left-aligned note cannot run under the status pill. */
    const val STATUS_END_GUARD_DP = 300

    const val APPS_ANCHORED_BOTTOM = true

    fun guidanceSitsAboveApps(loading: Boolean, posterRowCount: Int): Boolean =
        !loading && posterRowCount == 0

    fun guidanceInTopBand(): Boolean = false

    /**
     * Overlay dismiss prefers the control that opened it (the status Wallpaper
     * button) over whatever home tile was focused before the pill was used.
     */
    fun focusAfterOverlay(
        restoreKey: String?,
        screenIsAllApps: Boolean,
        allAppsKey: String?,
        homeKey: String,
    ): String? = restoreKey?.takeIf { it.isNotBlank() } ?: if (screenIsAllApps) allAppsKey else homeKey
}
