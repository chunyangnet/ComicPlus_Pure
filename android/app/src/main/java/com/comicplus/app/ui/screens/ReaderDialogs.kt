package com.comicplus.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.comicplus.app.data.source.SourceChapterDto
import com.comicplus.app.ui.AppSettings
import com.comicplus.app.ui.JmCommentsUiState
import com.comicplus.app.ui.icons.ComicPlusIcons as Icons
import com.comicplus.app.ui.theme.ComicPlusTheme
import com.comicplus.app.ui.theme.White

@Composable
internal fun ReaderCommentsDialog(
    chapter: SourceChapterDto,
    state: JmCommentsUiState,
    settings: AppSettings,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(chapter.sourceChapterId) {
        listState.scrollToItem(0)
    }
    CommentListLoadMoreEffect(
        listState = listState,
        contentKey = chapter.sourceChapterId,
        enabled = state.loaded && state.items.isNotEmpty(),
        loading = state.loadingMore,
        hasMore = state.hasMore,
        error = state.error,
        onLoadMore = onLoadMore,
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ComicPlusTheme(
            paletteKey = settings.paletteKey,
            darkMode = true,
            reduceMotion = settings.reduceMotion,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(.92f),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("本章评论", color = White, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    chapter.title,
                                    color = White.copy(alpha = .56f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Outlined.Close, contentDescription = "关闭评论", tint = White.copy(alpha = .82f))
                            }
                        }
                        Spacer(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
                        ) {
                            item(key = "reader-comments-summary", contentType = "reader-comments-summary") {
                                Text(
                                    when {
                                        state.loading -> "正在读取评论"
                                        state.total > 0L -> "共 ${formatCommentCount(state.total)} 条"
                                        state.loaded -> "暂时没有评论"
                                        else -> "JM 官方评论"
                                    },
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                    color = White.copy(alpha = .52f),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            when {
                                state.loading && state.items.isEmpty() -> item(
                                    key = "reader-comment-loading",
                                    contentType = "reader-comment-loading",
                                ) {
                                    CommentListSkeleton(reduceMotion = settings.reduceMotion)
                                }

                                state.error != null && state.items.isEmpty() -> item(
                                    key = "reader-comment-error",
                                    contentType = "reader-comment-error",
                                ) {
                                    CommentStateMessage(
                                        message = state.error,
                                        actionLabel = "重试",
                                        onAction = onRetry,
                                        modifier = Modifier.fillMaxWidth().height(260.dp),
                                    )
                                }

                                state.loaded && state.items.isEmpty() -> item(
                                    key = "reader-comment-empty",
                                    contentType = "reader-comment-empty",
                                ) {
                                    CommentStateMessage(
                                        message = "暂时没有评论",
                                        modifier = Modifier.fillMaxWidth().height(260.dp),
                                    )
                                }

                                else -> {
                                    items(
                                        items = state.items,
                                        key = { it.id },
                                        contentType = { "reader-comment" },
                                    ) { comment ->
                                        OfficialCommentItem(comment)
                                    }
                                    item(key = "reader-comment-footer", contentType = "reader-comment-footer") {
                                        CommentListFooter(
                                            loading = state.loadingMore,
                                            hasMore = state.hasMore,
                                            error = state.error,
                                            onRetry = onRetry,
                                            onLoadMore = onLoadMore,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChapterMenuDialog(
    chapters: List<SourceChapterDto>,
    selectedChapterId: String,
    onSelect: (SourceChapterDto) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedIndex = chapters.indexOfFirst { it.sourceChapterId == selectedChapterId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(max = 650.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF171719),
        ) {
            Column(Modifier.padding(top = 20.dp)) {
                Text("章节目录", modifier = Modifier.padding(horizontal = 20.dp), color = White, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text("共 ${chapters.size} 话", modifier = Modifier.padding(horizontal = 20.dp), color = White.copy(alpha = .5f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                LazyColumn(state = listState, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp)) {
                    items(chapters, key = { "dialog-${it.sourceChapterId}-${it.index}" }) { chapter ->
                        val selected = chapter.sourceChapterId == selectedChapterId
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp).clickable { onSelect(chapter) },
                            shape = RoundedCornerShape(6.dp),
                            color = if (selected) White.copy(alpha = .12f) else Color.Transparent,
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(chapter.index.toString(), color = White.copy(alpha = if (selected) .9f else .42f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(38.dp))
                                Text(chapter.title, color = White.copy(alpha = if (selected) 1f else .72f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
