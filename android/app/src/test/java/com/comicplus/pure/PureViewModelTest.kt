package com.comicplus.pure

import com.comicplus.app.ui.ComicUiItem
import com.comicplus.app.ui.JmCommentUiItem
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
    fun duplicateSearchPageStopsPaginationEvenWhenServerReportsMore() {
        val existing = listOf(comic("1"), comic("2"))

        assertEquals(
            false,
            shouldContinueComicPagination(
                existing = existing,
                incoming = listOf(comic("2"), comic("1")),
                serverHasMore = true,
            ),
        )
        assertEquals(
            true,
            shouldContinueComicPagination(
                existing = existing,
                incoming = listOf(comic("2"), comic("3")),
                serverHasMore = true,
            ),
        )
        assertEquals(
            false,
            shouldContinueComicPagination(
                existing = existing,
                incoming = listOf(comic("3")),
                serverHasMore = false,
            ),
        )
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

    @Test
    fun duplicateCommentPageStopsAutomaticPagination() {
        val existing = listOf(comment("1"), comment("2"))

        assertEquals(
            false,
            shouldContinueCommentPagination(existing, listOf(comment("2"), comment("1")), serverHasMore = true),
        )
        assertEquals(
            true,
            shouldContinueCommentPagination(existing, listOf(comment("2"), comment("3")), serverHasMore = true),
        )
        assertEquals(
            false,
            shouldContinueCommentPagination(existing, listOf(comment("3")), serverHasMore = false),
        )
    }

    @Test
    fun readerEntryWarmupPrioritizesCurrentAndForwardPages() {
        assertEquals(listOf(4, 5, 6), readerEntryWarmupIndices(4, pageCount = 10, pageBudget = 3))
        assertEquals(listOf(9, 8, 7), readerEntryWarmupIndices(9, pageCount = 10, pageBudget = 3))
        assertEquals(listOf(0), readerEntryWarmupIndices(-5, pageCount = 1, pageBudget = 3))
        assertEquals(emptyList<Int>(), readerEntryWarmupIndices(0, pageCount = 0, pageBudget = 3))
    }

    @Test
    fun favoriteOptimisticAddAndRollbackDoNotDisturbOtherItems() {
        val original = listOf(comic("1"), comic("2"))
        val item = comic("3")
        val snapshot = favoriteMutationSnapshot(original, item)
        val optimistic = favoriteItemsWithMembership(original, item, shouldBeFavorite = true)
        val withAnotherChange = optimistic + comic("4")
        val rolledBack = favoriteItemsWithMembership(
            withAnotherChange,
            item,
            shouldBeFavorite = snapshot.wasFavorite,
            insertionIndex = snapshot.originalIndex,
        )

        assertEquals(false, snapshot.wasFavorite)
        assertEquals(listOf("3", "1", "2"), optimistic.map(ComicUiItem::jmId))
        assertEquals(listOf("1", "2", "4"), rolledBack.map(ComicUiItem::jmId))
    }

    @Test
    fun favoriteRemovalRollbackRestoresOriginalRelativePosition() {
        val original = listOf(comic("1"), comic("2"), comic("3"))
        val item = original[1]
        val snapshot = favoriteMutationSnapshot(original, item)
        val optimistic = favoriteItemsWithMembership(original, item, shouldBeFavorite = false)
        val rolledBack = favoriteItemsWithMembership(
            optimistic,
            item,
            shouldBeFavorite = snapshot.wasFavorite,
            insertionIndex = snapshot.originalIndex,
        )

        assertEquals(true, snapshot.wasFavorite)
        assertEquals(listOf("1", "3"), optimistic.map(ComicUiItem::jmId))
        assertEquals(listOf("1", "2", "3"), rolledBack.map(ComicUiItem::jmId))
        assertEquals(8L, adjustedFavoriteCount(currentCount = 7L, loadedCount = 3, adding = true))
        assertEquals(6L, adjustedFavoriteCount(currentCount = 7L, loadedCount = 3, adding = false))
    }

    private fun comic(id: String) = ComicUiItem(
        jmId = id,
        title = "Comic $id",
        subtitle = "",
        metric = "",
        accentIndex = 0,
    )

    private fun comment(id: String) = JmCommentUiItem(id = id)
}
