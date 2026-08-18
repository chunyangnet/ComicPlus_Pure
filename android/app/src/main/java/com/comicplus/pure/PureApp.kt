package com.comicplus.pure

import android.content.ActivityNotFoundException
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.comicplus.app.ui.icons.ComicPlusIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.net.toUri
import com.comicplus.app.ui.AppSettings
import com.comicplus.app.ui.ComicResolveUiState
import com.comicplus.app.ui.ComicUiItem
import com.comicplus.app.ui.LocalComicPlusReduceMotion
import com.comicplus.app.ui.LocalFavoritePendingKeys
import com.comicplus.app.ui.ReaderUiState
import com.comicplus.app.ui.key
import com.comicplus.app.ui.screens.CategoryScreen
import com.comicplus.app.ui.screens.ComicDetailScreen
import com.comicplus.app.ui.screens.HomeScreen
import com.comicplus.app.ui.screens.LibraryScreen
import com.comicplus.app.ui.screens.OfficialBrowseScreen
import com.comicplus.app.ui.screens.ReaderScreen
import com.comicplus.app.ui.screens.RankingScreen
import com.comicplus.app.ui.screens.SearchScreen
import com.comicplus.app.ui.screens.SettingsScreen
import com.comicplus.app.ui.theme.ComicPlusTheme
import com.comicplus.app.ui.theme.White
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MainTab(val label: String, val outlined: ImageVector, val filled: ImageVector) {
    Home("首页", Icons.Outlined.Home, Icons.Filled.Home),
    Ranking("排行", Icons.Outlined.EmojiEvents, Icons.Filled.EmojiEvents),
    Category("分类", Icons.Outlined.Category, Icons.Filled.Category),
    Library("书架", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    Settings("设置", Icons.Outlined.Settings, Icons.Filled.Settings),
}

private enum class AppLayer { Main, OfficialBrowse, Search, Detail, Reader }

@Composable
fun PureApp() {
    val context = LocalContext.current
    val factory = remember(context) { PureViewModel.Factory(context.applicationContext) }
    val viewModel: PureViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tabName by rememberSaveable { mutableStateOf(MainTab.Home.name) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchInitialQuery by rememberSaveable { mutableStateOf("") }
    var searchInitialScope by rememberSaveable { mutableIntStateOf(0) }
    var officialBrowseVisible by rememberSaveable { mutableStateOf(false) }
    val favoriteKeys = remember(state.favorites) { state.favorites.mapTo(hashSetOf(), ComicUiItem::key) }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setAppForeground(true)
                Lifecycle.Event.ON_STOP -> {
                    viewModel.setAppForeground(false)
                    viewModel.flushLocalState()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    ComicPlusTheme(
        paletteKey = state.settings.paletteKey,
        darkMode = state.settings.darkMode,
        reduceMotion = state.settings.reduceMotion,
    ) {
        GlobalSystemBars(
            readerVisible = state.reader !is ReaderUiState.Idle,
        )
        CompositionLocalProvider(
            LocalComicPlusReduceMotion provides state.settings.reduceMotion,
            LocalFavoritePendingKeys provides state.favoritePendingKeys,
        ) {
            val readerVisible = state.reader !is ReaderUiState.Idle
            val detailVisible = state.detail !is ComicResolveUiState.Idle
            var retainedReaderState by remember { mutableStateOf<ReaderUiState?>(null) }
            var retainedDetailState by remember { mutableStateOf<ComicResolveUiState?>(null) }
            SideEffect {
                if (readerVisible) retainedReaderState = state.reader
                if (detailVisible) retainedDetailState = state.detail
            }
            LaunchedEffect(readerVisible, state.settings.reduceMotion) {
                if (!readerVisible) {
                    if (!state.settings.reduceMotion) delay(NAVIGATION_EXIT_MILLIS)
                    retainedReaderState = null
                }
            }
            LaunchedEffect(detailVisible, state.settings.reduceMotion) {
                if (!detailVisible) {
                    if (!state.settings.reduceMotion) delay(NAVIGATION_EXIT_MILLIS)
                    retainedDetailState = null
                }
            }

            val displayedReaderState = if (readerVisible) state.reader else retainedReaderState
            val displayedDetailState = if (detailVisible) state.detail else retainedDetailState
            val navigationStack = remember(
                officialBrowseVisible,
                searchVisible,
                detailVisible,
                readerVisible,
            ) {
                buildList {
                    add(AppLayer.Main)
                    if (officialBrowseVisible) add(AppLayer.OfficialBrowse)
                    if (searchVisible) add(AppLayer.Search)
                    if (detailVisible) add(AppLayer.Detail)
                    if (readerVisible) add(AppLayer.Reader)
                }
            }
            val visibleLayers = remember(navigationStack) { navigationStack.takeLast(2).toSet() }
            val topLayer = navigationStack.last()
            val mainReduceMotion = state.settings.reduceMotion || topLayer != AppLayer.Main

            Box(Modifier.fillMaxSize()) {
                if (AppLayer.Main in visibleLayers) {
                    CompositionLocalProvider(LocalComicPlusReduceMotion provides mainReduceMotion) {
                        val currentTab = MainTab.entries.firstOrNull { it.name == tabName }
                            ?: MainTab.Home
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            bottomBar = {
                                ComicPlusBottomBar(currentTab, mainReduceMotion) { tabName = it.name }
                            },
                        ) { padding ->
                            val modifier = Modifier.fillMaxSize().padding(padding)
                            AnimatedContent(
                                targetState = currentTab,
                                transitionSpec = {
                                    if (mainReduceMotion) {
                                        EnterTransition.None togetherWith ExitTransition.None
                                    } else {
                                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                        (
                                            slideInHorizontally(tween(280)) { direction * it / 7 } +
                                                fadeIn(tween(180))
                                            ).togetherWith(
                                            slideOutHorizontally(tween(220)) { -direction * it / 10 } +
                                                fadeOut(tween(150)),
                                        )
                                    }
                                },
                                label = "main-tab-transition",
                            ) { tab ->
                                when (tab) {
                                    MainTab.Home -> HomeScreen(
                                        comics = state.home,
                                        categories = state.categories,
                                        isBootstrapping = state.loading,
                                        isRefreshing = state.loading && state.home.isNotEmpty(),
                                        discoveryItems = state.discoveryItems,
                                        discoveryLoading = state.discoveryLoading,
                                        discoveryExhausted = state.discoveryExhausted,
                                        reduceMotion = mainReduceMotion,
                                        onRefresh = viewModel::refreshHome,
                                        onResolve = viewModel::openComic,
                                        onOpenSearch = { query ->
                                            searchInitialQuery = query
                                            searchInitialScope = 0
                                            searchVisible = true
                                            if (query.isNotBlank()) viewModel.search(query)
                                        },
                                        onLoadMoreDiscovery = viewModel::loadMoreDiscovery,
                                        onOpenCategory = { category ->
                                            viewModel.selectCategory(category)
                                            tabName = MainTab.Category.name
                                        },
                                        onOpenBrowse = { tabName = MainTab.Category.name },
                                        onMessage = { message ->
                                            scope.launch { snackbar.showSnackbar(message) }
                                        },
                                        modifier = modifier,
                                        favoriteKeys = favoriteKeys,
                                        onToggleFavorite = viewModel::toggleFavorite,
                                    )

                                    MainTab.Ranking -> RankingScreen(
                                        state = state.rankings,
                                        reduceMotion = mainReduceMotion,
                                        onEnsureRankings = { viewModel.loadRankings() },
                                        onSelectOrder = viewModel::loadRankings,
                                        onOpenOfficialBrowse = { officialBrowseVisible = true },
                                        onOpenSearch = { query ->
                                            searchInitialQuery = query
                                            searchInitialScope = 0
                                            searchVisible = true
                                            if (query.isNotBlank()) viewModel.search(query)
                                        },
                                        onClearSearch = viewModel::clearSearch,
                                        onResolve = viewModel::openComic,
                                        onMessage = { message ->
                                            scope.launch { snackbar.showSnackbar(message) }
                                        },
                                        modifier = modifier,
                                        favoriteKeys = favoriteKeys,
                                        onToggleFavorite = viewModel::toggleFavorite,
                                    )

                                    MainTab.Category -> CategoryScreen(
                                        categories = state.categories,
                                        state = state.category,
                                        reduceMotion = mainReduceMotion,
                                        onEnsureCategories = viewModel::loadCategories,
                                        onSelectCategory = viewModel::selectCategory,
                                        onSelectOrder = viewModel::selectCategoryOrder,
                                        onLoadMore = viewModel::loadMoreCategory,
                                        onOpenSearch = { query ->
                                            searchInitialQuery = query
                                            searchInitialScope = 0
                                            searchVisible = true
                                            if (query.isNotBlank()) viewModel.search(query)
                                        },
                                        onClearSearch = viewModel::clearSearch,
                                        onResolve = viewModel::openComic,
                                        onMessage = { message ->
                                            scope.launch { snackbar.showSnackbar(message) }
                                        },
                                        modifier = modifier,
                                        favoriteKeys = favoriteKeys,
                                        onToggleFavorite = viewModel::toggleFavorite,
                                    )

                                    MainTab.Library -> LibraryScreen(
                                        favorites = state.favorites,
                                        favoriteFolders = state.favoriteFolders,
                                        history = state.history,
                                        signedIn = state.account.signedIn,
                                        onOpen = viewModel::openComic,
                                        onToggleFavorite = viewModel::toggleFavorite,
                                        onSelectFavoriteFolder = viewModel::selectFavoriteFolder,
                                        onRetryFavoriteFolder = viewModel::retryFavoriteFolder,
                                        onCreateFavoriteFolder = viewModel::createFavoriteFolder,
                                        onMoveFavoriteToFolder = viewModel::moveFavoriteToFolder,
                                        onClearHistory = viewModel::clearHistory,
                                        modifier = modifier,
                                    )

                                    MainTab.Settings -> SettingsScreen(
                                        settings = state.settings,
                                        downloads = state.downloads,
                                        account = state.account,
                                        onLogin = viewModel::login,
                                        onLogout = viewModel::logout,
                                        onSyncFavorites = viewModel::syncOfficialFavorites,
                                        sourceStatus = state.sourceStatus,
                                        updateStatus = state.appUpdate,
                                        onSettingsChange = viewModel::updateSettings,
                                        onCheckUpdates = viewModel::checkForUpdates,
                                        onOpenUpdate = { url ->
                                            openExternalUrl(context, url) { message ->
                                                scope.launch { snackbar.showSnackbar(message) }
                                            }
                                        },
                                        onClearReaderCache = viewModel::clearReaderCache,
                                        onRefreshSources = { viewModel.refreshSources(force = true) },
                                        onOpenDownload = viewModel::openDownloaded,
                                        onDeleteDownload = viewModel::deleteDownload,
                                        modifier = modifier,
                                    )
                                }
                            }
                        }
                    }
                }

                PredictiveBackLayer(
                    visible = officialBrowseVisible && AppLayer.OfficialBrowse in visibleLayers,
                    enabled = topLayer == AppLayer.OfficialBrowse,
                    reduceMotion = state.settings.reduceMotion,
                    onBack = { officialBrowseVisible = false },
                ) { layerModifier ->
                    val layerReduceMotion = state.settings.reduceMotion || topLayer != AppLayer.OfficialBrowse
                    CompositionLocalProvider(LocalComicPlusReduceMotion provides layerReduceMotion) {
                        OfficialBrowseScreen(
                            state = state.officialBrowse,
                            categories = state.categories,
                            reduceMotion = layerReduceMotion,
                            onBack = { officialBrowseVisible = false },
                            onEnsure = viewModel::ensureOfficialBrowse,
                            onSelectWeeklyCategory = viewModel::selectWeeklyCategory,
                            onSelectWeeklyType = viewModel::selectWeeklyType,
                            onRetryWeekly = { viewModel.loadWeekly(force = true) },
                            onOpenTag = { tag ->
                                searchInitialQuery = tag
                                searchInitialScope = 3
                                searchVisible = true
                                viewModel.search(tag, mainTag = 3)
                            },
                            onSelectTypeRanking = { slug, order ->
                                viewModel.loadTypeRanking(slug, order)
                            },
                            onRetryTypeRanking = { viewModel.loadTypeRanking(force = true) },
                            onResolve = viewModel::openComic,
                            modifier = layerModifier,
                            favoriteKeys = favoriteKeys,
                            onToggleFavorite = viewModel::toggleFavorite,
                        )
                    }
                }

                PredictiveBackLayer(
                    visible = searchVisible && AppLayer.Search in visibleLayers,
                    enabled = topLayer == AppLayer.Search,
                    reduceMotion = state.settings.reduceMotion,
                    onBack = {
                        searchVisible = false
                        viewModel.clearSearch()
                    },
                ) { layerModifier ->
                    val layerReduceMotion = state.settings.reduceMotion || topLayer != AppLayer.Search
                    CompositionLocalProvider(LocalComicPlusReduceMotion provides layerReduceMotion) {
                        SearchScreen(
                            state = state.search,
                            initialQuery = searchInitialQuery,
                            initialScope = searchInitialScope,
                            reduceMotion = layerReduceMotion,
                            onBack = {
                                searchVisible = false
                                viewModel.clearSearch()
                            },
                            onSearch = { query, tag, order -> viewModel.search(query, tag, order) },
                            onLoadMore = viewModel::loadMoreSearch,
                            onResolve = viewModel::openComic,
                            onRedirectConsumed = viewModel::consumeSearchRedirect,
                            onMessage = { message ->
                                scope.launch { snackbar.showSnackbar(message) }
                            },
                            modifier = layerModifier,
                            favoriteKeys = favoriteKeys,
                            onToggleFavorite = viewModel::toggleFavorite,
                        )
                    }
                }

                PredictiveBackLayer(
                    visible = detailVisible && AppLayer.Detail in visibleLayers,
                    enabled = topLayer == AppLayer.Detail,
                    reduceMotion = state.settings.reduceMotion,
                    onBack = viewModel::dismissDetail,
                ) { layerModifier ->
                    val detailState = displayedDetailState ?: return@PredictiveBackLayer
                    val ready = detailState as? ComicResolveUiState.Ready
                    val downloadedChapterIds = remember(state.downloads, ready?.jmId) {
                        state.downloads
                            .asSequence()
                            .filter { it.comicId == ready?.jmId && it.complete }
                            .mapTo(mutableSetOf(), DownloadedChapter::chapterId)
                    }
                    val downloadProgress = remember(state.downloadProgress) {
                        state.downloadProgress.mapKeys { it.key.substringAfter(':') }
                    }
                    val layerReduceMotion = state.settings.reduceMotion || topLayer != AppLayer.Detail
                    CompositionLocalProvider(LocalComicPlusReduceMotion provides layerReduceMotion) {
                        ComicDetailScreen(
                            state = detailState,
                            reduceMotion = layerReduceMotion,
                            autoResumeReading = state.settings.autoResumeReading,
                            chapterDescending = state.settings.chapterDescending,
                            onChapterDescendingChange = viewModel::updateChapterSort,
                            onBack = viewModel::dismissDetail,
                            onShare = { detail ->
                                shareComic(context, detail) { message ->
                                    scope.launch { snackbar.showSnackbar(message) }
                                }
                            },
                            onRead = { detail, chapter ->
                                viewModel.openReader(detail, chapter, initialPageIndex = 0)
                            },
                            modifier = layerModifier,
                            onContinueReading = { detail, chapter, initialPageIndex ->
                                viewModel.openReader(detail, chapter, initialPageIndex)
                            },
                            onOpenComments = viewModel::openComicComments,
                            downloadedChapterIds = downloadedChapterIds,
                            downloadProgress = downloadProgress,
                            onDownload = viewModel::downloadChapter,
                            isFavorite = ready?.let { "${it.source}:${it.jmId}" in favoriteKeys } ?: false,
                            onToggleFavorite = viewModel::toggleFavorite,
                            comments = state.comments,
                            onRetryComments = viewModel::retryComments,
                            onLoadMoreComments = viewModel::loadMoreComments,
                        )
                    }
                }

                PredictiveBackLayer(
                    visible = readerVisible && AppLayer.Reader in visibleLayers,
                    enabled = topLayer == AppLayer.Reader,
                    reduceMotion = state.settings.reduceMotion,
                    onBack = viewModel::closeReader,
                ) { layerModifier ->
                    val readerState = displayedReaderState ?: return@PredictiveBackLayer
                    ReaderScreen(
                        state = readerState,
                        settings = state.settings,
                        sourceStatus = state.sourceStatus,
                        loadPage = viewModel::loadReaderPage,
                        prefetchPage = viewModel::prefetchReaderPage,
                        cachedPage = viewModel::cachedReaderPage,
                        onSelectChapter = viewModel::selectReaderChapter,
                        loadChapterSegment = viewModel::loadReaderChapterSegment,
                        onRetryChapter = viewModel::retryReaderChapter,
                        onProgressChange = viewModel::recordReaderProgress,
                        onSettingsChange = viewModel::updateSettings,
                        onRefreshSources = {
                            viewModel.refreshSources(force = true, updateOfficialList = true)
                        },
                        comments = state.comments,
                        onOpenComments = viewModel::openComments,
                        onRetryComments = viewModel::retryComments,
                        onLoadMoreComments = viewModel::loadMoreComments,
                        onClose = viewModel::closeReader,
                        modifier = layerModifier,
                    )
                }

                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
@Suppress("DEPRECATION") // Required to restore bar colors on pre-Android 15 devices.
private fun GlobalSystemBars(readerVisible: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = context.findActivity()?.window
    // The theme color is already animated. Follow it directly so the system
    // bars stay in phase with the app instead of adding a second easing pass.
    val barColor = MaterialTheme.colorScheme.background
    val useLightIcons = barColor.luminance() < .5f
    DisposableEffect(window, readerVisible) {
        if (window == null || readerVisible) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        val previousLightStatus = controller.isAppearanceLightStatusBars
        val previousLightNavigation = controller.isAppearanceLightNavigationBars
        val previousStatusColor = window.statusBarColor
        val previousNavigationColor = window.navigationBarColor
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.isAppearanceLightStatusBars = previousLightStatus
            controller.isAppearanceLightNavigationBars = previousLightNavigation
            window.statusBarColor = previousStatusColor
            window.navigationBarColor = previousNavigationColor
        }
    }
    SideEffect {
        if (window == null || readerVisible) return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !useLightIcons
        controller.isAppearanceLightNavigationBars = !useLightIcons
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = barColor.toArgb()
    }
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
private fun ComicPlusBottomBar(current: MainTab, reduceMotion: Boolean, onSelect: (MainTab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(65.dp).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MainTab.entries.forEach { tab ->
                    val selected = tab == current
                    val interaction = remember(tab) { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        if (reduceMotion || !selected) 1f else if (pressed) .97f else 1.02f,
                        spring(dampingRatio = .82f, stiffness = 900f),
                        label = "bottom-icon-scale",
                    )
                    val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "bottom-icon-color")
                    val pillColor by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .56f) else Color.Transparent,
                        label = "bottom-pill-color",
                    )
                    Column(
                        modifier = Modifier.weight(1f).clickable(interactionSource = interaction, indication = LocalIndication.current) { onSelect(tab) }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(shape = RoundedCornerShape(12.dp), color = pillColor) {
                            Icon(
                                if (selected) tab.filled else tab.outlined,
                                tab.label,
                                tint = color,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).size(21.dp).scale(scale),
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(tab.label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private fun shareComic(context: android.content.Context, state: ComicResolveUiState.Ready, onMessage: (String) -> Unit) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "《${state.title}》\nJM${state.jmId}")
    }
    try {
        context.startActivity(Intent.createChooser(intent, "分享漫画"))
    } catch (_: ActivityNotFoundException) {
        onMessage("当前设备没有可用的分享应用")
    }
}

private fun openExternalUrl(context: Context, url: String, onMessage: (String) -> Unit) {
    val uri = runCatchingNonFatal { url.toUri() }.getOrNull()
    if (uri == null || uri.scheme != "https") {
        onMessage("更新地址无效")
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        onMessage("当前设备没有可用的浏览器")
    }
}
