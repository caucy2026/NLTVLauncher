package com.lakefront.tvlauncher.logic

object WallpaperChoice {
    fun normalize(stored: String?): String? = stored?.trim()?.ifBlank { null }

    fun isDefault(stored: String?): Boolean = normalize(stored) == null
}
