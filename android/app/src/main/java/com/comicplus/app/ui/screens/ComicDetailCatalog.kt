package com.comicplus.app.ui.screens

import com.comicplus.app.data.source.SourceChapterDto

internal data class ReadingEntry(
    val chapter: SourceChapterDto,
    val pageIndex: Int,
    val resumed: Boolean,
)

internal fun resolveReadingEntry(
    chapters: List<SourceChapterDto>,
    resumeChapterId: String?,
    resumePageIndex: Int,
): ReadingEntry? {
    val firstChapter = chapters.firstOrNull() ?: return null
    val savedChapter = resumeChapterId?.let { id -> chapters.firstOrNull { it.sourceChapterId == id } }
    return if (savedChapter != null) {
        ReadingEntry(savedChapter, resumePageIndex.coerceAtLeast(0), resumed = true)
    } else {
        ReadingEntry(firstChapter, pageIndex = 0, resumed = false)
    }
}

internal data class ChapterRange(
    val key: String,
    val label: String,
    val chapters: List<SourceChapterDto>,
)

/**
 * Derived chapter data for the detail screen. Large comics can contain
 * thousands of chapters, so selection should be map lookups rather than a
 * nested scan through every range on each tap/recomposition.
 */
internal data class DetailChapterCatalog(
    val ranges: List<ChapterRange>,
    val rangesByKey: Map<String, ChapterRange>,
    val rangeKeyByChapterId: Map<String, String>,
)

internal data class DetailChapterSelection(
    val chapterId: String,
    val rangeKey: String,
)

internal fun defaultDetailChapterSelection(
    catalog: DetailChapterCatalog,
    preferredChapterId: String?,
): DetailChapterSelection? {
    val preferredRangeKey = preferredChapterId?.let(catalog.rangeKeyByChapterId::get)
    if (preferredRangeKey != null) {
        return DetailChapterSelection(preferredChapterId, preferredRangeKey)
    }
    val firstRange = catalog.ranges.firstOrNull() ?: return null
    val firstChapter = firstRange.chapters.firstOrNull() ?: return null
    return DetailChapterSelection(firstChapter.sourceChapterId, firstRange.key)
}

internal fun buildDetailChapterCatalog(
    chapters: List<SourceChapterDto>,
    descending: Boolean,
): DetailChapterCatalog {
    val sorted = chapters.sortedBy(SourceChapterDto::index)
    val chunkSize = when {
        sorted.size <= 40 -> sorted.size.coerceAtLeast(1)
        sorted.size <= 120 -> 30
        sorted.size <= 300 -> 50
        else -> 100
    }
    val ascendingRanges = sorted.chunked(chunkSize).map { chunk ->
        val first = chunk.first().index
        val last = chunk.last().index
        ChapterRange(
            key = "$first-$last",
            label = if (first == last) "$first" else "$first–$last",
            chapters = if (descending) chunk.asReversed() else chunk,
        )
    }
    val ranges = if (descending) ascendingRanges.asReversed() else ascendingRanges
    val rangeKeyByChapterId = buildMap(chapters.size) {
        ranges.forEach { range ->
            range.chapters.forEach { chapter ->
                if (chapter.sourceChapterId !in this) put(chapter.sourceChapterId, range.key)
            }
        }
    }
    return DetailChapterCatalog(
        ranges = ranges,
        rangesByKey = ranges.associateBy(ChapterRange::key),
        rangeKeyByChapterId = rangeKeyByChapterId,
    )
}
