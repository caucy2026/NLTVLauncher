package com.lakefront.tvlauncher.logic

object UninstallPolicy {
    /**
     * @return an explanation when Android's uninstall UI must not be started, or null when
     * the normal system confirmation flow is appropriate.
     */
    fun blockReason(isSelf: Boolean, isSystem: Boolean, isUpdatedSystem: Boolean): String? = when {
        isSelf -> "Lake TV Launcher is the app you're using. Remove it from system settings if you no longer want it."
        isSystem && !isUpdatedSystem -> "This is a system app. Android does not allow uninstalling it."
        else -> null
    }
}
