package com.comicplus.app.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.comicplus.app.ui.JmCommentUiItem

/**
 * Keeps the scroll observer isolated from the detail/readers' large UI trees.
 * The callbacks are wrapped in updated state so a recomposition never creates
 * another collector just because the ViewModel lambda identity changed.
 */
@Composable
internal fun CommentListLoadMoreEffect(
    listState: LazyListState,
    contentKey: Any?,
    enabled: Boolean,
    loading: Boolean,
    hasMore: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
) {
    val currentCanLoadMore = rememberUpdatedState(enabled && !loading && hasMore && error == null)
    val currentOnLoadMore = rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState, contentKey, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            shouldLoadMoreComments(
                lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
                totalItemsCount = layoutInfo.totalItemsCount,
            )
        }.collect { nearEnd ->
            if (nearEnd && currentCanLoadMore.value) currentOnLoadMore.value()
        }
    }
}

internal fun shouldLoadMoreComments(
    lastVisibleItemIndex: Int,
    totalItemsCount: Int,
    prefetchDistance: Int = 2,
): Boolean {
    if (lastVisibleItemIndex < 0 || totalItemsCount <= 0) return false
    val triggerIndex = (totalItemsCount - 1 - prefetchDistance.coerceAtLeast(0)).coerceAtLeast(0)
    return lastVisibleItemIndex >= triggerIndex
}

internal fun formatCommentCount(value: Long): String = when {
    value >= 100_000_000L -> "${value / 100_000_000L}亿+"
    value >= 10_000L -> "${value / 10_000L}万+"
    else -> value.coerceAtLeast(0L).toString()
}

internal fun containsSpoilerComment(items: List<JmCommentUiItem>): Boolean =
    items.any { comment -> comment.spoiler || containsSpoilerComment(comment.replies) }
