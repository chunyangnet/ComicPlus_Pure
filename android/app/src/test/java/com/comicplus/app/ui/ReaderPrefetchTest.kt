package com.comicplus.app.ui

import com.comicplus.app.data.source.DirectReaderPage
import com.comicplus.app.ui.screens.pagerPageToReadingIndex
import com.comicplus.app.ui.screens.readerPagedPrefetchIndices
import com.comicplus.app.ui.screens.readerPrefetchPlan
import com.comicplus.app.ui.screens.readerPrefetchPlanForMode
import com.comicplus.app.ui.screens.readerPrefetchIndices
import com.comicplus.app.ui.screens.readingIndexToPagerPage
import com.comicplus.app.ui.screens.shouldPreloadNextChapter
import com.comicplus.app.ui.screens.verticalListIndexForPosition
import com.comicplus.app.ui.screens.verticalPagePositionByDelta
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPrefetchTest {
    @Test
    fun readerDefaultsToMediumQuality() {
        assertEquals(ReaderImageQuality.Medium, AppSettings().readerImageQuality)
    }

    @Test
    fun prefetchIndices_onlyReturnsFollowingPagesWithinBounds() {
        assertEquals(listOf(5, 6, 7), readerPrefetchIndices(4, 8, 6))
        assertEquals(emptyList<Int>(), readerPrefetchIndices(7, 8, 3))
        assertEquals(emptyList<Int>(), readerPrefetchIndices(0, 0, 3))
    }

    @Test
    fun dataSaverCapsPrefetchToOnePage() {
        assertEquals(1, effectiveReaderPrefetchPages(configuredPages = 6, dataSaver = true))
        assertEquals(6, effectiveReaderPrefetchPages(configuredPages = 6, dataSaver = false))
        assertEquals(1, effectiveReaderPrefetchPages(configuredPages = 6, dataSaver = false, memoryClassMb = 256))
        assertEquals(2, effectiveReaderPrefetchPages(configuredPages = 6, dataSaver = false, memoryClassMb = 384))
    }

    @Test
    fun highLatencySmartModeAddsOneWarmPageWithoutIgnoringMemoryCap() {
        assertEquals(
            4,
            effectiveReaderPrefetchPages(
                configuredPages = 3,
                dataSaver = false,
                memoryClassMb = 768,
                networkLatencyMs = 600,
            ),
        )
        assertEquals(
            1,
            effectiveReaderPrefetchPages(
                configuredPages = 6,
                dataSaver = false,
                memoryClassMb = 256,
                networkLatencyMs = 600,
            ),
        )
    }

    @Test
    fun turboModeStartsEarlierWithoutOverfillingLowMemoryDevices() {
        assertEquals(
            3,
            effectiveReaderPrefetchPages(
                configuredPages = 1,
                dataSaver = false,
                memoryClassMb = 256,
                turboMode = true,
            ),
        )
        assertEquals(
            5,
            effectiveReaderPrefetchPages(
                configuredPages = 6,
                dataSaver = false,
                memoryClassMb = 768,
                turboMode = true,
            ),
        )
    }

    @Test
    fun ultraAggressiveUsesAQualityAndMemoryBoundedForwardRunway() {
        assertEquals(
            8,
            effectiveReaderPrefetchPages(
                configuredPages = 1,
                dataSaver = false,
                memoryClassMb = 768,
                mode = ReaderPrefetchMode.UltraAggressive,
            ),
        )
        assertEquals(
            12,
            effectiveReaderPrefetchPages(
                configuredPages = 1,
                dataSaver = false,
                memoryClassMb = 768,
                turboMode = true,
                mode = ReaderPrefetchMode.UltraAggressive,
            ),
        )
        assertEquals(
            6,
            effectiveReaderPrefetchPages(
                configuredPages = 1,
                dataSaver = false,
                memoryClassMb = 768,
                mode = ReaderPrefetchMode.UltraAggressive,
                imageQuality = ReaderImageQuality.High,
            ),
        )
        assertEquals(
            4,
            effectiveReaderPrefetchPages(
                configuredPages = 1,
                dataSaver = false,
                memoryClassMb = 384,
                mode = ReaderPrefetchMode.UltraAggressive,
                imageQuality = ReaderImageQuality.Low,
            ),
        )
        assertEquals(
            1,
            effectiveReaderPrefetchPages(
                configuredPages = 6,
                dataSaver = true,
                memoryClassMb = 768,
                mode = ReaderPrefetchMode.UltraAggressive,
            ),
        )
    }

    @Test
    fun pagedPrefetchKeepsThePreviousPageWarm() {
        assertEquals(listOf(5, 3, 6), readerPagedPrefetchIndices(4, 8, 3))
        assertEquals(listOf(1, 2), readerPagedPrefetchIndices(0, 8, 2))
    }

    @Test
    fun prefetchPlanFollowsTravelDirectionBeforeBacktracking() {
        assertEquals(
            listOf(6, 7, 4, 3),
            readerPrefetchPlan(5, 10, 4, direction = 1, includeOpposite = true),
        )
        assertEquals(
            listOf(4, 3, 6, 7),
            readerPrefetchPlan(5, 10, 4, direction = -1, includeOpposite = true),
        )
        assertEquals(
            listOf(1, 2, 3, 4),
            readerPrefetchPlan(0, 10, 4, direction = 1, includeOpposite = true),
        )
    }

    @Test
    fun ultraAggressivePagedModeSpendsItsWholeWindowAhead() {
        assertEquals(
            listOf(6, 7, 8, 9),
            readerPrefetchPlanForMode(
                currentPageIndex = 5,
                pageCount = 12,
                distance = 4,
                direction = 1,
                readerMode = ReaderMode.Paged,
                prefetchMode = ReaderPrefetchMode.UltraAggressive,
            ),
        )
        assertEquals(
            listOf(6, 7, 4, 3),
            readerPrefetchPlanForMode(
                currentPageIndex = 5,
                pageCount = 12,
                distance = 4,
                direction = 1,
                readerMode = ReaderMode.Paged,
                prefetchMode = ReaderPrefetchMode.Smart,
            ),
        )
    }

    @Test
    fun prefetchPlanFillsAvailablePagesAtChapterEdges() {
        assertEquals(
            listOf(8, 7, 6, 5),
            readerPrefetchPlan(9, 10, 4, direction = 1, includeOpposite = true),
        )
        assertEquals(
            listOf(1, 2, 3, 4),
            readerPrefetchPlan(0, 10, 4, direction = -1, includeOpposite = true),
        )
        assertEquals(
            listOf(6, 4, 3, 2, 1, 0),
            readerPrefetchPlan(5, 7, 6, direction = 1, includeOpposite = true),
        )
    }

    @Test
    fun rightToLeftPagerMappingIsReversible() {
        repeat(12) { readingIndex ->
            val pagerPage = readingIndexToPagerPage(readingIndex, 12, ReaderDirection.RightToLeft)
            assertEquals(readingIndex, pagerPageToReadingIndex(pagerPage, 12, ReaderDirection.RightToLeft))
        }
    }

    @Test
    fun nextChapterMetadataLoadsBeforeTheBoundary() {
        assertEquals(false, shouldPreloadNextChapter(currentPageIndex = 12, pageCount = 20, distance = 6))
        assertEquals(true, shouldPreloadNextChapter(currentPageIndex = 13, pageCount = 20, distance = 6))
        assertEquals(true, shouldPreloadNextChapter(currentPageIndex = 0, pageCount = 1, distance = 0))
        assertEquals(false, shouldPreloadNextChapter(currentPageIndex = 0, pageCount = 0, distance = 6))
    }

    @Test
    fun verticalChapterJumpsIncludeInsertedCommentRows() {
        val segments = listOf(
            segment("10", 3, chapterIndex = 0),
            segment("20", 2, chapterIndex = 1),
            segment("30", 4, chapterIndex = 2),
        )

        assertEquals(0, verticalListIndexForPosition(segments, "10", 0))
        assertEquals(4, verticalListIndexForPosition(segments, "20", 0))
        assertEquals(7, verticalListIndexForPosition(segments, "30", 0))
        assertEquals(10, verticalListIndexForPosition(segments, "30", 3))
    }

    @Test
    fun verticalHardwarePagingSkipsChapterBoundaryRows() {
        val segments = listOf(
            segment("10", 2, chapterIndex = 0),
            segment("20", 2, chapterIndex = 1),
        )

        assertEquals("20" to 0, verticalPagePositionByDelta(segments, "10", 1, 1))
        assertEquals("10" to 1, verticalPagePositionByDelta(segments, "20", 0, -1))
        assertEquals("20" to 1, verticalPagePositionByDelta(segments, "20", 1, 5))
    }

    private fun segment(chapterId: String, pageCount: Int, chapterIndex: Int) = ReaderChapterSegment(
        chapterId = chapterId,
        chapterTitle = "Chapter $chapterId",
        chapterIndex = chapterIndex,
        pages = List(pageCount) { index ->
            DirectReaderPage(
                index = index + 1,
                photoId = chapterId,
                fileName = "${index + 1}.jpg",
                scrambleId = "0",
                url = "https://example.com/$chapterId/${index + 1}.jpg",
                referer = "https://example.com/",
            )
        },
    )
}
