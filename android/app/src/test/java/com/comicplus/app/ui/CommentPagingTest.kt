package com.comicplus.app.ui

import com.comicplus.app.ui.screens.containsSpoilerComment
import com.comicplus.app.ui.screens.nextVisibleReplyCount
import com.comicplus.app.ui.screens.shouldLoadMoreComments
import org.junit.Assert.assertEquals
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

    @Test
    fun spoilerDetectionIncludesNestedReplies() {
        assertFalse(containsSpoilerComment(listOf(JmCommentUiItem(id = "plain"))))
        assertTrue(
            containsSpoilerComment(
                listOf(
                    JmCommentUiItem(
                        id = "root",
                        replies = listOf(JmCommentUiItem(id = "reply", spoiler = true)),
                    ),
                ),
            ),
        )
    }

    @Test
    fun replyExpansionAddsOneBoundedBatchAtATime() {
        assertEquals(13, nextVisibleReplyCount(current = 3, total = 40))
        assertEquals(23, nextVisibleReplyCount(current = 13, total = 40))
        assertEquals(25, nextVisibleReplyCount(current = 23, total = 25))
    }
}
