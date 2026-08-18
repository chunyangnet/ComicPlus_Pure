package com.comicplus.app.ui.screens

import android.graphics.Bitmap
import com.comicplus.app.data.source.DirectReaderPage
import com.comicplus.app.ui.AppSettings
import com.comicplus.app.ui.ReaderChapterSegment
import com.comicplus.app.ui.ReaderMode
import com.comicplus.app.ui.ReaderPrefetchMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Executes the speculative reader warm-up after the foreground position has
 * settled. Keeping this separate makes the cancellation and batching policy
 * visible without mixing it into the composable's state machine.
 */
internal suspend fun prefetchReaderPages(
    position: ReaderProgressPosition,
    previousPosition: ReaderProgressPosition?,
    loadedSegments: List<ReaderChapterSegment>,
    initialSegment: ReaderChapterSegment,
    settings: AppSettings,
    memoryClassMb: Int,
    prefetchDistance: Int,
    prefetchPage: suspend (DirectReaderPage) -> Unit,
    cachedPage: (DirectReaderPage) -> Bitmap?,
) {
    if (prefetchDistance <= 0) return

    val segment = loadedSegments.firstOrNull { it.chapterId == position.chapterId } ?: initialSegment
    val movementDirection = previousPosition
        ?.takeIf { it.chapterId == position.chapterId }
        ?.let { (position.pageIndex - it.pageIndex).coerceIn(-1, 1) }
        ?.takeUnless { it == 0 }
        ?: 1
    val indices = readerPrefetchPlanForMode(
        currentPageIndex = position.pageIndex,
        pageCount = segment.pages.size,
        distance = prefetchDistance,
        direction = movementDirection,
        readerMode = settings.readerMode,
        prefetchMode = settings.readerPrefetchMode,
    )
    if (indices.isEmpty()) return

    suspend fun prefetch(page: DirectReaderPage) {
        try {
            prefetchPage(page)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Visible page loading owns error UI; speculative failures stay silent.
        }
    }

    val pages = ArrayList<DirectReaderPage>(indices.size)
    indices.forEach { index ->
        val page = segment.pages.getOrNull(index) ?: return@forEach
        if (cachedPage(page) == null) pages.add(page)
    }
    if (pages.isEmpty()) return

    val parallel = settings.readerTurboMode || (
        (settings.readerPrefetchMode == ReaderPrefetchMode.Aggressive ||
            settings.readerPrefetchMode == ReaderPrefetchMode.UltraAggressive) &&
            memoryClassMb >= 512
        )
    if (parallel) {
        pages.chunked(2).forEach { batch ->
            coroutineScope { batch.map { page -> async { prefetch(page) } }.awaitAll() }
        }
    } else {
        pages.forEach { page -> prefetch(page) }
    }
}

internal fun readerPrefetchPlanForMode(
    currentPageIndex: Int,
    pageCount: Int,
    distance: Int,
    direction: Int,
    readerMode: ReaderMode,
    prefetchMode: ReaderPrefetchMode,
): List<Int> = readerPrefetchPlan(
    currentPageIndex = currentPageIndex,
    pageCount = pageCount,
    distance = distance,
    direction = direction,
    // Pages behind the current position have already been decoded. Ultra mode spends the
    // entire live window ahead of the current travel direction so a long fling cannot outrun it.
    includeOpposite = readerMode == ReaderMode.Paged && prefetchMode != ReaderPrefetchMode.UltraAggressive,
)
