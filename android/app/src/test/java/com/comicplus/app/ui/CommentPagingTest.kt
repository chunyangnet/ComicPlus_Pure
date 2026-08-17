package com.comicplus.app.ui

import com.comicplus.app.ui.screens.shouldLoadMoreComments
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentPagingTest {
    @Test
    fun loadMoreStartsOnlyWhenTheListApproachesItsEnd() {
        assertFalse(shouldLoadMoreComments(lastVisibleItemIndex = -1, totalItemsCount = 20))
        assertFalse(shouldLoadMoreComments(lastVisibleItemIndex = 15, totalItemsCount = 20))
        assertTrue(shouldLoadMoreComments(lastVisibleItemIndex = 17, totalItemsCount = 20))
        assertTrue(shouldLoadMoreComments(lastVisibleItemIndex = 19, totalItemsCount = 20))
    }

    @Test
    fun loadMoreThresholdHandlesShortListsAndInvalidDistance() {
        assertTrue(shouldLoadMoreComments(lastVisibleItemIndex = 0, totalItemsCount = 1))
        assertFalse(shouldLoadMoreComments(lastVisibleItemIndex = 0, totalItemsCount = 0))
        assertFalse(
            shouldLoadMoreComments(
                lastVisibleItemIndex = 8,
                totalItemsCount = 10,
                prefetchDistance = -1,
            ),
        )
        assertTrue(
            shouldLoadMoreComments(
                lastVisibleItemIndex = 9,
                totalItemsCount = 10,
                prefetchDistance = -1,
            ),
        )
    }
}
