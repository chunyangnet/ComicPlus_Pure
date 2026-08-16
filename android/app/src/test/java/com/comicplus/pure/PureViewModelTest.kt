package com.comicplus.pure

import com.comicplus.app.ui.ComicUiItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PureViewModelTest {
    @Test
    fun mergeComicPage_keepsOrderAndRemovesDuplicates() {
        val existing = listOf(comic("1"), comic("2"))
        val incoming = listOf(comic("2"), comic("3"), comic("3"))

        assertEquals(listOf("1", "2", "3"), mergeComicPage(existing, incoming).map(ComicUiItem::jmId))
    }

    @Test
    fun progressUpdatesAreThrottledButCompletionAlwaysPublishes() {
        assertEquals(false, shouldPublishProgress(0.20f, 0.205f, 50_000_000L, completed = false))
        assertEquals(true, shouldPublishProgress(0.20f, 0.22f, 50_000_000L, completed = false))
        assertEquals(true, shouldPublishProgress(0.20f, 0.205f, 160_000_000L, completed = false))
        assertEquals(true, shouldPublishProgress(0.99f, 1f, 1L, completed = true))
    }

    @Test
    fun paginationAcceptsOnlyTheRequestedForwardPage() {
        assertEquals(true, isForwardPageResponse(previousPage = 1, requestedPage = 2, responsePage = 2))
        assertEquals(false, isForwardPageResponse(previousPage = 2, requestedPage = 2, responsePage = 2))
        assertEquals(false, isForwardPageResponse(previousPage = 1, requestedPage = 2, responsePage = 1))
        assertEquals(false, isForwardPageResponse(previousPage = 1, requestedPage = 2, responsePage = 3))
    }

    private fun comic(id: String) = ComicUiItem(
        jmId = id,
        title = "Comic $id",
        subtitle = "",
        metric = "",
        accentIndex = 0,
    )
}
