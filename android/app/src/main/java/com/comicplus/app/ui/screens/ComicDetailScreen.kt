package com.comicplus.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.comicplus.app.data.source.SourceChapterDto
import com.comicplus.app.ui.ComicResolveUiState
import com.comicplus.app.ui.components.ComicCover
import com.comicplus.app.ui.components.CpDimens
import com.comicplus.app.ui.components.SearchCapsule
import com.comicplus.app.ui.components.ShimmerBlock
import com.comicplus.app.ui.components.pressFeedback
import com.comicplus.app.ui.components.rememberShimmerBrush
import com.comicplus.app.ui.theme.Canvas
import com.comicplus.app.ui.theme.Ink
import com.comicplus.app.ui.theme.InkSoft
import com.comicplus.app.ui.theme.Muted
import com.comicplus.app.ui.theme.SurfaceSoft
import com.comicplus.app.ui.theme.White
import kotlinx.coroutines.launch

@Composable
fun ComicDetailScreen(
    state: ComicResolveUiState,
    reduceMotion: Boolean,
    autoResumeReading: Boolean,
    onBack: () -> Unit,
    onShare: (ComicResolveUiState.Ready) -> Unit,
    onRead: (ComicResolveUiState.Ready, SourceChapterDto) -> Unit,
    modifier: Modifier = Modifier,
    downloadedChapterIds: Set<String> = emptySet(),
    downloadProgress: Map<String, Float> = emptyMap(),
    onDownload: (ComicResolveUiState.Ready, SourceChapterDto) -> Unit = { _, _ -> },
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Canvas,
        topBar = {
            DetailTopBar(
                onBack = onBack,
                onShare = (state as? ComicResolveUiState.Ready)?.let { ready -> { onShare(ready) } },
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
                onRead = onRead,
                downloadedChapterIds = downloadedChapterIds,
                downloadProgress = downloadProgress,
                onDownload = onDownload,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun DetailTopBar(onBack: () -> Unit, onShare: (() -> Unit)?) {
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
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
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(icon, contentDescription = contentDescription, tint = Ink, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DetailReady(
    state: ComicResolveUiState.Ready,
    autoResumeReading: Boolean,
    onRead: (ComicResolveUiState.Ready, SourceChapterDto) -> Unit,
    downloadedChapterIds: Set<String>,
    downloadProgress: Map<String, Float>,
    onDownload: (ComicResolveUiState.Ready, SourceChapterDto) -> Unit,
    modifier: Modifier,
) {
    val chapters = remember(state.jmId, state.chapters) {
        state.chapters.ifEmpty {
            listOf(SourceChapterDto(sourceChapterId = state.jmId, index = 1, title = "第 1 话"))
        }
    }
    val resumableChapterId = state.resumeChapterId?.takeIf { resumeId ->
        autoResumeReading && chapters.any { it.sourceChapterId == resumeId }
    }
    var selectedChapterId by rememberSaveable(state.jmId) {
        mutableStateOf(resumableChapterId ?: chapters.first().sourceChapterId)
    }
    LaunchedEffect(resumableChapterId, autoResumeReading) {
        if (autoResumeReading && resumableChapterId != null) selectedChapterId = resumableChapterId
    }
    var query by rememberSaveable(state.jmId) { mutableStateOf("") }
    var descending by rememberSaveable(state.jmId) { mutableStateOf(false) }
    val selectedChapter = chapters.firstOrNull { it.sourceChapterId == selectedChapterId } ?: chapters.first()
    val ranges = remember(chapters, descending) { buildChapterRanges(chapters, descending) }
    var selectedRangeKey by rememberSaveable(state.jmId) {
        mutableStateOf(ranges.firstOrNull()?.key.orEmpty())
    }
    LaunchedEffect(ranges, selectedChapterId) {
        if (ranges.none { it.key == selectedRangeKey }) selectedRangeKey = ranges.firstOrNull()?.key.orEmpty()
        ranges.firstOrNull { range -> range.chapters.any { it.sourceChapterId == selectedChapterId } }
            ?.let { selectedRangeKey = it.key }
    }
    val selectedRange = ranges.firstOrNull { it.key == selectedRangeKey } ?: ranges.first()
    val visibleChapters = remember(chapters, selectedRange, query, descending) {
        val keyword = query.trim()
        val base = if (keyword.isBlank()) selectedRange.chapters else chapters.filter {
            it.title.contains(keyword, ignoreCase = true) || it.index.toString() == keyword
        }.let { if (descending) it.asReversed() else it }
        base
    }
    val chapterListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(selectedRangeKey, query, descending) {
        if (chapterListState.firstVisibleItemIndex > 0) chapterListState.scrollToItem(0)
    }

    Scaffold(
        modifier = modifier,
        containerColor = Canvas,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 5.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("当前章节", color = Muted, style = MaterialTheme.typography.labelSmall)
                        Text(
                            selectedChapter.title,
                            color = Ink,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Button(
                        onClick = { onRead(state, selectedChapter) },
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(CpDimens.controlRadius),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                    ) {
                        Text(
                            if (
                                autoResumeReading &&
                                selectedChapter.sourceChapterId == state.resumeChapterId &&
                                state.resumePageIndex > 0
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
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            ComicDetailHeader(
                state,
                modifier = Modifier.padding(horizontal = CpDimens.screenPadding),
            )
            Spacer(Modifier.height(16.dp))
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
                    modifier = Modifier.clickable { descending = !descending },
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
            Row(Modifier.fillMaxWidth().weight(1f)) {
                if (ranges.size > 1 && query.isBlank()) {
                    LazyColumn(
                        modifier = Modifier.width(92.dp).fillMaxSize(),
                        contentPadding = PaddingValues(start = 10.dp, end = 6.dp, bottom = 18.dp),
                    ) {
                        items(ranges, key = { it.key }) { range ->
                            val selected = range.key == selectedRangeKey
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable {
                                    selectedRangeKey = range.key
                                    scope.launch { chapterListState.scrollToItem(0) }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            ) {
                                Column(Modifier.padding(horizontal = 9.dp, vertical = 11.dp)) {
                                    Text(
                                        range.label,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else InkSoft,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    )
                                    Text("${range.chapters.size} 话", color = Muted, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                ChapterList(
                    chapters = visibleChapters,
                    selectedChapterId = selectedChapterId,
                    listState = chapterListState,
                    onSelect = { chapter ->
                        selectedChapterId = chapter.sourceChapterId
                        ranges.firstOrNull { range -> range.chapters.any { it.sourceChapterId == chapter.sourceChapterId } }
                            ?.let { selectedRangeKey = it.key }
                    },
                    downloadedChapterIds = downloadedChapterIds,
                    downloadProgress = downloadProgress,
                    onDownload = { chapter -> onDownload(state, chapter) },
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ChapterList(
    chapters: List<SourceChapterDto>,
    selectedChapterId: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelect: (SourceChapterDto) -> Unit,
    downloadedChapterIds: Set<String>,
    downloadProgress: Map<String, Float>,
    onDownload: (SourceChapterDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 8.dp,
            end = CpDimens.screenPadding,
            bottom = 18.dp,
        ),
    ) {
        items(
            items = chapters,
            key = { chapter -> "${chapter.sourceChapterId}-${chapter.index}" },
            contentType = { "chapter" },
        ) { chapter ->
            ChapterRow(
                chapter = chapter,
                selected = chapter.sourceChapterId == selectedChapterId,
                onClick = { onSelect(chapter) },
                downloaded = chapter.sourceChapterId in downloadedChapterIds,
                progress = downloadProgress[chapter.sourceChapterId],
                onDownload = { onDownload(chapter) },
            )
        }
        if (chapters.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text("没有匹配的章节", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ComicDetailHeader(state: ComicResolveUiState.Ready, modifier: Modifier = Modifier) {
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
        }
    }
    if (state.description.isNotBlank()) {
        Spacer(Modifier.height(17.dp))
        Text(
            state.description,
            color = InkSoft,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChapterRow(
    chapter: SourceChapterDto,
    selected: Boolean,
    downloaded: Boolean,
    progress: Float?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp).clickable(onClick = onClick),
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

private data class ChapterRange(
    val key: String,
    val label: String,
    val chapters: List<SourceChapterDto>,
)

private fun buildChapterRanges(chapters: List<SourceChapterDto>, descending: Boolean): List<ChapterRange> {
    val sorted = chapters.sortedBy(SourceChapterDto::index)
    val chunkSize = when {
        sorted.size <= 40 -> sorted.size.coerceAtLeast(1)
        sorted.size <= 120 -> 30
        sorted.size <= 300 -> 50
        else -> 100
    }
    val ranges = sorted.chunked(chunkSize).map { chunk ->
        val first = chunk.first().index
        val last = chunk.last().index
        ChapterRange(
            key = "$first-$last",
            label = if (first == last) "$first" else "$first–$last",
            chapters = if (descending) chunk.asReversed() else chunk,
        )
    }
    return if (descending) ranges.asReversed() else ranges
}

