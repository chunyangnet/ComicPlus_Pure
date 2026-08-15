package com.comicplus.app.ui

import com.comicplus.app.ui.screens.pagerPageToReadingIndex
import com.comicplus.app.ui.screens.readerPagedPrefetchIndices
import com.comicplus.app.ui.screens.readerPrefetchPlan
import com.comicplus.app.ui.screens.readerPrefetchIndices
import com.comicplus.app.ui.screens.readingIndexToPagerPage
import com.comicplus.app.ui.screens.shouldPreloadNextChapter
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
}
