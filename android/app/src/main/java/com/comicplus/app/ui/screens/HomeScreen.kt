@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.comicplus.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.comicplus.app.ui.icons.ComicPlusIcons as Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicplus.app.search.JmIdParser
import com.comicplus.app.data.source.DirectJmCategory
import com.comicplus.app.ui.ComicPlusTestTags
import com.comicplus.app.ui.ComicUiItem
import com.comicplus.app.ui.key
import com.comicplus.app.ui.comicPlusDeviceTestTag
import com.comicplus.app.ui.components.AppBarAction
import com.comicplus.app.ui.components.ComicCover
import com.comicplus.app.ui.components.ComicPlusTopBar
import com.comicplus.app.ui.components.ComicPosterCard
import com.comicplus.app.ui.components.CpDimens
import com.comicplus.app.ui.components.PillRow
import com.comicplus.app.ui.components.RankingRow
import com.comicplus.app.ui.components.SearchCapsule
import com.comicplus.app.ui.components.SectionTitle
import com.comicplus.app.ui.components.SegmentedControl
import com.comicplus.app.ui.components.ShimmerBlock
import com.comicplus.app.ui.components.pressFeedback
import com.comicplus.app.ui.components.rememberMotionAllowed
import com.comicplus.app.ui.components.rememberShimmerBrush
import com.comicplus.app.ui.components.supportingLabel
import com.comicplus.app.ui.theme.Ink
import com.comicplus.app.ui.theme.InkSoft
import com.comicplus.app.ui.theme.Muted
import com.comicplus.app.ui.theme.SurfaceSoft
import com.comicplus.app.ui.theme.White
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    comics: List<ComicUiItem>,
    categories: List<DirectJmCategory>,
    isBootstrapping: Boolean,
    isRefreshing: Boolean,
    discoveryItems: List<ComicUiItem>,
    discoveryLoading: Boolean,
    discoveryExhausted: Boolean,
    reduceMotion: Boolean,
    onRefresh: () -> Unit,
    onResolve: (ComicUiItem) -> Unit,
    onOpenSearch: (String) -> Unit,
    onLoadMoreDiscovery: () -> Unit,
    onOpenCategory: (DirectJmCategory) -> Unit,
    onOpenBrowse: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    favoriteKeys: Set<String> = emptySet(),
    onToggleFavorite: (ComicUiItem) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    val searchIntent = remember(query) { JmIdParser.parse(query) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val currentCanLoadMoreDiscovery = rememberUpdatedState(!discoveryLoading && !discoveryExhausted)
    val currentOnLoadMoreDiscovery = rememberUpdatedState(onLoadMoreDiscovery)
    // The scrolling edge is the trigger. Loading/result changes only update
    // the gate and must not restart the collector while the edge stays visible.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 4 && info.totalItemsCount > 0
        }.collect { nearEnd ->
            if (nearEnd && currentCanLoadMoreDiscovery.value) currentOnLoadMoreDiscovery.value()
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().comicPlusDeviceTestTag(ComicPlusTestTags.HomeFeed),
            contentPadding = PaddingValues(bottom = 38.dp),
        ) {
            item(key = "home-top", contentType = "home-top") {
                Column {
                    ComicPlusTopBar(
                        title = "Comic Plus",
                        subtitle = "JM 官方源 · 本地纯净版",
                        showLogo = true,
                        actions = listOf(
                            AppBarAction(Icons.Outlined.Search, "搜索") {
                                searchFocusRequester.requestFocus()
                                keyboardController?.show()
                            },
                        ),
                    )
                    SearchCapsule(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = {
                            searchIntent.sourceId?.let { id -> onResolve(ComicUiItem(id, "JM$id", "", "", 0)) }
                                ?: if (query.isBlank()) {
                                    onMessage("请输入漫画名或 JM ID")
                                } else {
                                    onOpenSearch(query)
                                }
                        },
                        hint = searchIntent.sourceId?.let { "JM$it" },
                        placeholder = "搜索漫画或输入 JM ID",
                        focusRequester = searchFocusRequester,
                        modifier = Modifier.padding(horizontal = CpDimens.screenPadding),
                    )
                }
            }

            if (isBootstrapping && comics.isEmpty()) {
                item(key = "home-skeleton", contentType = "home-skeleton") {
                    HomeSkeleton(reduceMotion = reduceMotion)
                }
                return@LazyColumn
            }

            item(key = "home-featured", contentType = "home-featured") {
                Spacer(Modifier.height(18.dp))
                FeaturedCarousel(
                    items = comics.take(5),
                    onOpen = onResolve,
                    reduceMotion = reduceMotion,
                    favoriteKeys = favoriteKeys,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier.padding(horizontal = CpDimens.screenPadding),
                )
            }

            item(key = "home-categories", contentType = "home-categories") {
                Spacer(Modifier.height(18.dp))
                val labels = listOf("精选") + categories.take(5).map(DirectJmCategory::name)
                PillRow(
                    labels = labels,
                    selectedIndex = 0,
                    onSelected = { index ->
                        categories.take(5).getOrNull(index - 1)?.let(onOpenCategory)
                    },
                )
            }

            item(key = "home-editor-picks", contentType = "home-shelf") {
                HomeShelf(
                    title = "编辑精选",
                    items = comics.take(8),
                    onOpen = onResolve,
                    onMore = onOpenBrowse,
                    topPadding = 28.dp,
                    favoriteKeys = favoriteKeys,
                    onToggleFavorite = onToggleFavorite,
                )
            }

            item(key = "home-updates", contentType = "home-grid") {
                TodayUpdates(
                    items = (comics.drop(3) + comics.take(3)).distinctBy { it.key }.take(6),
                    onOpen = onResolve,
                    onMore = onOpenBrowse,
                    favoriteKeys = favoriteKeys,
                    onToggleFavorite = onToggleFavorite,
                )
            }

            item(key = "home-ranking", contentType = "home-ranking") {
                HotRanking(
                    items = comics,
                    onOpen = onResolve,
                    onMore = onOpenBrowse,
                    favoriteKeys = favoriteKeys,
                    onToggleFavorite = onToggleFavorite,
                )
            }

            item(key = "home-recommendations", contentType = "home-shelf") {
                HomeShelf(
                    title = "猜你喜欢",
                    items = comics.reversed().take(8),
                    onOpen = onResolve,
                    onMore = onOpenBrowse,
                    topPadding = CpDimens.sectionGap,
                    favoriteKeys = favoriteKeys,
                    onToggleFavorite = onToggleFavorite,
                )
            }

            item(key = "home-discovery-title", contentType = "home-section-title") {
                SectionTitle(
                    title = "持续发现",
                    modifier = Modifier.padding(horizontal = CpDimens.screenPadding, vertical = CpDimens.sectionGap),
                )
            }

            itemsIndexed(
                discoveryItems,
                key = { _, comic -> "discover-${comic.key}" },
                contentType = { _, _ -> "home-discovery-row" },
            ) { index, comic ->
                RankingRow(
                    rank = comics.size + index + 1,
                    comic = comic,
                    onClick = { onResolve(comic) },
                    prominent = false,
                    modifier = Modifier.padding(horizontal = CpDimens.screenPadding),
                    isFavorite = comic.key in favoriteKeys,
                    onToggleFavorite = { onToggleFavorite(comic) },
                )
            }

            if (discoveryLoading) {
                item(key = "home-discovery-loading", contentType = "loading") {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
            }

            item(key = "home-footer", contentType = "home-footer") {
                Text(
                    text = "永久免费 · 无收费入口 · 有能力请支持 JM 官方",
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 8.dp),
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HomeSkeleton(reduceMotion: Boolean) {
    val brush = rememberShimmerBrush(animated = !reduceMotion)
    Column(Modifier.padding(horizontal = CpDimens.screenPadding, vertical = 18.dp)) {
        ShimmerBlock(
            brush = brush,
            modifier = Modifier.fillMaxWidth().height(224.dp),
            shape = RoundedCornerShape(CpDimens.heroRadius),
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) { index ->
                ShimmerBlock(
                    brush = brush,
                    modifier = Modifier.width(if (index == 0) 68.dp else 58.dp).height(32.dp),
                    shape = CircleShape,
                )
            }
        }
        Spacer(Modifier.height(30.dp))
        ShimmerBlock(brush, Modifier.width(92.dp).height(20.dp), RoundedCornerShape(8.dp))
        Spacer(Modifier.height(15.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            repeat(3) {
                Column(Modifier.weight(1f)) {
                    ShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth().height(154.dp),
                        shape = RoundedCornerShape(CpDimens.cardRadius),
                    )
                    Spacer(Modifier.height(9.dp))
                    ShimmerBlock(brush, Modifier.fillMaxWidth(.86f).height(14.dp), RoundedCornerShape(7.dp))
                }
            }
        }
    }
}

@Composable
private fun FeaturedCarousel(
    items: List<ComicUiItem>,
    onOpen: (ComicUiItem) -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    favoriteKeys: Set<String> = emptySet(),
    onToggleFavorite: (ComicUiItem) -> Unit = {},
) {
    val actualItems = items.ifEmpty {
        listOf(ComicUiItem("0", "正在连接漫画目录", "精选", "", 0))
    }
    var index by rememberSaveable(actualItems.map { it.jmId }.joinToString()) { mutableIntStateOf(0) }
    val motionAllowed = rememberMotionAllowed() && !reduceMotion
    LaunchedEffect(actualItems.size, motionAllowed) {
        if (!motionAllowed || actualItems.size < 2) return@LaunchedEffect
        while (true) {
            delay(5_200)
            index = (index + 1) % actualItems.size
        }
    }
    val current = actualItems[index.coerceIn(actualItems.indices)]
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Column(modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(224.dp)
                .pressFeedback(interactionSource, pressedScale = .992f)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                ) {
                    if (current.jmId != "0") onOpen(current)
                },
            shape = RoundedCornerShape(CpDimens.heroRadius),
            shadowElevation = if (pressed) 0.dp else 2.dp,
            color = SurfaceSoft,
        ) {
            Box(Modifier.fillMaxSize()) {
                    ComicCover(current.coverUrl, current.title, current.accentIndex, Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                .48f to Color.Transparent,
                                1f to Color(0xD9121926),
                            ),
                        ),
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                        Text(
                            current.title,
                            color = White,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            current.supportingLabel(preferMetric = true),
                            color = White.copy(alpha = .78f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (current.jmId != "0") {
                        com.comicplus.app.ui.components.FavoriteButton(
                            isFavorite = current.key in favoriteKeys,
                            onClick = { onToggleFavorite(current) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                            favoriteKey = current.key,
                        )
                    }
            }
        }
        if (actualItems.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                actualItems.forEachIndexed { dotIndex, _ ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = if (dotIndex == index) 18.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (dotIndex == index) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeShelf(
    title: String,
    items: List<ComicUiItem>,
    onOpen: (ComicUiItem) -> Unit,
    onMore: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
    favoriteKeys: Set<String>,
    onToggleFavorite: (ComicUiItem) -> Unit,
) {
    Column(Modifier.padding(top = topPadding)) {
        SectionTitle(
            title = title,
            onMore = onMore,
            modifier = Modifier.padding(horizontal = CpDimens.screenPadding),
        )
        Spacer(Modifier.height(15.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = CpDimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            items(items, key = ComicUiItem::key, contentType = { "home-comic-poster" }) { comic ->
                ComicPosterCard(
                    comic = comic,
                    onClick = { onOpen(comic) },
                    modifier = Modifier.width(132.dp),
                    isFavorite = comic.key in favoriteKeys,
                    onToggleFavorite = { onToggleFavorite(comic) },
                )
            }
        }
    }
}

@Composable
private fun TodayUpdates(
    items: List<ComicUiItem>,
    onOpen: (ComicUiItem) -> Unit,
    onMore: () -> Unit,
    favoriteKeys: Set<String>,
    onToggleFavorite: (ComicUiItem) -> Unit,
) {
    Column(Modifier.padding(horizontal = CpDimens.screenPadding, vertical = CpDimens.sectionGap)) {
        SectionTitle(title = "今日更新", onMore = onMore)
        Spacer(Modifier.height(15.dp))
        items.chunked(2).forEachIndexed { rowIndex, rowItems ->
            if (rowIndex > 0) Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                rowItems.forEach { comic ->
                    ComicPosterCard(
                        comic = comic,
                        onClick = { onOpen(comic) },
                        modifier = Modifier.weight(1f),
                        isFavorite = comic.key in favoriteKeys,
                        onToggleFavorite = { onToggleFavorite(comic) },
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HotRanking(
    items: List<ComicUiItem>,
    onOpen: (ComicUiItem) -> Unit,
    onMore: () -> Unit,
    favoriteKeys: Set<String>,
    onToggleFavorite: (ComicUiItem) -> Unit,
) {
    Column(Modifier.padding(horizontal = CpDimens.screenPadding)) {
        SectionTitle(title = "热门排行", onMore = onMore)
        Spacer(Modifier.height(13.dp))
        if (items.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                color = SurfaceSoft,
                shape = RoundedCornerShape(CpDimens.controlRadius),
            ) {
                Text(
                    "JM 官方排行暂时没有可展示内容。",
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 18.dp),
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            items.take(5).forEachIndexed { index, comic ->
                RankingRow(
                    rank = index + 1,
                    comic = comic,
                    onClick = { onOpen(comic) },
                    isFavorite = comic.key in favoriteKeys,
                    onToggleFavorite = { onToggleFavorite(comic) },
                )
            }
        }
    }
}

