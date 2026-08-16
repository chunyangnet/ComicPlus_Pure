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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.comicplus.app.ui.JmSourceUiItem
import com.comicplus.app.ui.LocalComicPlusReduceMotion
import com.comicplus.app.ui.theme.White
import com.comicplus.app.ui.components.ShimmerBlock
import com.comicplus.app.ui.components.pressFeedback
import com.comicplus.app.ui.components.rememberShimmerBrush
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
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
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state !is ReaderUiState.Idle) ReaderEnvironmentEffects()
    when (state) {
        ReaderUiState.Idle -> Unit
        is ReaderUiState.Loading -> {
            BackHandler(onBack = onClose)
            ReaderLoading(state.title, state.chapterTitle, settings.reduceMotion, onClose, modifier)
        }
        is ReaderUiState.Error -> {
            BackHandler(onBack = onClose)
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
    val activeReaderState = state.copy(
        chapterId = activeSegment.chapterId,
        chapterTitle = activeSegment.chapterTitle,
        pages = activeSegment.pages,
        currentChapterIndex = activeSegment.chapterIndex,
        initialPageIndex = activePosition.pageIndex,
    )
    var chromeVisible by rememberSaveable(state.chapterId) { mutableStateOf(true) }
    var chapterMenuVisible by rememberSaveable { mutableStateOf(false) }
    var readerSettingsVisible by rememberSaveable { mutableStateOf(false) }
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
    val view = LocalView.current
    val readerWindow = LocalContext.current.findActivity()?.window
    val activityManager = LocalContext.current.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
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
        val current = if (settings.readerMode == ReaderMode.Vertical) {
            listState.firstVisibleItemIndex
        } else {
            pagerPageToReadingIndex(pagerState.currentPage, activeSegment.pages.size, settings.readerDirection)
        }
        val targetLimit = if (settings.readerMode == ReaderMode.Vertical) {
            (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
        } else {
            (activeSegment.pages.size - 1).coerceAtLeast(0)
        }
        val target = (current + delta).coerceIn(0, targetLimit)
        if (target != current) {
            scope.launch {
                if (settings.readerMode == ReaderMode.Vertical) {
                    listState.animateScrollToItem(target)
                } else {
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

    BackHandler {
        when {
            chapterMenuVisible -> chapterMenuVisible = false
            readerSettingsVisible -> readerSettingsVisible = false
            else -> leaveReader()
        }
    }

    LaunchedEffect(state.chapterId, chapterMenuVisible, readerSettingsVisible) {
        if (!chapterMenuVisible && !readerSettingsVisible) focusRequester.requestFocus()
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
        }.distinctUntilChanged().collectLatest { position ->
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
            val effectivePrefetchPages = effectiveReaderPrefetchPages(
                configuredPages = settings.readerPrefetchPages,
                dataSaver = settings.dataSaver,
                memoryClassMb = activityManager?.memoryClass ?: 256,
                turboMode = settings.readerTurboMode,
                mode = settings.readerPrefetchMode,
                pageVelocityPagesPerSecond = smoothedVelocity,
                networkLatencyMs = selectedImageLatencyMs,
            )
            if (effectivePrefetchPages <= 0) return@collectLatest
            delay(if (settings.readerTurboMode) READER_TURBO_PREFETCH_SETTLE_MILLIS else READER_PREFETCH_SETTLE_MILLIS)
            val segment = loadedSegments.firstOrNull { it.chapterId == position.chapterId } ?: initialSegment
            val movementDirection = previous
                ?.takeIf { it.chapterId == position.chapterId }
                ?.let { (position.pageIndex - it.pageIndex).coerceIn(-1, 1) }
                ?.takeUnless { it == 0 }
                ?: 1
            val indices = readerPrefetchPlan(
                currentPageIndex = position.pageIndex,
                pageCount = segment.pages.size,
                distance = effectivePrefetchPages,
                direction = movementDirection,
                includeOpposite = settings.readerMode == ReaderMode.Paged,
            )
            suspend fun prefetch(page: DirectReaderPage) {
                try {
                    prefetchPage(page)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Visible page loading owns error UI; speculative prefetch failures stay silent.
                }
            }
            val pages = indices.map(segment.pages::get).filter { cachedPage(it) == null }
            val parallel = settings.readerTurboMode || (
                settings.readerPrefetchMode == ReaderPrefetchMode.Aggressive &&
                    (activityManager?.memoryClass ?: 256) >= 512
                )
            if (parallel) {
                pages.chunked(2).forEach { batch ->
                    coroutineScope { batch.map { page -> async { prefetch(page) } }.awaitAll() }
                }
            } else {
                pages.forEach { page -> prefetch(page) }
            }
        }
    }
    LaunchedEffect(state.chapterId, loadedSegments.size, settings.readerMode, settings.readerDirection) {
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
        }.distinctUntilChanged().collectLatest { position ->
            activePosition = position
            latestProgress.set(position)
            delay(READER_PROGRESS_SETTLE_MILLIS)
            onProgressChange(state.sourceId, position.chapterId, position.pageIndex, position.pageCount)
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
                    loadPage = loadPage,
                    prefetchPage = prefetchPage,
                    cachedPage = cachedPage,
                    requestProfileKey = readerRequestProfileKey,
                    retryAllKey = retryAllKey,
                    skeletonBrush = skeletonBrush,
                    modifier = zoomModifier,
                    onDoubleTapZoom = toggleZoom,
                    onToggleChrome = { if (settings.tapToToggleReaderMenu) chromeVisible = !chromeVisible },
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
                pagerState = pagerState,
                direction = settings.readerDirection,
                reduceMotion = settings.reduceMotion,
                imageQuality = settings.readerImageQuality,
                turboMode = settings.readerTurboMode,
                loadPage = loadPage,
                cachedPage = cachedPage,
                requestProfileKey = readerRequestProfileKey,
                beyondViewportPageCount = 1,
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

private data class ReaderProgressPosition(
    val chapterId: String,
    val chapterTitle: String,
    val chapterIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
)

private sealed interface ChapterAppendState {
    data object Loading : ChapterAppendState
    data class Failed(val message: String) : ChapterAppendState
}

private fun readerPageItemKey(chapterId: String, pageIndex: Int): String =
    "reader-page:$chapterId:$pageIndex"

private fun verticalListIndexForPosition(
    segments: List<ReaderChapterSegment>,
    chapterId: String,
    pageIndex: Int,
): Int {
    var cursor = 0
    segments.forEach { segment ->
        if (segment.chapterId == chapterId) {
            return cursor + pageIndex.coerceIn(0, segment.pages.lastIndex.coerceAtLeast(0))
        }
        cursor += segment.pages.size
    }
    return 0
}

@Composable
private fun VerticalReaderPages(
    segments: List<ReaderChapterSegment>,
    chapters: List<SourceChapterDto>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    settings: AppSettings,
    loadPage: suspend (DirectReaderPage, (Float) -> Unit) -> Bitmap,
    prefetchPage: suspend (DirectReaderPage) -> Unit,
    cachedPage: (DirectReaderPage) -> Bitmap?,
    requestProfileKey: String,
    retryAllKey: Int,
    skeletonBrush: Brush,
    modifier: Modifier = Modifier,
    onDoubleTapZoom: (Offset) -> Unit,
    onToggleChrome: () -> Unit,
    onFailureChanged: (DirectReaderPage, Boolean) -> Unit,
    loadChapterSegment: suspend (SourceChapterDto) -> ReaderChapterSegment,
    onSegmentLoaded: (ReaderChapterSegment) -> Unit,
    onPositionChanged: (ReaderProgressPosition) -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val appendStates = remember(segments.firstOrNull()?.chapterId) { mutableStateMapOf<String, ChapterAppendState>() }
    val warmingChapters = remember(segments.firstOrNull()?.chapterId) { mutableStateMapOf<String, Boolean>() }
    val pagePositions = remember(segments.map { it.chapterId to it.pages.size }) {
        buildMap {
            segments.forEach { segment ->
                segment.pages.indices.forEach { pageIndex ->
                    put(
                        readerPageItemKey(segment.chapterId, pageIndex),
                        ReaderProgressPosition(
                            segment.chapterId,
                            segment.chapterTitle,
                            segment.chapterIndex,
                            pageIndex,
                            segment.pages.size,
                        ),
                    )
                }
            }
        }
    }
    val foregroundPageKey by remember(pagePositions) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            layoutInfo.visibleItemsInfo
                .asSequence()
                .filter { info -> pagePositions.containsKey(info.key as? String) }
                .maxByOrNull { info ->
                    val visibleTop = maxOf(info.offset, layoutInfo.viewportStartOffset)
                    val visibleBottom = minOf(info.offset + info.size, layoutInfo.viewportEndOffset)
                    (visibleBottom - visibleTop).coerceAtLeast(0)
                }
                ?.key as? String
        }
    }
    val visiblePageKeys by remember(pagePositions) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { info -> info.key as? String }
                .filter(pagePositions::containsKey)
                .toSet()
        }
    }
    LaunchedEffect(pagePositions) {
        snapshotFlow { foregroundPageKey?.let(pagePositions::get) }
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
            runCatching { loadChapterSegment(chapter) }
                .onSuccess { segment ->
                    // Append the chapter as soon as its page list is ready.
                    // Image warming is speculative and must never hold the
                    // chapter boundary on a loading row.
                    onSegmentLoaded(segment)
                    appendStates.remove(chapter.sourceChapterId)
                    warmingChapters[chapter.sourceChapterId] = true
                    val warmPages = segment.pages.take(if (settings.readerTurboMode) 2 else 1)
                    suspend fun warm(page: DirectReaderPage) {
                        try {
                            prefetchPage(page)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            // The visible page owns retry UI if speculative warming fails.
                        }
                    }
                    if (settings.readerTurboMode) {
                        coroutineScope { warmPages.map { page -> async { warm(page) } }.awaitAll() }
                    } else {
                        warmPages.forEach { page -> warm(page) }
                    }
                    warmingChapters.remove(chapter.sourceChapterId)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    warmingChapters.remove(chapter.sourceChapterId)
                    if (segments.any { it.chapterId == chapter.sourceChapterId }) return@onFailure
                    appendStates[chapter.sourceChapterId] = ChapterAppendState.Failed(
                        error.message?.take(100).orEmpty().ifBlank { "线路暂时不可用" },
                    )
                }
        }
    }

    LaunchedEffect(
        pagePositions,
        settings.readerTurboMode,
        settings.readerPrefetchPages,
        settings.readerPrefetchMode,
        settings.dataSaver,
    ) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { info -> pagePositions[info.key as? String] }
                .maxWithOrNull(compareBy<ReaderProgressPosition>({ it.chapterIndex }, { it.pageIndex }))
        }.distinctUntilChanged().collect { position ->
            if (position == null) return@collect
            val preloadDistance = if (settings.readerTurboMode) {
                NEXT_CHAPTER_TURBO_PRELOAD_PAGES
            } else {
                when {
                    settings.dataSaver || settings.readerPrefetchMode == ReaderPrefetchMode.Conservative -> NEXT_CHAPTER_MIN_PRELOAD_PAGES
                    settings.readerPrefetchMode == ReaderPrefetchMode.Aggressive -> NEXT_CHAPTER_MAX_PRELOAD_PAGES
                    else -> settings.readerPrefetchPages.coerceIn(NEXT_CHAPTER_MIN_PRELOAD_PAGES, NEXT_CHAPTER_MAX_PRELOAD_PAGES)
                }
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
                    reduceMotion = settings.reduceMotion,
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
                    foreground = pageKey == foregroundPageKey || pageKey in visiblePageKeys,
                    onTapFraction = { onToggleChrome() },
                    onFailureChanged = { onFailureChanged(page, it) },
                )
            }
            val followingChapter = chapters.getOrNull(segment.chapterIndex + 1)
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
                    ChapterEnd(hasNext = false, onNext = {})
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
private fun PagedReaderPages(
    pages: List<DirectReaderPage>,
    pagerState: PagerState,
    direction: ReaderDirection,
    reduceMotion: Boolean,
    imageQuality: ReaderImageQuality,
    turboMode: Boolean,
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
                reduceMotion = reduceMotion,
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
                )
                ReaderToolButton(label = "目录", icon = Icons.Outlined.FormatListNumbered, onClick = onOpenChapters)
                ReaderToolButton(label = "设置", icon = Icons.Outlined.Tune, onClick = onOpenSettings)
                ReaderToolButton(
                    label = "下一话",
                    icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    enabled = state.currentChapterIndex < state.chapters.lastIndex,
                    onClick = onNextChapter,
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .pressFeedback(interactionSource, pressedScale = .92f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = White.copy(alpha = if (enabled) .86f else .25f), modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = White.copy(alpha = if (enabled) .62f else .22f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ReaderPage(
    page: DirectReaderPage,
    loadPage: suspend (DirectReaderPage, (Float) -> Unit) -> Bitmap,
    cachedPage: (DirectReaderPage) -> Bitmap?,
    reduceMotion: Boolean,
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
) {
    var retryKey by rememberSaveable(page.photoId, page.fileName) { mutableIntStateOf(0) }
    var pageAspectRatio by remember(page.photoId, page.fileName) {
        mutableFloatStateOf(DEFAULT_READER_PAGE_ASPECT_RATIO)
    }
    val result by produceState<Result<Bitmap>?>(
        initialValue = cachedPage(page)?.let(Result.Companion::success),
        page.url,
        page.scrambleId,
        retryKey,
        retryAllKey,
        foreground,
        imageQuality,
        turboMode,
        requestProfileKey,
    ) {
        value = cachedPage(page)?.let(Result.Companion::success)
        if (value != null || !foreground) return@produceState
        value = try {
            Result.success(
                loadPage(page) { ratio -> pageAspectRatio = ratio.coerceIn(0.05f, 8f) },
            )
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
    val verticalModifier = Modifier
        .fillMaxWidth()
        .animateContentSize(animationSpec = tween(if (reduceMotion) 0 else 180))
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
            bitmap = bitmap.asImageBitmap(),
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
private fun ChapterEnd(hasNext: Boolean, onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 42.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("本章阅读完毕", color = White.copy(alpha = .7f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Text("Comic Plus · 永久免费", color = White.copy(alpha = .38f), style = MaterialTheme.typography.bodySmall)
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
private fun ChapterMenuDialog(
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

@Composable
private fun ReaderSettingsDialog(
    settings: AppSettings,
    sourceStatus: JmSourceUiState,
    onSettingsChange: (AppSettings) -> Unit,
    onRefreshSources: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(settings) }
    fun commit(updated: AppSettings) {
        draft = updated
        onSettingsChange(updated)
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF171719),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text("阅读设置", color = White, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Text("阅读模式", color = White.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                ReaderSegmentedControl(
                    labels = listOf("纵向滚动", "左右分页"),
                    selected = if (draft.readerMode == ReaderMode.Vertical) "纵向滚动" else "左右分页",
                    onSelected = { label ->
                        commit(
                            draft.copy(readerMode = if (label == "纵向滚动") ReaderMode.Vertical else ReaderMode.Paged),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                AnimatedVisibility(
                    visible = draft.readerMode == ReaderMode.Paged,
                    enter = fadeIn() + slideInVertically { -it / 3 },
                    exit = fadeOut() + slideOutVertically { -it / 3 },
                ) {
                    Spacer(Modifier.height(10.dp))
                    ReaderSegmentedControl(
                        labels = listOf("从左向右", "从右向左"),
                        selected = if (draft.readerDirection == ReaderDirection.LeftToRight) "从左向右" else "从右向左",
                        onSelected = { label ->
                            commit(
                                draft.copy(
                                    readerDirection = if (label == "从左向右") {
                                        ReaderDirection.LeftToRight
                                    } else {
                                        ReaderDirection.RightToLeft
                                    },
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("页面画质", color = White.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                ReaderSegmentedControl(
                    labels = listOf("流畅", "标准", "高清"),
                    selected = when {
                        draft.readerTurboMode -> "流畅"
                        draft.readerImageQuality == ReaderImageQuality.Low -> "流畅"
                        draft.readerImageQuality == ReaderImageQuality.High -> "高清"
                        else -> "标准"
                    },
                    onSelected = { label ->
                        commit(
                            draft.copy(
                                readerImageQuality = when (label) {
                                    "流畅" -> ReaderImageQuality.Low
                                    "高清" -> ReaderImageQuality.High
                                    else -> ReaderImageQuality.Medium
                                },
                                readerTurboMode = false,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "流畅 720px · 标准 1080px · 高清 1440px",
                    color = White.copy(alpha = .38f),
                    style = MaterialTheme.typography.labelSmall,
                )
                SettingSwitchRow("极速模式", draft.readerTurboMode) {
                    commit(
                        draft.copy(
                            readerTurboMode = it,
                            dataSaver = if (it) false else draft.dataSaver,
                        ),
                    )
                }
                AnimatedVisibility(
                    visible = draft.readerTurboMode,
                    enter = fadeIn() + slideInVertically { -it / 4 },
                    exit = fadeOut() + slideOutVertically { -it / 4 },
                ) {
                    Text(
                        "极速模式使用 480px 解码、当前页抢占和低延迟图片线路",
                        color = White.copy(alpha = .42f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("线路与延迟", color = White.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            sourceStatus.selectedImageHost?.let { host ->
                                val latency = sourceStatus.imageItems.firstOrNull { it.host == host }?.latencyMs
                                if (latency == null) host else "$host · $latency ms"
                            } ?: sourceStatus.selectedHost ?: "尚未检测",
                            color = White.copy(alpha = .42f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onRefreshSources, enabled = !sourceStatus.checking) {
                        if (sourceStatus.checking) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "检测线路延迟", tint = White.copy(alpha = .8f))
                        }
                    }
                }
                SettingSwitchRow("自动选择最快线路", draft.autoSelectSource) {
                    commit(draft.copy(autoSelectSource = it))
                }
                ReaderSourceEndpointGroup(
                    title = "漫画图片线路",
                    endpoints = sourceStatus.imageItems,
                    selectedHost = if (draft.autoSelectSource) {
                        sourceStatus.selectedImageHost
                    } else {
                        draft.preferredImageHost ?: sourceStatus.selectedImageHost
                    },
                    onSelect = { host ->
                        commit(draft.copy(autoSelectSource = false, preferredImageHost = host))
                    },
                )
                ReaderSourceEndpointGroup(
                    title = "章节接口线路",
                    endpoints = sourceStatus.items,
                    selectedHost = if (draft.autoSelectSource) {
                        sourceStatus.selectedHost
                    } else {
                        draft.preferredSourceHost ?: sourceStatus.selectedHost
                    },
                    onSelect = { host ->
                        commit(draft.copy(autoSelectSource = false, preferredSourceHost = host))
                    },
                )
                Spacer(Modifier.height(12.dp))
                SettingSwitchRow("保持屏幕常亮", draft.keepScreenOn) {
                    commit(draft.copy(keepScreenOn = it))
                }
                SettingSwitchRow("点击切换菜单", draft.tapToToggleReaderMenu) {
                    commit(draft.copy(tapToToggleReaderMenu = it))
                }
                SettingSwitchRow("跟随系统亮度", draft.readerBrightnessPercent == 0) { followSystem ->
                    commit(
                        draft.copy(
                            readerBrightnessPercent = if (followSystem) 0 else 60,
                        ),
                    )
                }
                AnimatedVisibility(
                    visible = draft.readerBrightnessPercent > 0,
                    enter = fadeIn() + slideInVertically { it / 4 },
                    exit = fadeOut() + slideOutVertically { it / 4 },
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "阅读亮度 ${draft.readerBrightnessPercent}%",
                            color = White.copy(alpha = .72f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = draft.readerBrightnessPercent.toFloat(),
                            onValueChange = {
                                val percent = (it / 10f).roundToInt().times(10).coerceIn(10, 100)
                                draft = draft.copy(readerBrightnessPercent = percent)
                            },
                            onValueChangeFinished = { onSettingsChange(draft) },
                            valueRange = 10f..100f,
                            steps = 8,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "预加载策略",
                    color = White.copy(alpha = .72f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                ReaderSegmentedControl(
                    labels = listOf("省内存", "智能", "积极", "自定义"),
                    selected = readerPrefetchModeLabel(draft.readerPrefetchMode),
                    onSelected = { label ->
                        val mode = readerPrefetchModeForLabel(label)
                        draft = draft.copy(
                            readerPrefetchMode = mode,
                            readerPrefetchPages = when (mode) {
                                ReaderPrefetchMode.Conservative -> 1
                                ReaderPrefetchMode.Smart -> 3
                                ReaderPrefetchMode.Aggressive -> 5
                                ReaderPrefetchMode.Custom -> draft.readerPrefetchPages.coerceIn(0, 6)
                            },
                        )
                        onSettingsChange(draft)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    readerPrefetchDescription(draft.readerPrefetchMode, draft.readerPrefetchPages),
                    color = White.copy(alpha = .42f),
                    style = MaterialTheme.typography.labelSmall,
                )
                AnimatedVisibility(
                    visible = draft.readerPrefetchMode == ReaderPrefetchMode.Custom,
                    enter = fadeIn() + slideInVertically { it / 4 },
                    exit = fadeOut() + slideOutVertically { it / 4 },
                ) {
                    Column {
                        Text("精确页数 ${draft.readerPrefetchPages}", color = White.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = draft.readerPrefetchPages.toFloat(),
                            onValueChange = { draft = draft.copy(readerPrefetchPages = it.roundToInt().coerceIn(0, 6)) },
                            onValueChangeFinished = { onSettingsChange(draft) },
                            valueRange = 0f..6f,
                            steps = 5,
                        )
                    }
                }
                Text("页面间距 ${draft.readerPageSpacingDp} dp", color = White.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = draft.readerPageSpacingDp.toFloat(),
                    onValueChange = { draft = draft.copy(readerPageSpacingDp = it.roundToInt().coerceIn(0, 16)) },
                    onValueChangeFinished = { onSettingsChange(draft) },
                    valueRange = 0f..16f,
                    steps = 7,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Color.Black),
                ) {
                    Text("完成")
                }
            }
        }
    }
}

@Composable
private fun ReaderSourceEndpointGroup(
    title: String,
    endpoints: List<JmSourceUiItem>,
    selectedHost: String?,
    onSelect: (String) -> Unit,
) {
    if (endpoints.isEmpty()) return
    Text(
        title,
        modifier = Modifier.padding(top = 8.dp, bottom = 3.dp),
        color = White.copy(alpha = .55f),
        style = MaterialTheme.typography.labelMedium,
    )
    endpoints.forEach { endpoint ->
        val selected = endpoint.host == selectedHost
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .clickable { onSelect(endpoint.host) }
                .background(if (selected) White.copy(alpha = .1f) else Color.Transparent)
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(7.dp).clip(CircleShape).background(
                    when {
                        endpoint.latencyMs == null -> Color(0xFFE57373)
                        selected -> White
                        else -> White.copy(alpha = .25f)
                    },
                ),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                endpoint.host,
                modifier = Modifier.weight(1f),
                color = White.copy(alpha = if (selected) .92f else .58f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                endpoint.latencyMs?.let { if (selected) "优先 · $it ms" else "$it ms" } ?: "不可用",
                color = if (endpoint.latencyMs == null) Color(0xFFE57373) else White.copy(alpha = .46f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = White.copy(alpha = .84f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun readerPrefetchModeLabel(mode: ReaderPrefetchMode): String = when (mode) {
    ReaderPrefetchMode.Conservative -> "省内存"
    ReaderPrefetchMode.Smart -> "智能"
    ReaderPrefetchMode.Aggressive -> "积极"
    ReaderPrefetchMode.Custom -> "自定义"
}

private fun readerPrefetchModeForLabel(label: String): ReaderPrefetchMode = when (label) {
    "省内存" -> ReaderPrefetchMode.Conservative
    "积极" -> ReaderPrefetchMode.Aggressive
    "自定义" -> ReaderPrefetchMode.Custom
    else -> ReaderPrefetchMode.Smart
}

private fun readerPrefetchDescription(mode: ReaderPrefetchMode, pages: Int): String = when (mode) {
    ReaderPrefetchMode.Conservative -> "只保温相邻 1 页，优先降低内存和流量占用"
    ReaderPrefetchMode.Aggressive -> "前后页并行保温，并提前准备下一话"
    ReaderPrefetchMode.Custom -> "按 $pages 页保温，并自动避让正在阅读的页面"
    ReaderPrefetchMode.Smart -> "根据翻页速度、方向和设备内存动态调整"
}

@Composable
private fun ReaderSegmentedControl(
    labels: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalComicPlusReduceMotion.current
    BoxWithConstraints(modifier.background(Color(0xFF242426), RoundedCornerShape(6.dp)).padding(3.dp)) {
        if (labels.isEmpty()) return@BoxWithConstraints
        val segmentWidth = maxWidth / labels.size
        val selectedIndex = labels.indexOf(selected).coerceAtLeast(0)
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = .84f, stiffness = 620f),
            label = "reader-segment-indicator-offset",
        )
        Surface(
            modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(indicatorOffset.roundToPx(), 0) }
                .width(segmentWidth)
                .height(40.dp),
            color = White.copy(alpha = .14f),
            shape = RoundedCornerShape(4.dp),
        ) {}
        Row(Modifier.fillMaxWidth().height(40.dp)) {
            labels.forEach { label ->
                val active = label == selected
                val contentColor by animateColorAsState(
                    White.copy(alpha = if (active) 1f else .58f),
                    tween(if (reduceMotion) 0 else 240),
                    label = "reader-segment-content",
                )
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize().clickable { onSelected(label) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = contentColor,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
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
private const val NEXT_CHAPTER_MIN_PRELOAD_PAGES = 4
private const val NEXT_CHAPTER_MAX_PRELOAD_PAGES = 6
private const val NEXT_CHAPTER_TURBO_PRELOAD_PAGES = 8
private const val READER_EDGE_TAP_FRACTION = .28f
private const val DEFAULT_READER_PAGE_ASPECT_RATIO = .70f
private const val MAX_READER_ZOOM = 4f
private const val DOUBLE_TAP_READER_ZOOM = 2.5f

internal fun readingIndexToPagerPage(
    readingIndex: Int,
    pageCount: Int,
    direction: ReaderDirection,
): Int {
    if (pageCount <= 0) return 0
    val safeIndex = readingIndex.coerceIn(0, pageCount - 1)
    return if (direction == ReaderDirection.RightToLeft) pageCount - 1 - safeIndex else safeIndex
}

internal fun pagerPageToReadingIndex(
    pagerPage: Int,
    pageCount: Int,
    direction: ReaderDirection,
): Int = readingIndexToPagerPage(pagerPage, pageCount, direction)

internal fun readerPrefetchIndices(
    currentPageIndex: Int,
    pageCount: Int,
    distance: Int,
): List<Int> {
    return readerPrefetchPlan(currentPageIndex, pageCount, distance, direction = 1, includeOpposite = false)
}

internal fun readerPagedPrefetchIndices(
    currentPageIndex: Int,
    pageCount: Int,
    distance: Int,
): List<Int> {
    if (pageCount <= 0 || distance <= 0) return emptyList()
    val safeCurrent = currentPageIndex.coerceIn(0, pageCount - 1)
    val result = ArrayList<Int>(distance)
    var offset = 1
    while (result.size < distance && (safeCurrent + offset < pageCount || safeCurrent - offset >= 0)) {
        if (safeCurrent + offset < pageCount) result += safeCurrent + offset
        if (result.size < distance && safeCurrent - offset >= 0) result += safeCurrent - offset
        offset++
    }
    return result
}

/**
 * Returns a direction-aware warming order. The first half follows the user's
 * travel direction, then a small backtrack window keeps accidental reversals
 * instant without stealing the foreground decode slot.
 */
internal fun readerPrefetchPlan(
    currentPageIndex: Int,
    pageCount: Int,
    distance: Int,
    direction: Int,
    includeOpposite: Boolean,
): List<Int> {
    if (pageCount <= 0 || distance <= 0) return emptyList()
    val safeCurrent = currentPageIndex.coerceIn(0, pageCount - 1)
    val forward = if (direction < 0) -1 else 1
    fun side(step: Int): List<Int> = buildList(distance) {
        for (offset in 1..distance) {
            val index = safeCurrent + step * offset
            if (index !in 0 until pageCount) break
            add(index)
        }
    }
    val primary = side(forward)
    if (!includeOpposite) return primary
    val opposite = side(-forward)
    val result = ArrayList<Int>(distance)
    // Keep the established UX: spend roughly half the budget in the travel
    // direction, then warm the opposite side. If an edge has fewer pages,
    // consume the remaining budget from the other side instead of returning a
    // short plan while valid pages are still available.
    val primaryQuota = minOf((distance + 1) / 2, primary.size)
    result += primary.take(primaryQuota)
    result += opposite.take(distance - result.size)
    if (result.size < distance) result += primary.drop(primaryQuota).take(distance - result.size)
    if (result.size < distance) result += opposite.drop(distance - result.size).take(distance - result.size)
    return result.distinct().take(distance)
}

internal fun shouldPreloadNextChapter(
    currentPageIndex: Int,
    pageCount: Int,
    distance: Int,
): Boolean {
    if (pageCount <= 0 || distance < 0) return false
    val safePageIndex = currentPageIndex.coerceIn(0, pageCount - 1)
    return pageCount - safePageIndex - 1 <= distance
}

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

