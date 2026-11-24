package com.lakefront.tvlauncher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.lakefront.tvlauncher.logic.WallpaperChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WallpaperStore(
    context: Context,
    private val preferences: LauncherPreferences,
) {
    private val resolver = context.applicationContext.contentResolver
    private val current = MutableStateFlow(preferences.wallpaperUri)
    val uri: StateFlow<String?> = current.asStateFlow()

    fun setCustom(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (error: SecurityException) {
            Log.w(TAG, "Could not persist wallpaper permission", error)
        }
        val value = WallpaperChoice.normalize(uri.toString()) ?: return
        preferences.wallpaperUri = value
        current.value = value
    }

    fun useDefault() {
        val existing = preferences.wallpaperUri
        if (existing != null) {
            runCatching {
                resolver.releasePersistableUriPermission(
                    Uri.parse(existing),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        preferences.wallpaperUri = null
        current.value = null
    }

    companion object {
        private const val TAG = "LakeTv"
    }
}
