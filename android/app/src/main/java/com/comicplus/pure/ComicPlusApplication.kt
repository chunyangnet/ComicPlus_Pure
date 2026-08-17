package com.comicplus.pure

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toOkioPath
import okhttp3.OkHttpClient

class ComicPlusApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        SystemVpnMonitor.start(this)
    }

    override fun newImageLoader(context: Context): ImageLoader {
        val coverClient = OkHttpClient.Builder()
            .build()
        SystemVpnMonitor.registerRouteChangeListener {
            coverClient.dispatcher.cancelAll()
            coverClient.connectionPool.evictAll()
        }
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(coverClient)) }
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
    }

    private companion object {
        private const val COVER_DISK_CACHE_BYTES = 128L * 1024L * 1024L
    }
}
