package com.comicplus.app.ui

import com.comicplus.app.data.source.SourceChapterDto
import com.comicplus.app.ui.screens.buildDetailChapterCatalog
import com.comicplus.app.ui.screens.resolveReadingEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicDetailEntryTest {
    private val chapters = listOf(
        SourceChapterDto("101", 1, "第 1 话"),
        SourceChapterDto("202", 2, "第 2 话"),
    )

    @Test
    fun entryUsesSavedChapterAndPageWhenProgressExists() {
        val entry = resolveReadingEntry(chapters, resumeChapterId = "202", resumePageIndex = 7)!!

        assertEquals("202", entry.chapter.sourceChapterId)
        assertEquals(7, entry.pageIndex)
        assertTrue(entry.resumed)
    }

    @Test
    fun entryStartsFromFirstChapterWhenThereIsNoMatchingProgress() {
        val entry = resolveReadingEntry(chapters, resumeChapterId = "missing", resumePageIndex = 7)!!

        assertEquals("101", entry.chapter.sourceChapterId)
        assertEquals(0, entry.pageIndex)
        assertFalse(entry.resumed)
    }

    @Test
    fun entryHandlesEmptyChapterList() {
        assertNull(resolveReadingEntry(emptyList(), resumeChapterId = null, resumePageIndex = 0))
    }

    @Test
    fun chapterCatalogIndexesLargeRangesByChapterId() {
        val manyChapters = List(205) { index ->
            SourceChapterDto("chapter-${index + 1}", index + 1, "第 ${index + 1} 话")
        }

        val catalog = buildDetailChapterCatalog(manyChapters, descending = false)

        assertEquals(5, catalog.ranges.size)
        assertEquals("101-150", catalog.rangeKeyByChapterId["chapter-125"])
        assertEquals(124, catalog.chapterIndicesById["chapter-125"])
        assertEquals("第 125 话", catalog.chapterLabels[124])
    }

    @Test
    fun descendingCatalogKeepsRangeAndChapterOrderAligned() {
        val manyChapters = List(45) { index ->
            SourceChapterDto("chapter-${index + 1}", index + 1, "第 ${index + 1} 话")
        }

        val catalog = buildDetailChapterCatalog(manyChapters, descending = true)

        assertEquals("31-45", catalog.ranges.first().key)
        assertEquals(45, catalog.ranges.first().chapters.first().index)
        assertEquals("31-45", catalog.rangeKeyByChapterId["chapter-40"])
    }
}
