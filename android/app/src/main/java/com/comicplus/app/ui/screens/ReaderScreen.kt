package com.comicplus.app.ui.screens

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.comicplus.app.ui.AppSettings
import com.comicplus.app.ui.ReaderDirection
import com.comicplus.app.ui.ReaderChapterSegment
import com.comicplus.app.ui.ReaderImageQuality
import com.comicplus.app.ui.ReaderMode
import com.comicplus.app.ui.ReaderPrefetchMode
import com.comicplus.app.ui.readerBrightnessFraction
import com.comicplus.app.data.source.DirectReaderPage
import com.comicplus.app.data.source.SourceChapterDto
import com.comicplus.app.ui.effectiveReaderPrefetchPages
import com.comicplus.app.ui.ReaderUiState
import com.comicplus.app.ui.JmSourceUiState
import com.comicplus.app.ui.JmCommentsUiState
import com.comicplus.app.ui.LocalComicPlusReduceMotion
import com.comicplus.app.ui.theme.White
import com.comicplus.app.ui.components.ShimmerBlock
import com.comicplus.app.ui.components.pressFeedback
import com.comicplus.app.ui.components.rememberShimmerBrush
import com.comicplus.pure.rethrowCancellation
import com.comicplus.pure.runCatchingNonFatal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
import kotlin.math.absoluteValue

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    settings: AppSettings,
    sourceStatus: JmSourceUiState,
    loadPage: suspend (DirectReaderPage, (Float) -> Unit) -> Bitmap,
    prefetchPage: suspend (DirectReaderPage) -> Unit,
    cachedPage: (DirectReaderPage) -> Bitmap?,
    onSelectChapter: (SourceChapterDto) -> Unit,
    loadChapterSegment: suspend (SourceChapterDto) -> ReaderChapterSegment,
    onRetryChapter: () -> Unit,
    onProgressChange: (String, String, Int, Int) -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onRefreshSources: () -> Unit,
    comments: JmCommentsUiState,
    onOpenComments: (String, String) -> Unit,
    onRetryComments: () -> Unit,
    onLoadMoreComments: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state !is ReaderUiState.Idle) ReaderEnvironmentEffects()
    when (state) {
        ReaderUiState.Idle -> Unit
        is ReaderUiState.Loading -> {
            ReaderLoading(state.title, state.chapterTitle, settings.reduceMotion, onClose, modifier)
        }
        is ReaderUiState.Error -> {
            ReaderError(state.title, state.message, onRetryChapter, onClose, modifier)
        }
        is ReaderUiState.Ready -> key(state.chapterId) {
            ReaderContent(
                state = state,
                settings = settings,
                sourceStatus = sourceStatus,
                loadPage = loadPage,
                prefetchPage = prefetchPage,
                cachedPage = cachedPage,
                onSelectChapter = onSelectChapter,
                loadChapterSegment = loadChapterSegment,
                onProgressChange = onProgressChange,
                onSettingsChange = onSettingsChange,
                onRefreshSources = onRefreshSources,
                comments = comments,
                onOpenComments = onOpenComments,
                onRetryComments = onRetryComments,
                onLoadMoreComments = onLoadMoreComments,
                onClose = onClose,
                modifier = modifier,
            )
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun ReaderEnvironmentEffects() {
    val context = LocalContext.current
    val view = LocalView.current
    val window = context.findActivity()?.window
    DisposableEffect(window, view) {
        if (window == null) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        val previousLightStatusBars = controller.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller.isAppearanceLightNavigationBars
        val previousStatusBarColor = window.statusBarColor
        val previousNavigationBarColor = window.navigationBarColor
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.BLACK
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = previousLightStatusBars
            controller.isAppearanceLightNavigationBars = previousLightNavigationBars
            window.statusBarColor = previousStatusBarColor
            window.navigationBarColor = previousNavigationBarColor
        }
    }
}

@Composable
private fun ReaderContent(
    state: ReaderUiState.Ready,
    settings: AppSettings,
    sourceStatus: JmSourceUiState,
    loadPage: suspend (DirectReaderPage, (Float) -> Unit) -> Bitmap,
    prefetchPage: suspend (DirectReaderPage) -> Unit,
    cachedPage: (DirectReaderPage) -> Bitmap?,
    onSelectChapter: (SourceChapterDto) -> Unit,
    loadChapterSegment: suspend (SourceChapterDto) -> ReaderChapterSegment,
    onProgressChange: (String, String, Int, Int) -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onRefreshSources: () -> Unit,
    comments: JmCommentsUiState,
    onOpenComments: (String, String) -> Unit,
    onRetryComments: () -> Unit,
    onLoadMoreComments: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val initialPageIndex = state.initialPageIndex.coerceIn(0, (state.pages.size - 1).coerceAtLeast(0))
    val initialSegment = remember(state.chapterId) {
        ReaderChapterSegment(
            chapterId = state.chapterId,
            chapterTitle = state.chapterTitle,
            chapterIndex = state.currentChapterIndex,
            pages = state.pages,
        )
    }
    val loadedSegments = remember(state.chapterId) { mutableStateListOf(initialSegment) }
    var activePosition by remember(state.chapterId) {
        mutableStateOf(
            ReaderProgressPosition(
                chapterId = state.chapterId,
                chapterTitle = state.chapterTitle,
                chapterIndex = state.currentChapterIndex,
                pageIndex = initialPageIndex,
                pageCount = state.pages.size,
            ),
        )
    }
    val latestProgress = remember(state.chapterId) { AtomicReference(activePosition) }
    val activeSegment = loadedSegments.firstOrNull { it.chapterId == activePosition.chapterId } ?: initialSegment
    val chaptersById = remember(state.chapters) { state.chapters.associateBy(SourceChapterDto::sourceChapterId) }
    val activeReaderState = state.copy(
        chapterId = activeSegment.chapterId,
        chapterTitle = activeSegment.chapterTitle,
        pages = activeSegment.pages,
        currentChapterIndex = activeSegment.chapterIndex,
        initialPageIndex = activePosition.pageIndex,
    )
    val activeChapter = chaptersById[activeSegment.chapterId]
        ?: SourceChapterDto(
            sourceChapterId = activeSegment.chapterId,
            index = activeSegment.chapterIndex + 1,
            title = activeSegment.chapterTitle,
        )
    var chromeVisible by rememberSaveable(state.chapterId) { mutableStateOf(true) }
    var chapterMenuVisible by rememberSaveable { mutableStateOf(false) }
    var readerSettingsVisible by rememberSaveable { mutableStateOf(false) }
    var readerCommentsVisible by rememberSaveable { mutableStateOf(false) }
    var readerCommentChapterId by rememberSaveable(state.chapterId) { mutableStateOf(state.chapterId) }
    val readerCommentChapter = chaptersById[readerCommentChapterId] ?: activeChapter
    val visibleComments = remember(comments, state.sourceId, readerCommentChapter.sourceChapterId) {
        comments.takeIf {
            it.comicId == state.sourceId && it.chapterId == readerCommentChapter.sourceChapterId
        } ?: JmCommentsUiState(
            comicId = state.sourceId,
            chapterId = readerCommentChapter.sourceChapterId,
            loading = true,
        )
    }
    fun openReaderComments(chapter: SourceChapterDto) {
        readerCommentChapterId = chapter.sourceChapterId
        onOpenComments(state.sourceId, chapter.sourceChapterId)
        readerCommentsVisible = true
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPageIndex)
    val pagerState = rememberPagerState(
        initialPage = readingIndexToPagerPage(initialPageIndex, state.pages.size, settings.readerDirection),
        pageCount = { activeSegment.pages.size },
    )
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val latestProgressCallback by rememberUpdatedState(onProgressChange)
    var retryAllKey by remember(state.chapterId) { mutableIntStateOf(0) }
    val failedPages = remember(state.chapterId) { mutableStateMapOf<String, Boolean>() }
    var lastReaderMode by remember(state.chapterId) { mutableStateOf(settings.readerMode) }
    var lastReaderDirection by remember(state.chapterId) { mutableStateOf(settings.readerDirection) }
    var readerPositionInitialized by remember(state.chapterId) { mutableStateOf(false) }
    var savedVerticalIndex by remember(state.chapterId) { mutableIntStateOf(initialPageIndex) }
    var savedVerticalOffset by remember(state.chapterId) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val view = LocalView.current
    val readerWindow = remember(context) { context.findActivity()?.window }
    val memoryClassMb = remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.memoryClass ?: 256
    }
    val skeletonBrush = rememberShimmerBrush(
        animated = !settings.reduceMotion,
        colors = listOf(Color(0xFF101010), Color(0xFF202020), Color(0xFF101010)),
    )
    val readerRequestProfileKey = remember(
        settings.readerImageQuality,
        settings.readerTurboMode,
        settings.autoSelectSource,
        settings.preferredImageHost,
        sourceStatus.selectedImageHost,
    ) {
        listOf(
            settings.readerImageQuality.name,
            settings.readerTurboMode.toString(),
            settings.autoSelectSource.toString(),
            settings.preferredImageHost.orEmpty(),
            sourceStatus.selectedImageHost.orEmpty(),
        ).joinToString("|")
    }
    val selectedImageLatencyMs = remember(sourceStatus.selectedImageHost, sourceStatus.imageItems) {
        sourceStatus.selectedImageHost?.let { host ->
            sourceStatus.imageItems.firstOrNull { it.host == host }?.latencyMs
        }
    }
    val leaveReader = {
        latestProgress.get().let { position ->
            onProgressChange(state.sourceId, position.chapterId, position.pageIndex, position.pageCount)
        }
        onClose()
    }
    val selectChapter: (SourceChapterDto) -> Unit = selectChapter@{ chapter ->
        if (state.changingChapterTitle != null || chapter.sourceChapterId == activePosition.chapterId) return@selectChapter
        latestProgress.get().let { position ->
            onProgressChange(state.sourceId, position.chapterId, position.pageIndex, position.pageCount)
        }
        onSelectChapter(chapter)
    }
    val moveByPage: (Int) -> Unit = { delta ->
        if (settings.readerMode == ReaderMode.Vertical) {
            val target = verticalPagePositionByDelta(
                segments = loadedSegments,
                chapterId = activePosition.chapterId,
                pageIndex = activePosition.pageIndex,
                delta = delta,
            )
            if (target != null && (target.first != activePosition.chapterId || target.second != activePosition.pageIndex)) {
                scope.launch {
                    listState.animateScrollToItem(
                        verticalListIndexForPosition(loadedSegments, target.first, target.second),
                    )
                }
            }
        } else {
            val current = pagerPageToReadingIndex(
                pagerState.currentPage,
                activeSegment.pages.size,
                settings.readerDirection,
            )
            val target = (current + delta).coerceIn(0, (activeSegment.pages.size - 1).coerceAtLeast(0))
            if (target != current) {
                scope.launch {
                    pagerState.animateScrollToPage(
                        readingIndexToPagerPage(target, activeSegment.pages.size, settings.readerDirection),
                    )
                }
            }
        }
    }

    DisposableEffect(view, settings.keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }
    DisposableEffect(readerWindow, settings.readerBrightnessPercent) {
        val previousBrightness = readerWindow?.attributes?.screenBrightness
        if (readerWindow != null) {
            val attributes = readerWindow.attributes
            attributes.screenBrightness = readerBrightnessFraction(settings.readerBrightnessPercent)
                ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            readerWindow.attributes = attributes
        }
        onDispose {
            if (readerWindow != null && previousBrightness != null) {
                val attributes = readerWindow.attributes
                attributes.screenBrightness = previousBrightness
                readerWindow.attributes = attributes
            }
        }
    }
    DisposableEffect(state.chapterId) {
            onDispose {
                latestProgress.get().let { position ->
                    latestProgressCallback(state.sourceId, position.chapterId, position.pageIndex, position.pageCount)
                }
            }
    }

    BackHandler(enabled = chapterMenuVisible || readerSettingsVisible || readerCommentsVisible) {
        when {
            chapterMenuVisible -> chapterMenuVisible = false
            readerSettingsVisible -> readerSettingsVisible = false
            readerCommentsVisible -> readerCommentsVisible = false
        }
    }

    LaunchedEffect(state.chapterId, chapterMenuVisible, readerSettingsVisible, readerCommentsVisible) {
        if (!chapterMenuVisible && !readerSettingsVisible && !readerCommentsVisible) focusRequester.requestFocus()
    }
    LaunchedEffect(settings.readerMode, settings.readerDirection) {
        if (!readerPositionInitialized) {
            readerPositionInitialized = true
            lastReaderMode = settings.readerMode
            lastReaderDirection = settings.readerDirection
            return@LaunchedEffect
        }
        if (settings.readerMode == lastReaderMode && settings.readerDirection == lastReaderDirection) {
            return@LaunchedEffect
        }
        val readingIndex = when (lastReaderMode) {
            ReaderMode.Vertical -> activePosition.pageIndex.also {
                savedVerticalIndex = listState.firstVisibleItemIndex
                savedVerticalOffset = listState.firstVisibleItemScrollOffset
            }
            ReaderMode.Paged -> pagerPageToReadingIndex(
                pagerState.currentPage,
                activeSegment.pages.size,
                lastReaderDirection,
            )
        }.coerceAtLeast(0)
        withFrameNanos { }
        if (settings.readerMode == ReaderMode.Vertical) {
            val targetListIndex = verticalListIndexForPosition(
                loadedSegments,
                activeSegment.chapterId,
                readingIndex.coerceIn(0, activeSegment.pages.lastIndex.coerceAtLeast(0)),
            )
            listState.scrollToItem(
                targetListIndex,
                if (targetListIndex == savedVerticalIndex) savedVerticalOffset else 0,
            )
        } else {
            pagerState.scrollToPage(
                readingIndexToPagerPage(
                    activePosition.pageIndex.coerceIn(0, activeSegment.pages.lastIndex.coerceAtLeast(0)),
                    activeSegment.pages.size,
                    settings.readerDirection,
                ),
            )
        }
        lastReaderMode = settings.readerMode
        lastReaderDirection = settings.readerDirection
    }
    LaunchedEffect(
        state.chapterId,
        loadedSegments.size,
        settings.readerMode,
        settings.readerDirection,
        settings.readerImageQuality,
        settings.readerTurboMode,
        settings.readerPrefetchPages,
        settings.readerPrefetchMode,
        settings.dataSaver,
    ) {
        var previousPosition: ReaderProgressPosition? = null
        var previousPositionAt = 0L
        var smoothedVelocity = 0f
        coroutineScope {
            var progressJob: Job? = null
            var prefetchJob: Job? = null
            snapshotFlow {
                if (settings.readerMode == ReaderMode.Vertical) {
                    activePosition
                } else {
                    ReaderProgressPosition(
                        chapterId = activeSegment.chapterId,
                        chapterTitle = activeSegment.chapterTitle,
                        chapterIndex = activeSegment.chapterIndex,
                        pageIndex = pagerPageToReadingIndex(
                            pagerState.currentPage,
                            activeSegment.pages.size,
                            settings.readerDirection,
                        ).coerceIn(0, (activeSegment.pages.size - 1).coerceAtLeast(0)),
                        pageCount = activeSegment.pages.size,
                    )
                }
            }.distinctUntilChanged().collect { position ->
                val now = SystemClock.elapsedRealtime()
                val previous = previousPosition
                if (previous != null && previous.chapterId == position.chapterId && previous.pageIndex != position.pageIndex) {
                    val elapsedSeconds = ((now - previousPositionAt).coerceAtLeast(120L) / 1000f)
                    val pageDelta = (position.pageIndex - previous.pageIndex).absoluteValue
                    val instantaneous = (pageDelta / elapsedSeconds).coerceIn(0f, 8f)
                    smoothedVelocity = if (smoothedVelocity == 0f) instantaneous else {
                        smoothedVelocity * .58f + instantaneous * .42f
                    }
                }
                previousPosition = position
                previousPositionAt = now
                activePosition = position
                latestProgress.set(position)
                progressJob?.cancel()
                progressJob = launch {
                    delay(READER_PROGRESS_SETTLE_MILLIS)
                    onProgressChange(state.sourceId, position.chapterId, position.pageIndex, position.pageCount)
                }
                prefetchJob?.cancel()
                prefetchJob = launch {
                    val effectivePrefetchPages = effectiveReaderPrefetchPages(
                        configuredPages = settings.readerPrefetchPages,
                        dataSaver = settings.dataSaver,
                        memoryClassMb = memoryClassMb,
                        turboMode = settings.readerTurboMode,
                        mode = settings.readerPrefetchMode,
                        imageQuality = settings.readerImageQuality,
                        pageVelocityPagesPerSecond = smoothedVelocity,
                        networkLatencyMs = selectedImageLatencyMs,
                    )
                    if (effectivePrefetchPages <= 0) return@launch
                    if (settings.readerPrefetchMode != ReaderPrefetchMode.UltraAggressive) {
                        delay(
                            if (settings.readerTurboMode) READER_TURBO_PREFETCH_SETTLE_MILLIS
                            else READER_PREFETCH_SETTLE_MILLIS,
                        )
                    }
                    prefetchReaderPages(
                        position = position,
                        previousPosition = previous,
                        loadedSegments = loadedSegments,
                        initialSegment = initialSegment,
                        settings = settings,
                        memoryClassMb = memoryClassMb,
                        prefetchDistance = effectivePrefetchPages,
                        prefetchPage = prefetchPage,
                        cachedPage = cachedPage,
                    )
                }
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> moveByPage(-1)
                    KeyEvent.KEYCODE_VOLUME_DOWN -> moveByPage(1)
                    KeyEvent.KEYCODE_DPAD_LEFT -> if (settings.readerMode == ReaderMode.Paged) {
                        moveByPage(if (settings.readerDirection == ReaderDirection.LeftToRight) -1 else 1)
                    } else return@onPreviewKeyEvent false
                    KeyEvent.KEYCODE_DPAD_RIGHT -> if (settings.readerMode == ReaderMode.Paged) {
                        moveByPage(if (settings.readerDirection == ReaderDirection.LeftToRight) 1 else -1)
                    } else return@onPreviewKeyEvent false
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .focusable(),
    ) {
        if (settings.readerMode == ReaderMode.Vertical) {
            ContinuousVerticalZoomContainer(listState = listState) { zoomModifier, toggleZoom ->
                VerticalReaderPages(
                    segments = loadedSegments,
                    chapters = state.chapters,
                    listState = listState,
                    settings = settings,
                    sequentialStartPageIndex = initialPageIndex,
                    loadPage = loadPage,
                    prefetchPage = prefetchPage,
                    cachedPage = cachedPage,
                    requestProfileKey = readerRequestProfileKey,
                    retryAllKey = retryAllKey,
                    skeletonBrush = skeletonBrush,
                    modifier = zoomModifier,
                    onDoubleTapZoom = toggleZoom,
                    onToggleChrome = { if (settings.tapToToggleReaderMenu) chromeVisible = !chromeVisible },
                    onOpenComments = ::openReaderComments,
                    onFailureChanged = { page, failed ->
                        val key = page.failureKey()
                        if (failed) failedPages[key] = true else failedPages.remove(key)
                    },
                    loadChapterSegment = loadChapterSegment,
                    onSegmentLoaded = { segment ->
                        if (loadedSegments.none { it.chapterId == segment.chapterId }) loadedSegments += segment
                    },
                    onPositionChanged = { position ->
                        activePosition = position
                        latestProgress.set(position)
                    },
                )
            }
        } else {
            PagedReaderPages(
                pages = activeSegment.pages,
                initialPageIndex = initialPageIndex,
                pagerState = pagerState,
                direction = settings.readerDirection,
                imageQuality = settings.readerImageQuality,
                turboMode = settings.readerTurboMode,
                sequentialLoading = settings.sequentialPageLoading,
                loadPage = loadPage,
                cachedPage = cachedPage,
                requestProfileKey = readerRequestProfileKey,
                beyondViewportPageCount = if (
                    settings.sequentialPageLoading
                ) 0 else if (
                    settings.readerPrefetchMode == ReaderPrefetchMode.UltraAggressive
                ) 2 else 1,
                retryAllKey = retryAllKey,
                skeletonBrush = skeletonBrush,
                onToggleChrome = { if (settings.tapToToggleReaderMenu) chromeVisible = !chromeVisible },
                onFailureChanged = { page, failed ->
                    val key = page.failureKey()
                    if (failed) failedPages[key] = true else failedPages.remove(key)
                },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = if (settings.reduceMotion) fadeIn(tween(80)) else slideInVertically(tween(220)) { -it / 2 } + fadeIn(tween(160)),
            exit = if (settings.reduceMotion) fadeOut(tween(80)) else slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(130)),
        ) {
            ReaderTopBar(
                title = state.title,
                chapterTitle = activePosition.chapterTitle,
                chapterIndex = activePosition.chapterIndex,
                chapterCount = state.chapters.size,
                onClose = leaveReader,
            )
        }
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (settings.reduceMotion) fadeIn(tween(80)) else slideInVertically(tween(240)) { it / 2 } + fadeIn(tween(170)),
            exit = if (settings.reduceMotion) fadeOut(tween(80)) else slideOutVertically(tween(190)) { it / 2 } + fadeOut(tween(130)),
        ) {
            ReaderBottomMenuHost(
                state = activeReaderState,
                settings = settings,
                listState = listState,
                pagerState = pagerState,
                verticalPageIndex = activePosition.pageIndex,
                onVerticalPageSelect = { pageIndex ->
                    scope.launch {
                        listState.scrollToItem(
                            verticalListIndexForPosition(loadedSegments, activeSegment.chapterId, pageIndex),
                        )
                    }
                },
                onPreviousChapter = {
                    state.chapters.getOrNull(activeSegment.chapterIndex - 1)?.let(selectChapter)
                },
                onNextChapter = {
                    state.chapters.getOrNull(activeSegment.chapterIndex + 1)?.let(selectChapter)
                },
                onOpenChapters = { chapterMenuVisible = true },
                onOpenSettings = { readerSettingsVisible = true },
                onOpenComments = { openReaderComments(activeChapter) },
            )
        }
        if (failedPages.isNotEmpty()) {
            ReaderFailureBanner(
                failedCount = failedPages.size,
                onRetryAll = { retryAllKey++ },
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 62.dp),
            )
        }
        state.changingChapterTitle?.let { chapterTitle ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = Color(0xE61A1A1C),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("正在打开 $chapterTitle", color = White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (chapterMenuVisible) {
        ChapterMenuDialog(
            chapters = state.chapters,
            selectedChapterId = activeSegment.chapterId,
            onSelect = {
                chapterMenuVisible = false
                selectChapter(it)
            },
            onDismiss = { chapterMenuVisible = false },
        )
    }
    if (readerSettingsVisible) {
        ReaderSettingsDialog(
            settings = settings,
            sourceStatus = sourceStatus,
            onSettingsChange = onSettingsChange,
            onRefreshSources = onRefreshSources,
            onDismiss = { readerSettingsVisible = false },
        )
    }
    if (readerCommentsVisible) {
        ReaderCommentsDialog(
            chapter = readerCommentChapter,
            state = visibleComments,
            settings = settings,
            onRetry = onRetryComments,
            onLoadMore = onLoadMoreComments,
            onDismiss = { readerCommentsVisible = false },
        )
    }
}

@Composable
private fun ContinuousVerticalZoomContainer(
    listState: androidx.compose.foundation.lazy.LazyListState,
    content: @Composable (Modifier, (Offset) -> Unit) -> Unit,
) {
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffset(value: Float, targetScale: Float): Float {
        if (targetScale <= 1f || viewport == IntSize.Zero) return 0f
        val maxX = viewport.width * (targetScale - 1f) / 2f
        return value.coerceIn(-maxX, maxX)
    }

    val toggleZoom: (Offset) -> Unit = { position ->
        val previousScale = scale
        val nextScale = if (previousScale > 1f) 1f else DOUBLE_TAP_READER_ZOOM
        if (viewport != IntSize.Zero) {
            val centerX = viewport.width / 2f
            offsetX = clampOffset(
                offsetX + (position.x - centerX - offsetX) * (1f - nextScale / previousScale),
                nextScale,
            )
        }
        scale = nextScale
        if (nextScale == 1f) offsetX = 0f
    }

    Box(
        Modifier.fillMaxSize()
            .clipToBounds()
            .onSizeChanged {
                viewport = it
                offsetX = clampOffset(offsetX, scale)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    var hasPressed: Boolean
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressedCount = event.changes.count { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        when {
                            pressedCount >= 2 -> {
                                val previousScale = scale
                                val nextScale = (scale * zoomChange).coerceIn(1f, MAX_READER_ZOOM)
                                val centroid = event.calculateCentroid()
                                val centerX = viewport.width / 2f
                                val centerY = viewport.height / 2f
                                offsetX = clampOffset(
                                    offsetX +
                                        (centroid.x - centerX - offsetX) * (1f - nextScale / previousScale) +
                                        panChange.x,
                                    nextScale,
                                )
                                val verticalDelta = (
                                    (centroid.y - centerY) * (nextScale / previousScale - 1f) - panChange.y
                                    ) / nextScale
                                if (verticalDelta != 0f) listState.dispatchRawDelta(verticalDelta)
                                scale = nextScale
                                if (nextScale == 1f) offsetX = 0f
                                event.changes.forEach { it.consume() }
                            }
                            scale > 1f && panChange != Offset.Zero -> {
                                offsetX = clampOffset(offsetX + panChange.x, scale)
                                if (panChange.y != 0f) listState.dispatchRawDelta(-panChange.y / scale)
                                event.changes.forEach { it.consume() }
                            }
                        }
                        hasPressed = event.changes.any { it.pressed }
                    } while (hasPressed)
                }
            },
    ) {
        content(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
            },
            toggleZoom,
        )
    }
}

private data class ReaderSegmentPosition(
    val chapterTitle: String,
    val chapterIndex: Int,
    val pageCount: Int,
)

private sealed interface ChapterAppendState {
    data object Loading : ChapterAppendState
    data class Failed(val message: String) : ChapterAppendState
}

private fun readerPageItemKey(chapterId: String, pageIndex: Int): String =
    "$READER_PAGE_KEY_PREFIX$chapterId:$pageIndex"

private fun readerPagePosition(
    key: Any?,
    segments: Map<String, ReaderSegmentPosition>,
): ReaderProgressPosition? {
    val raw = key as? String ?: return null
    if (!raw.startsWith(READER_PAGE_KEY_PREFIX)) return null
    val pageSeparator = raw.lastIndexOf(':')
    if (pageSeparator <= READER_PAGE_KEY_PREFIX.length) return null
    val chapterId = raw.substring(READER_PAGE_KEY_PREFIX.length, pageSeparator)
    val pageIndex = raw.substring(pageSeparator + 1).toIntOrNull() ?: return null
    val segment = segments[chapterId] ?: return null
    if (pageIndex !in 0 until segment.pageCount) return null
    return ReaderProgressPosition(
        chapterId = chapterId,
        chapterTitle = segment.chapterTitle,
        chapterIndex = segment.chapterIndex,
        pageIndex = pageIndex,
        pageCount = segment.pageCount,
    )
}

internal fun verticalListIndexForPosition(
    segments: List<ReaderChapterSegment>,
    chapterId: String,
    pageIndex: Int,
): Int {
    var cursor = 0
    segments.forEachIndexed { segmentIndex, segment ->
        if (segment.chapterId == chapterId) {
            return cursor + pageIndex.coerceIn(0, segment.pages.lastIndex.coerceAtLeast(0))
        }
        cursor += segment.pages.size
        // Every loaded chapter before another loaded chapter contributes the
        // chapter-comment row that is inserted between their image pages.
        if (segmentIndex < segments.lastIndex) cursor++
    }
    return 0
}

internal fun verticalPagePositionByDelta(
    segments: List<ReaderChapterSegment>,
    chapterId: String,
    pageIndex: Int,
    delta: Int,
): Pair<String, Int>? {
    if (segments.isEmpty()) return null
    var totalPages = 0
    var currentOrdinal: Int? = null
    segments.forEach { segment ->
        if (segment.chapterId == chapterId && segment.pages.isNotEmpty()) {
            currentOrdinal = totalPages + pageIndex.coerceIn(0, segment.pages.lastIndex)
        }
        totalPages += segment.pages.size
    }
    val current = currentOrdinal ?: return null
    val target = (current + delta).coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    var cursor = 0
    segments.forEach { segment ->
        val nextCursor = cursor + segment.pages.size
        if (target < nextCursor) return segment.chapterId to (target - cursor)
        cursor = nextCursor
    }
    return null
}

@Composable
private fun VerticalReaderPages(
    segments: List<ReaderChapterSegment>,
    chapters: List<SourceChapterDto>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    settings: AppSettings,
    sequentialStartPageIndex: Int,
    loadPage: suspend (DirectReaderPage, (Float) -> Unit) -> Bitmap,
    prefetchPage: suspend (DirectReaderPage) -> Unit,
    cachedPage: (DirectReaderPage) -> Bitmap?,
    requestProfileKey: String,
    retryAllKey: Int,
    skeletonBrush: Brush,
    modifier: Modifier = Modifier,
    onDoubleTapZoom: (Offset) -> Unit,
    onToggleChrome: () -> Unit,
    onOpenComments: (SourceChapterDto) -> Unit,
    onFailureChanged: (DirectReaderPage, Boolean) -> Unit,
    loadChapterSegment: suspend (SourceChapterDto) -> ReaderChapterSegment,
    onSegmentLoaded: (ReaderChapterSegment) -> Unit,
    onPositionChanged: (ReaderProgressPosition) -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val appendStates = remember(segments.firstOrNull()?.chapterId) { mutableStateMapOf<String, ChapterAppendState>() }
    val warmingChapters = remember(segments.firstOrNull()?.chapterId) { mutableStateMapOf<String, Boolean>() }
    val chaptersById = remember(chapters) { chapters.associateBy(SourceChapterDto::sourceChapterId) }
    val segmentSnapshot = remember(segments, segments.size) { segments.toList() }
    val sequentialGate = remember(segments.firstOrNull()?.chapterId) { SequentialPageLoadGate() }
    val sequentialPages = remember(segmentSnapshot, sequentialStartPageIndex) {
        segmentSnapshot.flatMapIndexed { index, segment ->
            if (index == 0) segment.pages.drop(sequentialStartPageIndex.coerceAtLeast(0)) else segment.pages
        }
    }
    val segmentPositions = remember(segmentSnapshot) {
        segmentSnapshot.associate { segment ->
            segment.chapterId to ReaderSegmentPosition(
                chapterTitle = segment.chapterTitle,
                chapterIndex = segment.chapterIndex,
                pageCount = segment.pages.size,
            )
        }
    }
    val foregroundPageKey by remember(segmentPositions) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            layoutInfo.visibleItemsInfo
                .asSequence()
                .filter { info -> readerPagePosition(info.key, segmentPositions) != null }
                .maxByOrNull { info ->
                    val visibleTop = maxOf(info.offset, layoutInfo.viewportStartOffset)
                    val visibleBottom = minOf(info.offset + info.size, layoutInfo.viewportEndOffset)
                    (visibleBottom - visibleTop).coerceAtLeast(0)
                }
                ?.key as? String
        }
    }
    val visiblePageKeys by remember(segmentPositions) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { info ->
                    (info.key as? String)?.takeIf { key -> readerPagePosition(key, segmentPositions) != null }
                }
                .toSet()
        }
    }
    LaunchedEffect(segmentPositions) {
        snapshotFlow { readerPagePosition(foregroundPageKey, segmentPositions) }
            .distinctUntilChanged().collect { position ->
            if (position != null) onPositionChanged(position)
        }
    }

    fun requestNextChapter(chapter: SourceChapterDto) {
        if (warmingChapters[chapter.sourceChapterId] == true ||
            appendStates[chapter.sourceChapterId] is ChapterAppendState.Loading ||
            segments.any { it.chapterId == chapter.sourceChapterId }
        ) return
        appendStates[chapter.sourceChapterId] = ChapterAppendState.Loading
        scope.launch {
            runCatchingNonFatal { loadChapterSegment(chapter) }
                .onSuccess { segment ->
                    // Append the chapter as soon as its page list is ready.
                    // Image warming is speculative and must never hold the
                    // chapter boundary on a loading row.
                    onSegmentLoaded(segment)
                    appendStates.remove(chapter.sourceChapterId)
                    warmingChapters[chapter.sourceChapterId] = true
                    val warmPages = segment.pages.take(
                        when {
                            settings.readerPrefetchMode == ReaderPrefetchMode.UltraAggressive ->
                                ULTRA_NEXT_CHAPTER_WARM_PAGES
                            settings.readerTurboMode -> 2
                            else -> 1
                        },
                    )
                    suspend fun warm(page: DirectReaderPage) {
                        try {
                            prefetchPage(page)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            // The visible page owns retry UI if speculative warming fails.
                        }
                    }
                    if (
                        !settings.sequentialPageLoading && (
                            settings.readerTurboMode ||
                                settings.readerPrefetchMode == ReaderPrefetchMode.UltraAggressive
                            )
                    ) {
                        warmPages.chunked(2).forEach { batch ->
                            coroutineScope { batch.map { page -> async { warm(page) } }.awaitAll() }
                        }
                    } else if (!settings.sequentialPageLoading) {
                        warmPages.forEach { page -> warm(page) }
                    }
                    warmingChapters.remove(chapter.sourceChapterId)
                }
                .onFailure { error ->
                    error.rethrowCancellation()
                    warmingChapters.remove(chapter.sourceChapterId)
                    if (segments.any { it.chapterId == chapter.sourceChapterId }) return@onFailure
                    appendStates[chapter.sourceChapterId] = ChapterAppendState.Failed(
                        error.message?.take(100).orEmpty().ifBlank { "线路暂时不可用" },
                    )
                }
        }
    }

    LaunchedEffect(
        segmentPositions,
        settings.readerTurboMode,
        settings.readerPrefetchPages,
        settings.readerPrefetchMode,
        settings.dataSaver,
        settings.sequentialPageLoading,
    ) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { info -> readerPagePosition(info.key, segmentPositions) }
                .maxWithOrNull(compareBy<ReaderProgressPosition>({ it.chapterIndex }, { it.pageIndex }))
        }.distinctUntilChanged().collect { position ->
            if (position == null) return@collect
            if (settings.sequentialPageLoading) {
                val segment = segmentSnapshot.firstOrNull { it.chapterId == position.chapterId }
                val lastPage = segment?.pages?.lastOrNull()
                if (lastPage != null && position.pageIndex >= lastPage.index - 1) {
                    sequentialGate.awaitAvailable(lastPage)
                    if (isActive) chapters.getOrNull(position.chapterIndex + 1)?.let(::requestNextChapter)
                }
                return@collect
            }
            val preloadDistance = when {
                settings.dataSaver || settings.readerPrefetchMode == ReaderPrefetchMode.Conservative ->
                    NEXT_CHAPTER_MIN_PRELOAD_PAGES
                settings.readerPrefetchMode == ReaderPrefetchMode.UltraAggressive ->
                    NEXT_CHAPTER_ULTRA_PRELOAD_PAGES
                settings.readerTurboMode -> NEXT_CHAPTER_TURBO_PRELOAD_PAGES
                settings.readerPrefetchMode == ReaderPrefetchMode.Aggressive ->
                    NEXT_CHAPTER_MAX_PRELOAD_PAGES
                else -> settings.readerPrefetchPages.coerceIn(
                    NEXT_CHAPTER_MIN_PRELOAD_PAGES,
                    NEXT_CHAPTER_MAX_PRELOAD_PAGES,
                )
            }
            if (shouldPreloadNextChapter(position.pageIndex, position.pageCount, preloadDistance)) {
                chapters.getOrNull(position.chapterIndex + 1)?.let(::requestNextChapter)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(settings.readerPageSpacingDp.dp),
    ) {
        segments.forEachIndexed { segmentIndex, segment ->
            itemsIndexed(
                items = segment.pages,
                key = { pageIndex, _ -> readerPageItemKey(segment.chapterId, pageIndex) },
                contentType = { _, _ -> "reader-page" },
            ) { pageIndex, page ->
                val pageKey = readerPageItemKey(segment.chapterId, pageIndex)
                ReaderPage(
                    page = page,
                    loadPage = loadPage,
                    cachedPage = cachedPage,
                    imageQuality = settings.readerImageQuality,
                    turboMode = settings.readerTurboMode,
                    requestProfileKey = requestProfileKey,
                    retryAllKey = retryAllKey,
                    skeletonBrush = skeletonBrush,
                    paged = false,
                    localZoomEnabled = false,
                    onSharedDoubleTap = onDoubleTapZoom,
                    // Every page already in the viewport is user-visible work. The most
                    // prominent page remains the progress anchor, but adjacent visible pages
                    // must not be demoted to background decoding and cause a blank on fling.
                    foreground = if (settings.sequentialPageLoading) {
                        pageKey == foregroundPageKey
                    } else {
                        pageKey == foregroundPageKey || pageKey in visiblePageKeys
                    },
                    onTapFraction = { onToggleChrome() },
                    onFailureChanged = { onFailureChanged(page, it) },
                    sequentialLoading = settings.sequentialPageLoading,
                    sequentialGate = sequentialGate,
                    sequentialPages = sequentialPages,
                )
            }
            val currentChapter = chaptersById[segment.chapterId]
                ?: SourceChapterDto(
                    sourceChapterId = segment.chapterId,
                    index = segment.chapterIndex + 1,
                    title = segment.chapterTitle,
            )
            val followingChapter = chapters.getOrNull(segment.chapterIndex + 1)
            if (followingChapter != null) {
                item(key = "chapter-comments:${segment.chapterId}") {
                    ChapterCommentEntry(
                        chapter = currentChapter,
                        onOpenComments = { onOpenComments(currentChapter) },
                    )
                }
            }
            if (followingChapter != null && segments.none { it.chapterId == followingChapter.sourceChapterId }) {
                item(key = "reader-transition:${segment.chapterId}:${followingChapter.sourceChapterId}") {
                    val appendState = appendStates[followingChapter.sourceChapterId]
                    ChapterTransitionItem(
                        chapter = followingChapter,
                        loading = appendState is ChapterAppendState.Loading,
                        error = (appendState as? ChapterAppendState.Failed)?.message,
                        onRetry = { requestNextChapter(followingChapter) },
                    )
                }
            } else if (segmentIndex == segments.lastIndex) {
                item(key = "chapter-end:${segment.chapterId}") {
                    ChapterEnd(
                        chapter = currentChapter,
                        onOpenComments = { onOpenComments(currentChapter) },
                        hasNext = false,
                        onNext = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterTransitionItem(
    chapter: SourceChapterDto,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "下一话 · ${chapter.title}",
            color = White.copy(alpha = .72f),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        when {
            loading -> CircularProgressIndicator(Modifier.size(13.dp), color = White.copy(alpha = .55f), strokeWidth = 1.5.dp)
            error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("接续失败", color = Color(0xFFE57373), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onRetry) { Text("重试", color = White) }
            }
            else -> Text("继续阅读", color = White.copy(alpha = .34f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ChapterCommentEntry(
    chapter: SourceChapterDto,
    onOpenComments: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clickable(onClick = onOpenComments),
        color = Color.White.copy(alpha = .07f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = White.copy(alpha = .78f),
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("查看本章评论", color = White, style = MaterialTheme.typography.labelLarge)
                Text(
                    chapter.title,
                    color = White.copy(alpha = .48f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = "打开本章评论",
                tint = White.copy(alpha = .58f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PagedReaderPages(
    pages: List<DirectReaderPage>,
    initialPageIndex: Int,
    pagerState: PagerState,
    direction: ReaderDirection,
    imageQuality: ReaderImageQuality,
    turboMode: Boolean,
    sequentialLoading: Boolean,
    loadPage: suspend (DirectReaderPage, (Float) -> Unit) -> Bitmap,
    cachedPage: (DirectReaderPage) -> Bitmap?,
    requestProfileKey: String,
    beyondViewportPageCount: Int,
    retryAllKey: Int,
    skeletonBrush: Brush,
    onToggleChrome: () -> Unit,
    onFailureChanged: (DirectReaderPage, Boolean) -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val sequentialPages = remember(pages, initialPageIndex) {
        pages.drop(initialPageIndex.coerceIn(0, pages.size))
    }
    val sequentialGate = remember(sequentialPages) { SequentialPageLoadGate() }
    val leftEdgeDelta = if (direction == ReaderDirection.LeftToRight) -1 else 1
    fun movePage(delta: Int) {
        val currentIndex = pagerPageToReadingIndex(pagerState.currentPage, pages.size, direction)
        val targetIndex = (currentIndex + delta).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        if (targetIndex == currentIndex) return
        scope.launch {
            pagerState.animateScrollToPage(readingIndexToPagerPage(targetIndex, pages.size, direction))
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = beyondViewportPageCount.coerceIn(0, 6),
        key = { it },
    ) { pagerPage ->
        val readingIndex = pagerPageToReadingIndex(pagerPage, pages.size, direction)
        val page = pages[readingIndex]
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ReaderPage(
                page = page,
                loadPage = loadPage,
                cachedPage = cachedPage,
                imageQuality = imageQuality,
                turboMode = turboMode,
                requestProfileKey = requestProfileKey,
                retryAllKey = retryAllKey,
                skeletonBrush = skeletonBrush,
                paged = true,
                foreground = pagerPage == pagerState.currentPage,
                onTapFraction = { fraction ->
                    when {
                        fraction <= READER_EDGE_TAP_FRACTION -> movePage(leftEdgeDelta)
                        fraction >= 1f - READER_EDGE_TAP_FRACTION -> movePage(-leftEdgeDelta)
                        else -> onToggleChrome()
                    }
                },
                onFailureChanged = { onFailureChanged(page, it) },
                sequentialLoading = sequentialLoading,
                sequentialGate = sequentialGate,
                sequentialPages = sequentialPages,
            )
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    chapterTitle: String,
    chapterIndex: Int,
    chapterCount: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = .78f))
            .statusBarsPadding()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回详情", tint = White)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                chapterTitle,
                color = White.copy(alpha = .6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            "${chapterIndex + 1}/$chapterCount",
            modifier = Modifier.padding(end = 12.dp),
            color = White.copy(alpha = .52f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ReaderBottomMenuHost(
    state: ReaderUiState.Ready,
    settings: AppSettings,
    listState: androidx.compose.foundation.lazy.LazyListState,
    pagerState: PagerState,
    verticalPageIndex: Int,
    onVerticalPageSelect: (Int) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenComments: () -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val currentPageIndex = if (settings.readerMode == ReaderMode.Vertical) {
        verticalPageIndex
    } else {
        pagerPageToReadingIndex(pagerState.currentPage, state.pages.size, settings.readerDirection)
    }.coerceIn(0, (state.pages.size - 1).coerceAtLeast(0))
    var sliderValue by remember(state.chapterId) { mutableFloatStateOf(currentPageIndex.toFloat()) }
    var sliderDragging by remember { mutableStateOf(false) }
    LaunchedEffect(currentPageIndex, sliderDragging) {
        if (!sliderDragging) sliderValue = currentPageIndex.toFloat()
    }
    ReaderBottomMenu(
        state = state,
        currentPageIndex = currentPageIndex,
        sliderValue = sliderValue,
        sliderDragging = sliderDragging,
        onSliderDraggingChange = { sliderDragging = it },
        onSliderValueChange = { sliderValue = it },
        onSliderFinished = {
            sliderDragging = false
            val index = sliderValue.roundToInt().coerceIn(0, (state.pages.size - 1).coerceAtLeast(0))
            scope.launch {
                if (settings.readerMode == ReaderMode.Vertical) {
                    onVerticalPageSelect(index)
                } else {
                    pagerState.scrollToPage(readingIndexToPagerPage(index, state.pages.size, settings.readerDirection))
                }
            }
        },
        onPreviousChapter = onPreviousChapter,
        onNextChapter = onNextChapter,
        onOpenChapters = onOpenChapters,
        onOpenSettings = onOpenSettings,
        onOpenComments = onOpenComments,
    )
}

@Composable
private fun ReaderBottomMenu(
    state: ReaderUiState.Ready,
    currentPageIndex: Int,
    sliderValue: Float,
    sliderDragging: Boolean,
    onSliderDraggingChange: (Boolean) -> Unit,
    onSliderValueChange: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenComments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = .84f),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (sliderDragging) "跳转至第 ${sliderValue.roundToInt() + 1} 页" else state.chapterTitle,
                    modifier = Modifier.weight(1f),
                    color = White.copy(alpha = .76f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${currentPageIndex + 1} / ${state.pages.size}",
                    color = White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Slider(
                value = sliderValue.coerceIn(0f, (state.pages.size - 1).coerceAtLeast(1).toFloat()),
                onValueChange = {
                    onSliderDraggingChange(true)
                    onSliderValueChange(it)
                },
                onValueChangeFinished = onSliderFinished,
                enabled = state.pages.size > 1,
                valueRange = 0f..(state.pages.size - 1).coerceAtLeast(1).toFloat(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReaderToolButton(
                    label = "上一话",
                    icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    enabled = state.currentChapterIndex > 0,
                    onClick = onPreviousChapter,
                    modifier = Modifier.weight(1f),
                )
                ReaderToolButton(
                    label = "目录",
                    icon = Icons.Outlined.FormatListNumbered,
                    onClick = onOpenChapters,
                    modifier = Modifier.weight(1f),
                )
                ReaderToolButton(
                    label = "评论",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    onClick = onOpenComments,
                    modifier = Modifier.weight(1f),
                )
                ReaderToolButton(
                    label = "设置",
                    icon = Icons.Outlined.Tune,
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                )
                ReaderToolButton(
                    label = "下一话",
                    icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    enabled = state.currentChapterIndex < state.chapters.lastIndex,
                    onClick = onNextChapter,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReaderToolButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .pressFeedback(interactionSource, pressedScale = .92f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = White.copy(alpha = if (enabled) .86f else .25f), modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = White.copy(alpha = if (enabled) .62f else .22f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReaderPage(
    page: DirectReaderPage,
    loadPage: suspend (DirectReaderPage, (Float) -> Unit) -> Bitmap,
    cachedPage: (DirectReaderPage) -> Bitmap?,
    imageQuality: ReaderImageQuality,
    turboMode: Boolean,
    requestProfileKey: String,
    retryAllKey: Int,
    skeletonBrush: Brush,
    paged: Boolean,
    localZoomEnabled: Boolean = true,
    onSharedDoubleTap: ((Offset) -> Unit)? = null,
    foreground: Boolean = true,
    onTapFraction: (Float) -> Unit,
    onFailureChanged: (Boolean) -> Unit,
    sequentialLoading: Boolean = false,
    sequentialGate: SequentialPageLoadGate? = null,
    sequentialPages: List<DirectReaderPage> = emptyList(),
) {
    var retryKey by rememberSaveable(page.photoId, page.fileName) { mutableIntStateOf(0) }
    var pageAspectRatio by remember(page.photoId, page.fileName) {
        mutableFloatStateOf(DEFAULT_READER_PAGE_ASPECT_RATIO)
    }
    val cachedResult = remember(page, requestProfileKey, foreground, retryKey, retryAllKey) {
        cachedPage(page)?.let(Result.Companion::success)
    }
    val result by produceState<Result<Bitmap>?>(
        initialValue = cachedResult,
        page.url,
        page.scrambleId,
        retryKey,
        retryAllKey,
        foreground,
        imageQuality,
        turboMode,
        requestProfileKey,
    ) {
        value = cachedResult
        if (!foreground) return@produceState
        value = try {
            if (sequentialLoading && sequentialGate != null) {
                Result.success(
                    sequentialGate.load(
                        sequence = sequentialPages,
                        target = page,
                        cachedPage = cachedPage,
                    ) { candidate, isTarget ->
                        loadPage(candidate) { ratio ->
                            if (isTarget) pageAspectRatio = ratio.coerceIn(0.05f, 8f)
                        }
                    },
                )
            } else if (value != null) {
                value!!
            } else {
                Result.success(
                    loadPage(page) { ratio -> pageAspectRatio = ratio.coerceIn(0.05f, 8f) },
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
    LaunchedEffect(result) {
        onFailureChanged(result?.isFailure == true)
    }
    DisposableEffect(page.failureKey()) {
        onDispose { onFailureChanged(false) }
    }
    val bitmap = result?.getOrNull()
    LaunchedEffect(bitmap) {
        if (bitmap != null) {
            pageAspectRatio = (bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f))
                .coerceIn(0.05f, 8f)
        }
    }
    val verticalModifier = Modifier.fillMaxWidth()
    when {
        bitmap != null -> {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
            ZoomableReaderImage(
                bitmap = bitmap,
                contentDescription = "第 ${page.index} 页",
                modifier = if (paged) Modifier.fillMaxSize() else verticalModifier.aspectRatio(ratio),
                contentScale = if (paged) ContentScale.Fit else ContentScale.FillWidth,
                zoomEnabled = localZoomEnabled,
                onSharedDoubleTap = onSharedDoubleTap,
                onTapFraction = onTapFraction,
            )
        }
        result?.isFailure == true -> {
            Column(
                modifier = (if (paged) Modifier.fillMaxSize() else verticalModifier.aspectRatio(pageAspectRatio))
                    .background(Color(0xFF101010)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, tint = White.copy(alpha = .48f))
                Spacer(Modifier.height(8.dp))
                Text("第 ${page.index} 页加载失败", color = White.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("点击失败页重试", color = White.copy(alpha = .42f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { retryKey++ },
                    colors = ButtonDefaults.buttonColors(containerColor = White.copy(alpha = .12f), contentColor = White),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("重试本页")
                }
            }
        }
        else -> {
            Box(
                modifier = (if (paged) Modifier.fillMaxSize() else verticalModifier.aspectRatio(pageAspectRatio))
                    .background(Color(0xFF101010))
                    .pointerInput(onTapFraction) {
                        detectTapGestures { position ->
                            onTapFraction((position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                ReaderPageSkeleton(skeletonBrush)
                Text(
                    "正在加载第 ${page.index} 页",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                    color = White.copy(alpha = .42f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ReaderPageSkeleton(brush: Brush) {
    ShimmerBlock(
        brush = brush,
        modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
        shape = RoundedCornerShape(0.dp),
    )
}

@Composable
private fun ZoomableReaderImage(
    bitmap: Bitmap,
    contentDescription: String,
    modifier: Modifier,
    contentScale: ContentScale,
    zoomEnabled: Boolean = true,
    onSharedDoubleTap: ((Offset) -> Unit)? = null,
    onTapFraction: (Float) -> Unit,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var viewport by remember(bitmap) { mutableStateOf(IntSize.Zero) }

    fun clampOffset(value: Offset, targetScale: Float): Offset {
        if (targetScale <= 1f || viewport == IntSize.Zero) return Offset.Zero
        val maxX = viewport.width * (targetScale - 1f) / 2f
        val maxY = viewport.height * (targetScale - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    Box(modifier = modifier.clipToBounds().onSizeChanged { viewport = it }) {
        Image(
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .pointerInput(bitmap, zoomEnabled) {
                    if (!zoomEnabled) return@pointerInput
                    awaitEachGesture {
                        var hasPressed: Boolean
                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val transforming = pressedCount >= 2 || (scale > 1f && panChange != Offset.Zero)
                            if (transforming) {
                                val previousScale = scale
                                val nextScale = (scale * zoomChange).coerceIn(1f, MAX_READER_ZOOM)
                                val centroid = event.calculateCentroid()
                                val center = Offset(viewport.width / 2f, viewport.height / 2f)
                                offset = clampOffset(
                                    offset +
                                        (centroid - center - offset) * (1f - nextScale / previousScale) +
                                        panChange,
                                    nextScale,
                                )
                                scale = nextScale
                                if (nextScale == 1f) offset = Offset.Zero
                                event.changes.forEach { it.consume() }
                            }
                            hasPressed = event.changes.any { it.pressed }
                        } while (hasPressed)
                    }
                }
                .pointerInput(bitmap, onTapFraction) {
                    detectTapGestures(
                        onDoubleTap = { position ->
                            if (!zoomEnabled) {
                                onSharedDoubleTap?.invoke(position)
                            } else if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = DOUBLE_TAP_READER_ZOOM
                                val centerOffset = Offset(
                                    x = (viewport.width / 2f - position.x) * (scale - 1f),
                                    y = (viewport.height / 2f - position.y) * (scale - 1f),
                                )
                                offset = clampOffset(centerOffset, scale)
                            }
                        },
                        onTap = { position ->
                            onTapFraction((position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f))
                        },
                    )
                },
        )
    }
}

@Composable
private fun ReaderFailureBanner(failedCount: Int, onRetryAll: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onRetryAll),
        color = Color(0xE6262628),
        shape = CircleShape,
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, tint = White, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text("$failedCount 页加载失败 · 同时重试", color = White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ChapterEnd(
    chapter: SourceChapterDto,
    onOpenComments: () -> Unit,
    hasNext: Boolean,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 42.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("本章阅读完毕", color = White.copy(alpha = .7f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Text(chapter.title, color = White.copy(alpha = .42f), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        Text("Comic Plus · 永久免费", color = White.copy(alpha = .38f), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(13.dp))
        TextButton(onClick = onOpenComments) {
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(17.dp), tint = White)
            Spacer(Modifier.width(6.dp))
            Text("查看本章评论", color = White)
        }
        if (hasNext) {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Color.Black),
                shape = RoundedCornerShape(13.dp),
            ) {
                Text("阅读下一话")
            }
        }
    }
}

@Composable
private fun ReaderLoading(title: String, chapterTitle: String, reduceMotion: Boolean, onClose: () -> Unit, modifier: Modifier) {
    val brush = rememberShimmerBrush(
        animated = !reduceMotion,
        colors = listOf(Color(0xFF101010), Color(0xFF202020), Color(0xFF101010)),
    )
    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ShimmerBlock(brush, Modifier.fillMaxWidth().height(430.dp), RoundedCornerShape(2.dp))
            Spacer(Modifier.height(16.dp))
            Text("正在准备 $title", color = White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(chapterTitle.ifBlank { "正在获取章节图片" }, color = White.copy(alpha = .52f), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(6.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回详情", tint = White)
        }
    }
}

private fun DirectReaderPage.failureKey(): String = "$photoId-$fileName"

private const val READER_PREFETCH_SETTLE_MILLIS = 140L
private const val READER_TURBO_PREFETCH_SETTLE_MILLIS = 24L
private const val READER_PROGRESS_SETTLE_MILLIS = 500L
private const val READER_PAGE_KEY_PREFIX = "reader-page:"
private const val NEXT_CHAPTER_MIN_PRELOAD_PAGES = 4
private const val NEXT_CHAPTER_MAX_PRELOAD_PAGES = 6
private const val NEXT_CHAPTER_TURBO_PRELOAD_PAGES = 8
private const val NEXT_CHAPTER_ULTRA_PRELOAD_PAGES = 16
private const val ULTRA_NEXT_CHAPTER_WARM_PAGES = 6
private const val READER_EDGE_TAP_FRACTION = .28f
private const val DEFAULT_READER_PAGE_ASPECT_RATIO = .70f
private const val MAX_READER_ZOOM = 4f
private const val DOUBLE_TAP_READER_ZOOM = 2.5f

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val base = current.baseContext
        if (base === current) return null
        current = base
    }
    return current as? Activity
}

@Composable
private fun ReaderError(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Text(message, color = White.copy(alpha = .58f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = White.copy(alpha = .12f), contentColor = White),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("返回详情")
                }
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("重试章节")
                }
            }
        }
    }
}

