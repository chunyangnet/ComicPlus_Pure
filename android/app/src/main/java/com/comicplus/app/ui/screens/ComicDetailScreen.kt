package com.comicplus.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.comicplus.app.ui.icons.ComicPlusIcons as Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import coil3.compose.AsyncImage
import com.comicplus.app.data.source.SourceChapterDto
import com.comicplus.app.ui.ComicResolveUiState
import com.comicplus.app.ui.JmCommentUiItem
import com.comicplus.app.ui.JmCommentsUiState
import com.comicplus.app.ui.LocalFavoritePendingKeys
import com.comicplus.app.ui.markdownToAnnotatedString
import com.comicplus.app.ui.rememberDelayedBusyIndicator
import com.comicplus.app.ui.components.ComicCover
import com.comicplus.app.ui.components.CpDimens
import com.comicplus.app.ui.components.FavoriteButton
import com.comicplus.app.ui.components.PillRow
import com.comicplus.app.ui.components.SearchCapsule
import com.comicplus.app.ui.components.SegmentedControl
import com.comicplus.app.ui.components.ShimmerBlock
import com.comicplus.app.ui.components.pressFeedback
import com.comicplus.app.ui.components.rememberShimmerBrush
import com.comicplus.app.ui.theme.Canvas
import com.comicplus.app.ui.theme.Ink
import com.comicplus.app.ui.theme.InkSoft
import com.comicplus.app.ui.theme.Line
import com.comicplus.app.ui.theme.Muted
import com.comicplus.app.ui.theme.SurfaceSoft
import com.comicplus.app.ui.theme.White
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ComicDetailScreen(
    state: ComicResolveUiState,
    reduceMotion: Boolean,
    autoResumeReading: Boolean,
    chapterDescending: Boolean = false,
    onChapterDescendingChange: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    onShare: (ComicResolveUiState.Ready) -> Unit,
    onRead: (ComicResolveUiState.Ready, SourceChapterDto) -> Unit,
    modifier: Modifier = Modifier,
    onContinueReading: (ComicResolveUiState.Ready, SourceChapterDto, Int) -> Unit = { detail, chapter, _ ->
        onRead(detail, chapter)
    },
    onOpenComments: (String) -> Unit = {},
    downloadedChapterIds: Set<String> = emptySet(),
    downloadProgress: Map<String, Float> = emptyMap(),
    onDownload: (ComicResolveUiState.Ready, SourceChapterDto) -> Unit = { _, _ -> },
    isFavorite: Boolean = false,
    onToggleFavorite: (ComicResolveUiState.Ready) -> Unit = {},
    comments: JmCommentsUiState = JmCommentsUiState(),
    onRetryComments: () -> Unit = {},
    onLoadMoreComments: () -> Unit = {},
) {
    val favoritePending = (state as? ComicResolveUiState.Ready)?.let { ready ->
        "${ready.source}:${ready.jmId}" in LocalFavoritePendingKeys.current
    } == true
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Canvas,
        topBar = {
            DetailTopBar(
                onBack = onBack,
                onShare = (state as? ComicResolveUiState.Ready)?.let { ready -> { onShare(ready) } },
                isFavorite = (state as? ComicResolveUiState.Ready)?.let { isFavorite },
                onToggleFavorite = (state as? ComicResolveUiState.Ready)?.let { ready -> { onToggleFavorite(ready) } },
                favoritePending = favoritePending,
            )
        },
    ) { padding ->
        when (state) {
            ComicResolveUiState.Idle -> Unit
            is ComicResolveUiState.Loading -> DetailLoading(
                label = "正在打开 JM 官方源 JM${state.jmId}",
                reduceMotion = reduceMotion,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is ComicResolveUiState.Error -> DetailError(
                state = state,
                onBack = onBack,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is ComicResolveUiState.Ready -> DetailReady(
                state = state,
                autoResumeReading = autoResumeReading,
                chapterDescending = chapterDescending,
                onChapterDescendingChange = onChapterDescendingChange,
                onRead = onRead,
                onContinueReading = onContinueReading,
                onOpenComments = onOpenComments,
                downloadedChapterIds = downloadedChapterIds,
                downloadProgress = downloadProgress,
                onDownload = onDownload,
                isFavorite = isFavorite,
                onToggleFavorite = { onToggleFavorite(state) },
                comments = comments,
                onRetryComments = onRetryComments,
                onLoadMoreComments = onLoadMoreComments,
                reduceMotion = reduceMotion,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun DetailTopBar(
    onBack: () -> Unit,
    onShare: (() -> Unit)?,
    isFavorite: Boolean?,
    onToggleFavorite: (() -> Unit)?,
    favoritePending: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(66.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailTopBarAction(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )
            Text(
                "漫画详情",
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                color = Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (onToggleFavorite != null && isFavorite != null) {
                DetailTopBarAction(
                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "加入收藏",
                    onClick = onToggleFavorite,
                    loading = favoritePending,
                )
            }
            if (onShare != null) {
                DetailTopBarAction(
                    icon = Icons.Outlined.Share,
                    contentDescription = "分享漫画",
                    onClick = onShare,
                )
            } else {
                Spacer(Modifier.size(40.dp))
            }
        }
    }
}

@Composable
private fun DetailTopBarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    loading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val showLoading = rememberDelayedBusyIndicator(loading)
    Surface(
        modifier = Modifier
            .size(40.dp)
            .pressFeedback(interactionSource, pressedScale = .92f),
        color = if (pressed) SurfaceSoft else MaterialTheme.colorScheme.surface,
        shape = CircleShape,
        shadowElevation = if (pressed) 0.dp else 1.dp,
    ) {
        IconButton(
            onClick = onClick,
            enabled = !loading,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (showLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    icon,
                    contentDescription = if (loading) "收藏同步中" else contentDescription,
                    tint = Ink,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailReady(
    state: ComicResolveUiState.Ready,
    autoResumeReading: Boolean,
    chapterDescending: Boolean,
    onChapterDescendingChange: (Boolean) -> Unit,
    reduceMotion: Boolean,
    onRead: (ComicResolveUiState.Ready, SourceChapterDto) -> Unit,
    onContinueReading: (ComicResolveUiState.Ready, SourceChapterDto, Int) -> Unit,
    onOpenComments: (String) -> Unit,
    downloadedChapterIds: Set<String>,
    downloadProgress: Map<String, Float>,
    onDownload: (ComicResolveUiState.Ready, SourceChapterDto) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    comments: JmCommentsUiState,
    onRetryComments: () -> Unit,
    onLoadMoreComments: () -> Unit,
    modifier: Modifier,
) {
    val chapters = remember(state.jmId, state.chapters) {
        state.chapters.ifEmpty {
            listOf(SourceChapterDto(sourceChapterId = state.jmId, index = 1, title = "第 1 话"))
        }
    }
    val readingEntry = remember(chapters, state.resumeChapterId, state.resumePageIndex) {
        resolveReadingEntry(chapters, state.resumeChapterId, state.resumePageIndex)
    } ?: return
    val savedChapter = readingEntry.chapter.takeIf { readingEntry.resumed }
    val resumableChapterId = savedChapter?.sourceChapterId?.takeIf { autoResumeReading }
    var query by rememberSaveable(state.jmId) { mutableStateOf("") }
    var descending by rememberSaveable(state.jmId) { mutableStateOf(chapterDescending) }
    LaunchedEffect(chapterDescending, state.jmId) {
        descending = chapterDescending
    }
    val entryChapter = readingEntry.chapter
    val entryPageIndex = readingEntry.pageIndex
    val chapterCatalog = remember(chapters, descending) {
        buildDetailChapterCatalog(chapters, descending)
    }
    val ranges = chapterCatalog.ranges
    val defaultSelection = remember(chapterCatalog, resumableChapterId) {
        defaultDetailChapterSelection(chapterCatalog, resumableChapterId)
    } ?: return
    var selectedChapterId by rememberSaveable(state.jmId) {
        mutableStateOf(defaultSelection.chapterId)
    }
    var selectedRangeKey by rememberSaveable(state.jmId) {
        mutableStateOf(defaultSelection.rangeKey)
    }
    LaunchedEffect(defaultSelection) {
        selectedChapterId = defaultSelection.chapterId
        selectedRangeKey = defaultSelection.rangeKey
    }
    val selectedRange = chapterCatalog.rangesByKey[selectedRangeKey] ?: ranges.first()
    val visibleChapters = remember(chapters, selectedRange, query, descending) {
        val keyword = query.trim()
        val base = if (keyword.isBlank()) selectedRange.chapters else chapters.filter {
            it.title.contains(keyword, ignoreCase = true) || it.index.toString() == keyword
        }.let { if (descending) it.asReversed() else it }
        base
    }
    val chapterListState = rememberLazyListState()
    val commentListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedSection by rememberSaveable(state.jmId) { mutableStateOf(DetailSection.Chapters.name) }
    val section = DetailSection.entries.firstOrNull { it.name == selectedSection } ?: DetailSection.Chapters
    val visibleComments = comments.takeIf {
        it.comicId == state.jmId && it.chapterId == state.jmId
    } ?: JmCommentsUiState(
        comicId = state.jmId,
        chapterId = state.jmId,
        loading = true,
    )
    var revealSpoilersRequest by rememberSaveable(state.jmId) { mutableIntStateOf(0) }
    val hasSpoilers = remember(visibleComments.items) {
        containsSpoilerComment(visibleComments.items)
    }
    val chapterContentStartIndex = if (ranges.size > 1 && query.isBlank()) 5 else 4
    LaunchedEffect(selectedRangeKey, query, descending) {
        if (section == DetailSection.Chapters && chapterListState.firstVisibleItemIndex > chapterContentStartIndex) {
            chapterListState.scrollToItem(chapterContentStartIndex)
        }
    }
    LaunchedEffect(section, state.jmId) {
        if (section == DetailSection.Comments) {
            onOpenComments(state.jmId)
            commentListState.scrollToItem(0)
        }
    }
    CommentListLoadMoreEffect(
        listState = commentListState,
        contentKey = "${state.jmId}:all",
        enabled = section == DetailSection.Comments && visibleComments.loaded && visibleComments.items.isNotEmpty(),
        loading = visibleComments.loadingMore,
        hasMore = visibleComments.hasMore,
        error = visibleComments.error,
        onLoadMore = onLoadMoreComments,
    )

    Scaffold(
        modifier = modifier,
        containerColor = Canvas,
        bottomBar = {
            if (section == DetailSection.Chapters) {
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 5.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(if (savedChapter != null) "上次阅读" else "开始章节", color = Muted, style = MaterialTheme.typography.labelSmall)
                            Text(
                                entryChapter.title,
                                color = Ink,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Button(
                            onClick = { onContinueReading(state, entryChapter, entryPageIndex) },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(CpDimens.controlRadius),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                        ) {
                            Text(
                                if (
                                    savedChapter != null
                                ) {
                                    "继续阅读"
                                } else {
                                    "开始阅读"
                                },
                                color = White,
                            )
                            Spacer(Modifier.width(5.dp))
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = if (section == DetailSection.Chapters) chapterListState else commentListState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 18.dp),
        ) {
            item(key = "detail-header", contentType = "detail-header") {
                ComicDetailHeader(
                    state,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier.padding(horizontal = CpDimens.screenPadding),
                )
                Spacer(Modifier.height(14.dp))
            }
            stickyHeader(key = "detail-section-tabs", contentType = "detail-section-tabs") {
                Surface(color = Canvas, shadowElevation = 1.dp) {
                    SegmentedControl(
                        labels = DetailSection.entries.map(DetailSection::label),
                        selected = section.label,
                        onSelected = { label ->
                            selectedSection = DetailSection.entries.first { it.label == label }.name
                        },
                        modifier = Modifier.padding(horizontal = CpDimens.screenPadding, vertical = 8.dp),
                    )
                }
            }
            when (section) {
                DetailSection.Chapters -> {
                    item(key = "chapter-heading", contentType = "chapter-heading") {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("章节目录", color = Ink, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "共 ${chapters.size} 话${if (ranges.size > 1) " · ${ranges.size} 个区间" else ""}",
                                    color = Muted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Surface(
                                modifier = Modifier.clickable {
                                    val next = !descending
                                    descending = next
                                    onChapterDescendingChange(next)
                                },
                                shape = CircleShape,
                                color = SurfaceSoft,
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.SwapVert, contentDescription = null, tint = InkSoft, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(if (descending) "倒序" else "正序", color = InkSoft, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    item(key = "chapter-search", contentType = "chapter-search") {
                        Spacer(Modifier.height(11.dp))
                        SearchCapsule(
                            query = query,
                            onQueryChange = { query = it },
                            onSearch = {},
                            placeholder = "搜索全部章节名称或序号",
                            showSearchAction = false,
                            modifier = Modifier.padding(horizontal = CpDimens.screenPadding),
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    if (ranges.size > 1 && query.isBlank()) {
                        item(key = "chapter-ranges", contentType = "chapter-ranges") {
                            PillRow(
                                labels = ranges.map(ChapterRange::label),
                                selectedIndex = ranges.indexOf(selectedRange),
                                onSelected = { index ->
                                    ranges.getOrNull(index)?.let { range ->
                                        selectedRangeKey = range.key
                                        scope.launch { chapterListState.animateScrollToItem(chapterContentStartIndex) }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = CpDimens.screenPadding),
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    items(
                        items = visibleChapters,
                        key = { chapter -> "${chapter.sourceChapterId}-${chapter.index}" },
                        contentType = { "chapter" },
                    ) { chapter ->
                        ChapterRow(
                            chapter = chapter,
                            selected = chapter.sourceChapterId == selectedChapterId,
                            onClick = {
                                selectedChapterId = chapter.sourceChapterId
                                chapterCatalog.rangeKeyByChapterId[chapter.sourceChapterId]
                                    ?.let { selectedRangeKey = it }
                                onRead(state, chapter)
                            },
                            downloaded = chapter.sourceChapterId in downloadedChapterIds,
                            progress = downloadProgress[chapter.sourceChapterId],
                            onDownload = { onDownload(state, chapter) },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    if (visibleChapters.isEmpty()) {
                        item(key = "chapter-empty", contentType = "chapter-empty") {
                            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                                Text("没有匹配的章节", color = Muted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                DetailSection.Comments -> {
                    item(key = "comment-heading", contentType = "comment-heading") {
                        OfficialCommentsHeader(
                            state = visibleComments,
                            hasSpoilers = hasSpoilers,
                            onRevealSpoilers = { revealSpoilersRequest += 1 },
                        )
                    }
                    when {
                        visibleComments.loading && visibleComments.items.isEmpty() -> item(
                            key = "comment-loading",
                            contentType = "comment-loading",
                        ) {
                            CommentListSkeleton(reduceMotion = reduceMotion, modifier = Modifier.fillMaxWidth())
                        }

                        visibleComments.error != null && visibleComments.items.isEmpty() -> item(
                            key = "comment-error",
                            contentType = "comment-error",
                        ) {
                            CommentStateMessage(
                                message = visibleComments.error,
                                actionLabel = "重试",
                                onAction = onRetryComments,
                                modifier = Modifier.fillMaxWidth().height(260.dp),
                            )
                        }

                        visibleComments.loaded && visibleComments.items.isEmpty() -> item(
                            key = "comment-empty",
                            contentType = "comment-empty",
                        ) {
                            CommentStateMessage(
                                message = "暂时没有评论",
                                modifier = Modifier.fillMaxWidth().height(260.dp),
                            )
                        }

                        else -> {
                            items(
                                items = visibleComments.items,
                                key = JmCommentUiItem::id,
                                contentType = { "official-comment" },
                            ) { comment ->
                                OfficialCommentItem(
                                    comment = comment,
                                    revealSpoilersRequest = revealSpoilersRequest,
                                )
                            }
                            item(key = "comment-footer", contentType = "official-comment-footer") {
                                CommentListFooter(
                                    loading = visibleComments.loadingMore,
                                    hasMore = visibleComments.hasMore,
                                    error = visibleComments.error,
                                    onRetry = onRetryComments,
                                    onLoadMore = onLoadMoreComments,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class DetailSection(val label: String) {
    Chapters("章节"),
    Comments("评论"),
}

@Composable
private fun OfficialCommentsHeader(
    state: JmCommentsUiState,
    hasSpoilers: Boolean,
    onRevealSpoilers: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("总评论", color = Ink, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(3.dp))
            Text(
                when {
                    state.loading -> "正在读取 JM 官方总评论"
                    state.total > 0L -> "共 ${formatCommentCount(state.total)} 条 · 最新优先"
                    state.loaded -> "暂时没有评论"
                    else -> "JM 官方总评论 · 最新优先"
                },
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (hasSpoilers) {
            RevealSpoilersButton(onClick = onRevealSpoilers)
        } else {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
internal fun OfficialCommentItem(
    comment: JmCommentUiItem,
    modifier: Modifier = Modifier,
    replyDepth: Int = 0,
    revealSpoilersRequest: Int = 0,
) {
    var spoilerVisible by rememberSaveable(comment.id, comment.spoiler) { mutableStateOf(!comment.spoiler) }
    LaunchedEffect(comment.id, revealSpoilersRequest) {
        if (comment.spoiler && revealSpoilersRequest > 0) spoilerVisible = true
    }
    val compact = replyDepth > 0
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = if (compact) 0.dp else CpDimens.screenPadding,
                vertical = if (compact) 9.dp else 14.dp,
            ),
            verticalAlignment = Alignment.Top,
        ) {
            CommentAvatar(
                name = comment.displayName,
                avatarUrl = comment.avatarUrl,
                size = if (compact) 28.dp else 38.dp,
            )
            Spacer(Modifier.width(if (compact) 9.dp else 12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            comment.displayName,
                            color = Ink,
                            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (comment.username.isNotBlank() && comment.username != comment.displayName) {
                            Text(
                                "@${comment.username}",
                                color = Muted,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (comment.createdAt.isNotBlank()) {
                        Text(
                            comment.createdAt,
                            modifier = Modifier.width(104.dp).padding(start = 8.dp),
                            color = Muted,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                if (comment.spoiler && !spoilerVisible) {
                    SpoilerPlaceholder(onReveal = { spoilerVisible = true })
                } else {
                    CommentText(comment.content, compact)
                }
                if (comment.likes > 0L || comment.spoiler && spoilerVisible) {
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (comment.likes > 0L) {
                            Icon(
                                Icons.Outlined.ThumbUp,
                                contentDescription = null,
                                tint = Muted,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(formatCommentCount(comment.likes), color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.weight(1f))
                        if (comment.spoiler && spoilerVisible) {
                            IconButton(
                                onClick = { spoilerVisible = false },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.VisibilityOff,
                                    contentDescription = "隐藏剧透",
                                    tint = Muted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                if (comment.replies.isNotEmpty()) {
                    Spacer(Modifier.height(9.dp))
                    if (compact) {
                        CommentReplyList(
                            parentId = comment.id,
                            replies = comment.replies,
                            replyDepth = replyDepth,
                            revealSpoilersRequest = revealSpoilersRequest,
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceSoft,
                        ) {
                            CommentReplyList(
                                parentId = comment.id,
                                replies = comment.replies,
                                replyDepth = replyDepth,
                                revealSpoilersRequest = revealSpoilersRequest,
                                modifier = Modifier.padding(horizontal = 10.dp),
                            )
                        }
                    }
                }
            }
        }
        if (!compact) {
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(Line.copy(alpha = .72f)))
        }
    }
}

@Composable
private fun CommentReplyList(
    parentId: String,
    replies: List<JmCommentUiItem>,
    replyDepth: Int,
    revealSpoilersRequest: Int,
    modifier: Modifier = Modifier,
) {
    var visibleCount by rememberSaveable(parentId, replies.size) {
        mutableIntStateOf(minOf(replies.size, INITIAL_VISIBLE_COMMENT_REPLIES))
    }
    val visibleReplies = remember(replies, visibleCount) {
        replies.take(visibleCount.coerceIn(0, replies.size))
    }
    Column(modifier) {
        visibleReplies.forEachIndexed { index, reply ->
            key("$parentId:${reply.id}:$index") {
                OfficialCommentItem(
                    comment = reply,
                    replyDepth = replyDepth + 1,
                    revealSpoilersRequest = revealSpoilersRequest,
                )
            }
            if (index < visibleReplies.lastIndex) {
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(Line.copy(alpha = .72f)))
            }
        }
        if (replies.size > INITIAL_VISIBLE_COMMENT_REPLIES) {
            Row(modifier = Modifier.align(Alignment.Start)) {
                if (visibleReplies.size < replies.size) {
                    val nextBatch = minOf(COMMENT_REPLY_BATCH_SIZE, replies.size - visibleReplies.size)
                    TextButton(
                        onClick = {
                            visibleCount = nextVisibleReplyCount(visibleCount, replies.size)
                        },
                    ) {
                        Text(
                            "再展开 $nextBatch 条回复",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                if (visibleReplies.size > INITIAL_VISIBLE_COMMENT_REPLIES) {
                    TextButton(onClick = { visibleCount = INITIAL_VISIBLE_COMMENT_REPLIES }) {
                        Text("收起", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RevealSpoilersButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Icon(
            Icons.Outlined.Visibility,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text("展开全部剧透", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CommentAvatar(
    name: String,
    avatarUrl: String?,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).ifBlank { "J" },
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun CommentText(content: String, compact: Boolean) {
    val source = content.ifBlank { "（无文字内容）" }
    val text = if (source.length <= ASYNC_COMMENT_MARKDOWN_THRESHOLD) {
        remember(source) { markdownToAnnotatedString(source) }
    } else {
        val rendered by produceState<AnnotatedString?>(initialValue = null, key1 = source) {
            value = withContext(Dispatchers.Default) { markdownToAnnotatedString(source) }
        }
        rendered
    }
    if (text == null) {
        Text(
            text = "正在排版评论…",
            color = Muted,
            style = MaterialTheme.typography.labelSmall,
        )
    } else {
        Text(
            text = text,
            color = InkSoft,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SpoilerPlaceholder(onReveal: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 11.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.VisibilityOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "剧透内容",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = onReveal, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Outlined.Visibility,
                    contentDescription = "显示剧透",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun CommentListSkeleton(reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush(animated = !reduceMotion)
    Column(modifier.padding(horizontal = CpDimens.screenPadding)) {
        repeat(4) {
            Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.Top) {
                ShimmerBlock(brush, Modifier.size(38.dp), CircleShape)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    ShimmerBlock(brush, Modifier.fillMaxWidth(.42f).height(14.dp), RoundedCornerShape(6.dp))
                    Spacer(Modifier.height(10.dp))
                    ShimmerBlock(brush, Modifier.fillMaxWidth().height(13.dp), RoundedCornerShape(6.dp))
                    Spacer(Modifier.height(7.dp))
                    ShimmerBlock(brush, Modifier.fillMaxWidth(.72f).height(13.dp), RoundedCornerShape(6.dp))
                }
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(Line.copy(alpha = .72f)))
        }
    }
}

@Composable
internal fun CommentStateMessage(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(14.dp))
                Button(onClick = onAction, shape = RoundedCornerShape(CpDimens.controlRadius)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
internal fun CommentListFooter(
    loading: Boolean,
    hasMore: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = CpDimens.screenPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            loading -> {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在加载", color = Muted, style = MaterialTheme.typography.labelMedium)
            }

            error != null -> TextButton(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("重试")
            }

            hasMore -> TextButton(onClick = onLoadMore) { Text("加载更多") }
            else -> Text("已显示全部评论", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ComicDetailHeader(
    state: ComicResolveUiState.Ready,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val favoriteKey = "${state.source}:${state.jmId}"
    val favoritePending = favoriteKey in LocalFavoritePendingKeys.current
    val showFavoriteLoading = rememberDelayedBusyIndicator(favoritePending)
    Row(modifier = modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.width(104.dp).height(139.dp).clip(RoundedCornerShape(CpDimens.cardRadius)),
        ) {
            ComicCover(
                coverUrl = state.coverUrl,
                title = state.title,
                accentIndex = state.jmId.takeLast(3).toIntOrNull() ?: 0,
                modifier = Modifier.fillMaxSize(),
            )
            FavoriteButton(
                isFavorite = isFavorite,
                onClick = onToggleFavorite,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                compact = true,
                favoriteKey = favoriteKey,
            )
        }
        Spacer(Modifier.width(17.dp))
        Column(Modifier.weight(1f)) {
            Text(
                state.title,
                color = Ink,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "JM 官方源 · JM${state.jmId}",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = CircleShape,
                color = if (state.cacheState == "direct") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    if (state.cacheState == "direct") "JM 实时只读" else "目录已缓存",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = if (state.cacheState == "direct") MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(9.dp))
            Surface(
                modifier = Modifier.clickable(enabled = !favoritePending, onClick = onToggleFavorite),
                shape = CircleShape,
                color = if (isFavorite) MaterialTheme.colorScheme.primaryContainer else SurfaceSoft,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showFavoriteLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 1.8.dp,
                        )
                    } else {
                        Icon(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else InkSoft,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(
                        when {
                            showFavoriteLoading -> "同步中"
                            isFavorite -> "已收藏"
                            else -> "加入收藏"
                        },
                        color = if (isFavorite) MaterialTheme.colorScheme.onPrimaryContainer else InkSoft,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
    if (state.description.isNotBlank()) {
        val description by produceState<AnnotatedString?>(initialValue = null, key1 = state.description) {
            value = withContext(Dispatchers.Default) {
                markdownToAnnotatedString(state.description)
            }
        }
        description?.let { rendered ->
            Spacer(Modifier.height(17.dp))
            Text(
                text = rendered,
                modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding),
                color = InkSoft,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private const val INITIAL_VISIBLE_COMMENT_REPLIES = 3
private const val COMMENT_REPLY_BATCH_SIZE = 10
private const val ASYNC_COMMENT_MARKDOWN_THRESHOLD = 256

internal fun nextVisibleReplyCount(current: Int, total: Int): Int =
    (current.coerceAtLeast(INITIAL_VISIBLE_COMMENT_REPLIES) + COMMENT_REPLY_BATCH_SIZE)
        .coerceAtMost(total.coerceAtLeast(0))

@Composable
private fun ChapterRow(
    chapter: SourceChapterDto,
    selected: Boolean,
    downloaded: Boolean,
    progress: Float?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(bottom = 7.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(CpDimens.controlRadius),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else SurfaceSoft,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(chapter.index.toString(), color = if (selected) MaterialTheme.colorScheme.primary else Muted, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                chapter.title,
                modifier = Modifier.weight(1f),
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else InkSoft,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = onDownload,
                enabled = !downloaded && progress == null,
                modifier = Modifier.size(36.dp),
            ) {
                if (progress != null) {
                    androidx.compose.material3.CircularProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (downloaded) Icons.Outlined.CheckCircle else Icons.Outlined.Download,
                        contentDescription = if (downloaded) "已下载" else "下载章节",
                        tint = if (downloaded) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            if (selected) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DetailLoading(label: String, reduceMotion: Boolean, modifier: Modifier) {
    val brush = rememberShimmerBrush(animated = !reduceMotion)
    Column(modifier.padding(horizontal = CpDimens.screenPadding, vertical = 18.dp)) {
        Row {
            ShimmerBlock(brush, Modifier.width(104.dp).height(139.dp), RoundedCornerShape(CpDimens.cardRadius))
            Spacer(Modifier.width(17.dp))
            Column(Modifier.weight(1f)) {
                ShimmerBlock(brush, Modifier.fillMaxWidth().height(24.dp), RoundedCornerShape(8.dp))
                Spacer(Modifier.height(10.dp))
                ShimmerBlock(brush, Modifier.fillMaxWidth(.58f).height(15.dp), RoundedCornerShape(7.dp))
                Spacer(Modifier.height(18.dp))
                ShimmerBlock(brush, Modifier.width(92.dp).height(28.dp), CircleShape)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(label, color = Ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("直接连接 JM 官方只读接口", color = Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))
        ShimmerBlock(brush, Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(CpDimens.controlRadius))
        Spacer(Modifier.height(12.dp))
        repeat(5) {
            ShimmerBlock(brush, Modifier.fillMaxWidth().height(56.dp), RoundedCornerShape(CpDimens.controlRadius))
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DetailError(state: ComicResolveUiState.Error, onBack: () -> Unit, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "暂时无法打开 JM 官方源 JM${state.jmId}",
                color = Ink,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(state.message, color = Muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onBack, shape = RoundedCornerShape(CpDimens.controlRadius)) {
                Text("返回")
            }
        }
    }
}

