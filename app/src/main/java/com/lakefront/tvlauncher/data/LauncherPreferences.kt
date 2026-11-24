package com.lakefront.tvlauncher.data

import android.content.Context
import com.lakefront.tvlauncher.logic.ComponentKey
import com.lakefront.tvlauncher.logic.WallpaperChoice

/**
 * Small preference file. Writes use apply() so they do not block the caller.
 */
class LauncherPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var pinsRaw: String
        get() = prefs.getString(KEY_PINS, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_PINS, value).apply()
        }

    var wallpaperUri: String?
        get() = WallpaperChoice.normalize(prefs.getString(KEY_WALL, null))
        set(value) {
            prefs.edit().apply {
                val normalized = WallpaperChoice.normalize(value)
                if (normalized == null) remove(KEY_WALL) else putString(KEY_WALL, normalized)
            }.apply()
        }

    var fileManagerKey: ComponentKey?
        get() = ComponentKey.parse(prefs.getString(KEY_FILES, null).orEmpty())
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_FILES) else putString(KEY_FILES, value.flatten())
            }.apply()
        }

    var storeKey: ComponentKey?
        get() = ComponentKey.parse(prefs.getString(KEY_STORE, null).orEmpty())
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_STORE) else putString(KEY_STORE, value.flatten())
            }.apply()
        }

    private companion object {
        const val FILE = "lake_launcher"
        const val KEY_PINS = "pins"
        const val KEY_WALL = "wallpaper_uri"
        const val KEY_FILES = "file_manager"
        const val KEY_STORE = "app_store"
    }
}
