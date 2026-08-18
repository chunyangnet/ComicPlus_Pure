package com.comicplus.pure

import okhttp3.OkHttpClient
import java.util.concurrent.Executors

private val okHttpLifecycleExecutor by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    Executors.newSingleThreadExecutor { task ->
        Thread(task, "ComicPlus-network-cleanup").apply { isDaemon = true }
    }
}

internal fun evictConnectionsAsync(vararg clients: OkHttpClient) {
    scheduleOkHttpCleanup(clients, shutdownDispatchers = false)
}

internal fun closeOkHttpClientsAsync(vararg clients: OkHttpClient) {
    scheduleOkHttpCleanup(clients, shutdownDispatchers = true)
}

private fun scheduleOkHttpCleanup(
    clients: Array<out OkHttpClient>,
    shutdownDispatchers: Boolean,
) {
    if (clients.isEmpty()) return
    okHttpLifecycleExecutor.execute {
        clients.forEach { client ->
            runCatchingNonFatal {
                client.dispatcher.cancelAll()
                client.connectionPool.evictAll()
                if (shutdownDispatchers) client.dispatcher.executorService.shutdown()
            }
        }
    }
}
