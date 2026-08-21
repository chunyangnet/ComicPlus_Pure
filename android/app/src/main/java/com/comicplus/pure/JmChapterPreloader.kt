package com.comicplus.pure

import com.comicplus.app.ui.AppSettings
import com.comicplus.app.ui.ReaderPrefetchMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns ultra-aggressive whole-chapter source caching independently from nearby bitmap warming.
 * Only two workers are created regardless of chapter size, and JmGateway still lets visible page
 * network work preempt them.
 */
internal class JmChapterPreloader(
    private val scope: CoroutineScope,
    private val gateway: JmGateway,
    private val settingsProvider: () -> AppSettings,
) {
    private val jobs = ConcurrentHashMap<String, Job>()

    fun schedule(chapterId: String, pages: List<JmPage>, startPageIndex: Int = 0) {
        val settings = settingsProvider()
        if (
            settings.readerPrefetchMode != ReaderPrefetchMode.UltraAggressive ||
            settings.dataSaver ||
            settings.sequentialPageLoading ||
            pages.isEmpty() ||
            jobs[chapterId]?.isActive == true
        ) return

        val order = readerEntryWarmupIndices(
            currentPageIndex = startPageIndex,
            pageCount = pages.size,
            pageBudget = pages.size,
        )
        val job = scope.launch(Dispatchers.IO) {
            val cursor = AtomicInteger()
            coroutineScope {
                repeat(minOf(ULTRA_PRELOAD_WORKERS, order.size)) {
                    launch {
                        while (isActive) {
                            val orderIndex = cursor.getAndIncrement()
                            val pageIndex = order.getOrNull(orderIndex) ?: break
                            val page = pages.getOrNull(pageIndex) ?: continue
                            try {
                                gateway.preloadPageSource(
                                    page = page,
                                    quality = settings.readerImageQuality,
                                    turboMode = settings.readerTurboMode,
                                )
                            } catch (error: Throwable) {
                                error.rethrowCancellation()
                                // One bad mirror/page must not prevent the rest of the chapter
                                // from entering the durable cache.
                            }
                        }
                    }
                }
            }
        }
        jobs[chapterId] = job
        job.invokeOnCompletion { jobs.remove(chapterId, job) }
    }

    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    fun cancelExcept(chapterId: String) {
        jobs.entries.forEach { (id, job) ->
            if (id != chapterId && jobs.remove(id, job)) job.cancel()
        }
    }

    private companion object {
        private const val ULTRA_PRELOAD_WORKERS = 2
    }
}
