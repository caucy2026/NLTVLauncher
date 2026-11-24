package com.lakefront.tvlauncher

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache

class LakeApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(false)
            .memoryCache {
                MemoryCache.Builder(this@LakeApp)
                    .maxSizeBytes(16 * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
