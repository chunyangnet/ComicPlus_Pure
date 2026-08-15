package com.comicplus.pure

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.allowRgb565
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toOkioPath

class ComicPlusApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.10)
                .strongReferencesEnabled(true)
                .weakReferencesEnabled(true)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("coil-covers").toOkioPath())
                .maxSizeBytes(COVER_DISK_CACHE_BYTES)
                .build()
        }
        .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(4))
        .decoderCoroutineContext(Dispatchers.Default.limitedParallelism(2))
        .allowRgb565(true)
        .crossfade(160)
        .build()

    private companion object {
        private const val COVER_DISK_CACHE_BYTES = 128L * 1024L * 1024L
    }
}
