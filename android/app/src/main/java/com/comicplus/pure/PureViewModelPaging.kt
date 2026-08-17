package com.comicplus.pure

import com.comicplus.app.ui.ComicUiItem
import com.comicplus.app.ui.JmCommentUiItem
import com.comicplus.app.ui.key

/**
 * Small, allocation-conscious helpers used by the ViewModel's paged endpoints.
 *
 * Keeping these outside the ViewModel makes the request code easier to audit and
 * keeps the hot merge/continuation decisions independently testable.
 */
internal fun mergeComicPage(
    existing: List<ComicUiItem>,
    incoming: List<ComicUiItem>,
): List<ComicUiItem> = buildList(existing.size + incoming.size) {
    val seen = HashSet<String>(existing.size + incoming.size)
    existing.forEach { item ->
        if (seen.add(item.key)) add(item)
    }
    incoming.forEach { item ->
        if (seen.add(item.key)) add(item)
    }
}

/**
 * A server can report another page even when it repeats every item from the
 * current list. Check for a new key without materialising a second merged list.
 */
internal fun shouldContinueComicPagination(
    existing: List<ComicUiItem>,
    incoming: List<ComicUiItem>,
    serverHasMore: Boolean,
): Boolean {
    if (!serverHasMore || incoming.isEmpty()) return false
    val seen = HashSet<String>(existing.size + incoming.size)
    existing.forEach { seen.add(it.key) }
    return incoming.any { seen.add(it.key) }
}

internal fun mergeCommentPage(
    existing: List<JmCommentUiItem>,
    incoming: List<JmCommentUiItem>,
): List<JmCommentUiItem> = buildList(existing.size + incoming.size) {
    val seen = HashSet<String>(existing.size + incoming.size)
    existing.forEach { item ->
        if (seen.add(item.id)) add(item)
    }
    incoming.forEach { item ->
        if (seen.add(item.id)) add(item)
    }
}

internal fun shouldContinueCommentPagination(
    existing: List<JmCommentUiItem>,
    incoming: List<JmCommentUiItem>,
    serverHasMore: Boolean,
): Boolean {
    if (!serverHasMore || incoming.isEmpty()) return false
    val seen = HashSet<String>(existing.size + incoming.size)
    existing.forEach { seen.add(it.id) }
    return incoming.any { seen.add(it.id) }
}

/** Current page first, then forward pages, finally backfill from behind at a chapter edge. */
internal fun readerEntryWarmupIndices(
    currentPageIndex: Int,
    pageCount: Int,
    pageBudget: Int,
): List<Int> {
    if (pageCount <= 0 || pageBudget <= 0) return emptyList()
    val current = currentPageIndex.coerceIn(0, pageCount - 1)
    return buildList(pageBudget.coerceAtMost(pageCount)) {
        add(current)
        var offset = 1
        while (size < pageBudget && current + offset < pageCount) {
            add(current + offset)
            offset++
        }
        offset = 1
        while (size < pageBudget && current - offset >= 0) {
            add(current - offset)
            offset++
        }
    }
}

internal fun shouldPublishProgress(previous: Float, current: Float, elapsedNanos: Long, completed: Boolean): Boolean =
    completed || current - previous >= 0.01f || elapsedNanos >= 150_000_000L

internal fun isForwardPageResponse(previousPage: Int, requestedPage: Int, responsePage: Int): Boolean =
    requestedPage > previousPage && responsePage == requestedPage
