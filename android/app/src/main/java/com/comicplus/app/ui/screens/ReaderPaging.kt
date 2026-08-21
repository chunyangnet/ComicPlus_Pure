package com.comicplus.app.ui.screens

import com.comicplus.app.ui.ReaderDirection
import android.graphics.Bitmap
import com.comicplus.app.data.source.DirectReaderPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class ReaderProgressPosition(
    val chapterId: String,
    val chapterTitle: String,
    val chapterIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
)

internal fun readingIndexToPagerPage(
    readingIndex: Int,
    pageCount: Int,
    direction: ReaderDirection,
): Int {
    if (pageCount <= 0) return 0
    val safeIndex = readingIndex.coerceIn(0, pageCount - 1)
    return if (direction == ReaderDirection.RightToLeft) pageCount - 1 - safeIndex else safeIndex
}

internal fun pagerPageToReadingIndex(
    pagerPage: Int,
    pageCount: Int,
    direction: ReaderDirection,
): Int = readingIndexToPagerPage(pagerPage, pageCount, direction)

internal fun readerPrefetchIndices(
    currentPageIndex: Int,
    pageCount: Int,
    distance: Int,
): List<Int> = readerPrefetchPlan(
    currentPageIndex = currentPageIndex,
    pageCount = pageCount,
    distance = distance,
    direction = 1,
    includeOpposite = false,
)

internal fun readerPagedPrefetchIndices(
    currentPageIndex: Int,
    pageCount: Int,
    distance: Int,
): List<Int> {
    if (pageCount <= 0 || distance <= 0) return emptyList()
    val safeCurrent = currentPageIndex.coerceIn(0, pageCount - 1)
    val result = ArrayList<Int>(distance)
    var offset = 1
    while (result.size < distance && (safeCurrent + offset < pageCount || safeCurrent - offset >= 0)) {
        if (safeCurrent + offset < pageCount) result.add(safeCurrent + offset)
        if (result.size < distance && safeCurrent - offset >= 0) result.add(safeCurrent - offset)
        offset++
    }
    return result
}

/**
 * Returns a direction-aware warming order. The first half follows the user's
 * travel direction, then a small backtrack window keeps accidental reversals
 * instant without stealing the foreground decode slot.
 *
 * This is deliberately index-based instead of composing temporary `List`s and
 * calling `take`/`drop`/`distinct` on every page movement. The two directions
 * cannot overlap, so the old de-duplication pass was redundant on this path.
 */
internal fun readerPrefetchPlan(
    currentPageIndex: Int,
    pageCount: Int,
    distance: Int,
    direction: Int,
    includeOpposite: Boolean,
): List<Int> {
    if (pageCount <= 0 || distance <= 0) return emptyList()
    val safeCurrent = currentPageIndex.coerceIn(0, pageCount - 1)
    val forward = if (direction < 0) -1 else 1
    val result = ArrayList<Int>(distance.coerceAtMost(pageCount - 1))

    var primaryOffset = 1
    fun appendPrimaryUntil(limit: Int) {
        while (result.size < limit) {
            val index = safeCurrent + forward * primaryOffset
            if (index !in 0 until pageCount) break
            result.add(index)
            primaryOffset++
        }
    }

    if (!includeOpposite) {
        appendPrimaryUntil(distance)
        return result
    }

    var oppositeOffset = 1
    val primaryQuota = minOf((distance + 1) / 2, distance)
    appendPrimaryUntil(primaryQuota)
    while (result.size < distance) {
        val index = safeCurrent - forward * oppositeOffset
        if (index !in 0 until pageCount) break
        result.add(index)
        oppositeOffset++
    }
    appendPrimaryUntil(distance)
    while (result.size < distance) {
        val index = safeCurrent - forward * oppositeOffset
        if (index !in 0 until pageCount) break
        result.add(index)
        oppositeOffset++
    }
    return result
}

internal fun shouldPreloadNextChapter(
    currentPageIndex: Int,
    pageCount: Int,
    distance: Int,
): Boolean {
    if (pageCount <= 0 || distance < 0) return false
    val safePageIndex = currentPageIndex.coerceIn(0, pageCount - 1)
    return pageCount - safePageIndex - 1 <= distance
}

internal fun canLoadSequentialPage(
    pageIndex: Int,
    startPageIndex: Int,
    loadedThroughPageIndex: Int,
    previousChapterComplete: Boolean = true,
): Boolean {
    if (!previousChapterComplete || pageIndex < 0) return false
    val safeStart = startPageIndex.coerceAtLeast(0)
    return pageIndex <= safeStart || pageIndex <= loadedThroughPageIndex + 1
}

internal fun sequentialPageLoadOrder(
    sequence: List<DirectReaderPage>,
    target: DirectReaderPage,
): List<DirectReaderPage> {
    val targetKey = target.sequentialLoadKey()
    val targetIndex = sequence.indexOfFirst { it.sequentialLoadKey() == targetKey }
    return if (targetIndex >= 0) sequence.subList(0, targetIndex + 1) else listOf(target)
}

internal class SequentialPageLoadGate {
    private val mutex = Mutex()
    private val completed = HashSet<String>()
    private val completion = HashMap<String, CompletableDeferred<Unit>>()

    suspend fun awaitAvailable(page: DirectReaderPage) {
        val waiter = mutex.withLock {
            val key = page.sequentialLoadKey()
            if (key in completed) return@withLock null
            completion.getOrPut(key) { CompletableDeferred() }
        }
        waiter?.await()
    }

    suspend fun load(
        sequence: List<DirectReaderPage>,
        target: DirectReaderPage,
        cachedPage: (DirectReaderPage) -> Bitmap?,
        loader: suspend (DirectReaderPage, Boolean) -> Bitmap,
    ): Bitmap = mutex.withLock {
        val targetKey = target.sequentialLoadKey()
        var targetBitmap: Bitmap? = null
        sequentialPageLoadOrder(sequence, target).forEach { page ->
            val key = page.sequentialLoadKey()
            val isTarget = key == targetKey
            val cached = cachedPage(page)
            if (key in completed && !isTarget) return@forEach
            val bitmap = cached ?: loader(page, isTarget)
            completed += key
            completion.remove(key)?.complete(Unit)
            if (isTarget) targetBitmap = bitmap
        }
        targetBitmap ?: cachedPage(target)?.also {
            completed += targetKey
            completion.remove(targetKey)?.complete(Unit)
        } ?: loader(target, true).also {
            completed += targetKey
            completion.remove(targetKey)?.complete(Unit)
        }
    }
}

private fun DirectReaderPage.sequentialLoadKey(): String = "$photoId|$fileName"
