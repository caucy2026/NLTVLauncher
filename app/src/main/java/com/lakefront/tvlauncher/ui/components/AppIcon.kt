package com.lakefront.tvlauncher.ui.components

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.lakefront.tvlauncher.logic.AppEntry
import com.lakefront.tvlauncher.logic.IconCacheBudget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppIconImage(
    packageName: String,
    activityName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val key = "$packageName/$activityName"
    var bitmap by remember(key) { mutableStateOf(AppIconCache.peek(key)) }
    LaunchedEffect(key) {
        val image = withContext(Dispatchers.IO) {
            AppIconCache.get(key) {
                val pm = context.packageManager
                try {
                    pm.getActivityIcon(ComponentName(packageName, activityName))
                } catch (_: Exception) {
                    try {
                        pm.getApplicationIcon(packageName)
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
        bitmap = image
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(modifier.background(Color.White.copy(alpha = 0.08f)))
    }
}

private object AppIconCache {
    private val cache = object : LruCache<String, ImageBitmap>(IconCacheBudget.MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun peek(key: String): ImageBitmap? = cache.get(key)

    fun get(key: String, load: () -> Drawable?): ImageBitmap? {
        cache.get(key)?.let { return it }
        val drawable = load() ?: return null
        val image = drawable.toImageBitmap(IconCacheBudget.ICON_PX)
        cache.put(key, image)
        return image
    }
}

internal fun warmAppIcons(context: Context, apps: List<AppEntry>) {
    val pm = context.applicationContext.packageManager
    apps.take(IconCacheBudget.WARM_LIMIT).forEach { entry ->
        AppIconCache.get("${entry.packageName}/${entry.activityName}") {
            try {
                pm.getActivityIcon(ComponentName(entry.packageName, entry.activityName))
            } catch (_: Exception) {
                try {
                    pm.getApplicationIcon(entry.packageName)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}

private fun Drawable.toImageBitmap(size: Int = 192): ImageBitmap {
    val source = constantState?.newDrawable()?.mutate() ?: this
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    source.setBounds(0, 0, size, size)
    source.draw(canvas)
    return bitmap.asImageBitmap()
}
