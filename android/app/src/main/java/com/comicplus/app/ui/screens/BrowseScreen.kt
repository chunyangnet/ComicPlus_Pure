package com.comicplus.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.comicplus.app.ui.icons.ComicPlusIcons as Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicplus.app.data.source.DirectJmCategory
import com.comicplus.app.search.JmIdParser
import com.comicplus.app.ui.CategoryUiState
import com.comicplus.app.ui.ComicPlusTestTags
import com.comicplus.app.ui.ComicUiItem
import com.comicplus.app.ui.RankingsUiState
import com.comicplus.app.ui.comicPlusDeviceTestTag
import com.comicplus.app.ui.components.ComicCover
import com.comicplus.app.ui.components.ComicPlusTopBar
import com.comicplus.app.ui.components.CpDimens
import com.comicplus.app.ui.components.AppBarAction
import com.comicplus.app.ui.components.PillRow
import com.comicplus.app.ui.components.RankingRow
import com.comicplus.app.ui.components.SearchCapsule
import com.comicplus.app.ui.components.ShimmerBlock
import com.comicplus.app.ui.components.rememberShimmerBrush
import com.comicplus.app.ui.key
import com.comicplus.app.ui.theme.Ink
import com.comicplus.app.ui.theme.InkSoft
import com.comicplus.app.ui.theme.Muted
import com.comicplus.app.ui.theme.SurfaceSoft

@Composable
fun CategoryScreen(
    categories: List<DirectJmCategory>,
    state: CategoryUiState,
    reduceMotion: Boolean,
    onEnsureCategories: () -> Unit,
    onSelectCategory: (DirectJmCategory) -> Unit,
    onSelectOrder: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onResolve: (ComicUiItem) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    favoriteKeys: Set<String> = emptySet(),
    onToggleFavorite: (ComicUiItem) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { onEnsureCategories() }
    LaunchedEffect(categories.isNotEmpty(), state.page, state.loading, state.error) {
        if (categories.isNotEmpty() && state.page == 0 && !state.loading && state.error == null) {
            onSelectOrder(state.order)
        }
    }

    Column(modifier.fillMaxSize().comicPlusDeviceTestTag(ComicPlusTestTags.BrowseScreen)) {
        ComicPlusTopBar(title = "分类", subtitle = "按题材浏览 JM 目录", actions = emptyList())
        BrowseSearch(
            query = query,
            onQueryChange = { query = it; onClearSearch() },
            onOpenSearch = onOpenSearch,
            onResolve = onResolve,
            onMessage = onMessage,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxSize()) {
            CategoryRail(
                categories = categories,
                selectedSlug = state.selectedSlug,
                onSelect = { category ->
                    if (category.id == "0" || category.type == "slug" || category.type.isBlank()) {
                        onSelectCategory(category)
                    } else {
                        onOpenSearch(category.name)
                    }
                },
            )
            Column(Modifier.weight(1f).fillMaxSize()) {
                val selectedOrder = categoryOrders.firstOrNull { it.id == state.order } ?: categoryOrders.first()
                PillRow(
                    labels = categoryOrders.map(BrowseOrder::label),
                    selectedIndex = categoryOrders.indexOf(selectedOrder),
                    onSelected = { index -> categoryOrders.getOrNull(index)?.let { onSelectOrder(it.id) } },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                )
                Spacer(Modifier.height(12.dp))
                CategoryGrid(
                    state = state,
                    reduceMotion = reduceMotion,
                    onLoadMore = onLoadMore,
                    onRetry = {
                        if (state.page == 0) onSelectOrder(state.order) else onLoadMore()
                    },
                    onOpen = onResolve,
                    favoriteKeys = favoriteKeys,
                    onToggleFavorite = onToggleFavorite,
                )
            }
        }
    }
}

@Composable
fun RankingScreen(
    state: RankingsUiState,
    reduceMotion: Boolean,
    onEnsureRankings: () -> Unit,
    onSelectOrder: (String) -> Unit,
    onOpenOfficialBrowse: () -> Unit,
    onOpenSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onResolve: (ComicUiItem) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    favoriteKeys: Set<String> = emptySet(),
    onToggleFavorite: (ComicUiItem) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    val order = rankingOrders.firstOrNull { it.id == state.jmOrder } ?: rankingOrders.first()
    LaunchedEffect(Unit) { onEnsureRankings() }

    Column(modifier.fillMaxSize().comicPlusDeviceTestTag(ComicPlusTestTags.BrowseScreen)) {
        ComicPlusTopBar(
            title = "排行",
            subtitle = "JM 热度榜单",
            actions = listOf(
                AppBarAction(
                    icon = Icons.Outlined.Explore,
                    label = "官方目录",
                    prominent = true,
                    onClick = onOpenOfficialBrowse,
                ),
            ),
        )
        BrowseSearch(
            query = query,
            onQueryChange = { query = it; onClearSearch() },
            onOpenSearch = onOpenSearch,
            onResolve = onResolve,
            onMessage = onMessage,
        )
        Spacer(Modifier.height(14.dp))
        PillRow(
            labels = rankingOrders.map(BrowseOrder::label),
            selectedIndex = rankingOrders.indexOf(order),
            onSelected = { index -> rankingOrders.getOrNull(index)?.let { onSelectOrder(it.id) } },
        )
        Spacer(Modifier.height(8.dp))
        AnimatedContent(
            targetState = order.id,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val duration = if (reduceMotion) 70 else 180
                if (reduceMotion) fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                else (slideInHorizontally(tween(duration)) { it / 18 } + fadeIn(tween(duration))) togetherWith
                    (slideOutHorizontally(tween(duration)) { -it / 22 } + fadeOut(tween(duration - 30)))
            },
            label = "ranking-order-transition",
        ) { targetOrderId ->
            val targetOrder = rankingOrders.firstOrNull { it.id == targetOrderId } ?: rankingOrders.first()
            when {
                state.jmLoading && state.jmItems.isEmpty() -> RankingListSkeleton(reduceMotion)
                state.jmItems.isEmpty() -> EmptyState(
                    text = state.jmError?.let { "${targetOrder.label}暂时无法更新" } ?: "${targetOrder.label}暂无内容",
                    action = "重新加载",
                    onAction = onEnsureRankings,
                )
                else -> RankingList(state.jmItems, onResolve, favoriteKeys, onToggleFavorite)
            }
        }
    }
}

@Composable
private fun BrowseSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: (String) -> Unit,
    onResolve: (ComicUiItem) -> Unit,
    onMessage: (String) -> Unit,
) {
    val searchIntent = remember(query) { JmIdParser.parse(query) }
    SearchCapsule(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = {
            searchIntent.sourceId?.let { id -> onResolve(ComicUiItem(id, "JM$id", "", "", 0)) }
                ?: if (query.isBlank()) onMessage("输入漫画名或 JM ID") else onOpenSearch(query)
        },
        hint = searchIntent.sourceId?.let { "JM$it" },
        placeholder = "搜索漫画或输入 JM ID",
        modifier = Modifier.padding(horizontal = CpDimens.screenPadding),
    )
}

@Composable
private fun CategoryRail(
    categories: List<DirectJmCategory>,
    selectedSlug: String,
    onSelect: (DirectJmCategory) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.width(96.dp).fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
    ) {
        items(categories, key = DirectJmCategory::slug, contentType = { "category" }) { category ->
            val selected = category.slug == selectedSlug
            val interactionSource = remember(category.slug) { MutableInteractionSource() }
            Surface(
                modifier = Modifier.padding(start = 10.dp, end = 7.dp, bottom = 5.dp).fillMaxWidth()
                    .clickable(interactionSource, LocalIndication.current) { onSelect(category) },
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 11.dp)) {
                    Text(
                        category.name,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else InkSoft,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    category.totalAlbums?.let {
                        Text(compactTotal(it), color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryGrid(
    state: CategoryUiState,
    reduceMotion: Boolean,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (ComicUiItem) -> Unit,
    favoriteKeys: Set<String>,
    onToggleFavorite: (ComicUiItem) -> Unit,
) {
    if (state.loading && state.items.isEmpty()) {
        CategoryGridSkeleton(reduceMotion)
        return
    }
    if (state.items.isEmpty()) {
        EmptyState(
            text = state.error?.let { "当前分类加载失败" } ?: "当前分类暂无内容",
            action = "重新加载",
            onAction = onRetry,
        )
        return
    }
    val gridState = rememberLazyGridState()
    val currentCanLoadMore = rememberUpdatedState(
        state.hasMore && !state.loadingMore && state.error == null,
    )
    val currentOnLoadMore = rememberUpdatedState(onLoadMore)
    // Keep page-state changes out of the effect keys so completing one page
    // cannot restart the collector and automatically drain following pages.
    LaunchedEffect(gridState, state.selectedSlug, state.order) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 6
        }.collect { nearEnd ->
            if (nearEnd && currentCanLoadMore.value) currentOnLoadMore.value()
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(92.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = CpDimens.screenPadding, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        items(state.items, key = ComicUiItem::key, contentType = { "category-comic" }) { comic ->
            CategoryComicCard(
                comic = comic,
                onClick = { onOpen(comic) },
                isFavorite = comic.key in favoriteKeys,
                onToggleFavorite = { onToggleFavorite(comic) },
            )
        }
        if (state.loadingMore || state.error != null || !state.hasMore) {
            item(key = "category-tail", span = { GridItemSpan(maxLineSpan) }) {
                CategoryTail(state, onRetry)
            }
        }
    }
}

@Composable
private fun CategoryTail(state: CategoryUiState, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        when {
            state.loadingMore -> Text("正在加载更多", color = Muted, style = MaterialTheme.typography.bodySmall)
            state.error != null -> Button(
                onClick = onRetry,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceSoft, contentColor = InkSoft),
            ) { Text("加载失败，点击重试") }
            else -> Text("已经到底了", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CategoryComicCard(
    comic: ComicUiItem,
    onClick: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    val interactionSource = remember(comic.key) { MutableInteractionSource() }
    Column(Modifier.fillMaxWidth().clickable(interactionSource, LocalIndication.current, onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(CpDimens.cardRadius)),
        ) {
            ComicCover(comic.coverUrl, comic.title, comic.accentIndex, Modifier.fillMaxSize())
            com.comicplus.app.ui.components.FavoriteButton(
                isFavorite = isFavorite,
                onClick = onToggleFavorite,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                compact = true,
                favoriteKey = comic.key,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            comic.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = Ink,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun RankingList(
    items: List<ComicUiItem>,
    onOpen: (ComicUiItem) -> Unit,
    favoriteKeys: Set<String>,
    onToggleFavorite: (ComicUiItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = CpDimens.screenPadding, vertical = 4.dp),
    ) {
        itemsIndexed(items.take(20), key = { _, item -> item.key }, contentType = { _, _ -> "ranking-row" }) { index, comic ->
            RankingRow(
                rank = index + 1,
                comic = comic,
                onClick = { onOpen(comic) },
                prominent = index < 3,
                isFavorite = comic.key in favoriteKeys,
                onToggleFavorite = { onToggleFavorite(comic) },
            )
            if (index >= 3 && index < minOf(items.size, 20) - 1) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 44.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
}

@Composable
private fun CategoryGridSkeleton(reduceMotion: Boolean) {
    val brush = rememberShimmerBrush(animated = !reduceMotion)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(92.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = CpDimens.screenPadding, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        items(12, contentType = { "category-skeleton" }) { index ->
            Column {
                ShimmerBlock(brush, Modifier.fillMaxWidth().aspectRatio(3f / 4f), RoundedCornerShape(CpDimens.cardRadius))
                Spacer(Modifier.height(8.dp))
                ShimmerBlock(brush, Modifier.fillMaxWidth(if (index % 3 == 0) .72f else .9f).height(13.dp), RoundedCornerShape(7.dp))
            }
        }
    }
}

@Composable
private fun RankingListSkeleton(reduceMotion: Boolean) {
    val brush = rememberShimmerBrush(animated = !reduceMotion)
    Column(Modifier.padding(horizontal = CpDimens.screenPadding, vertical = 4.dp)) {
        repeat(7) { index ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                ShimmerBlock(brush, Modifier.width(30.dp).height(18.dp), RoundedCornerShape(7.dp))
                Spacer(Modifier.width(10.dp))
                ShimmerBlock(brush, Modifier.width(if (index < 3) 70.dp else 52.dp).aspectRatio(3f / 4f), RoundedCornerShape(8.dp))
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    ShimmerBlock(brush, Modifier.fillMaxWidth(.78f).height(15.dp), RoundedCornerShape(7.dp))
                    Spacer(Modifier.height(9.dp))
                    ShimmerBlock(brush, Modifier.fillMaxWidth(.52f).height(12.dp), RoundedCornerShape(6.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String, action: String, onAction: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CpDimens.screenPadding, vertical = 10.dp),
        color = SurfaceSoft,
        shape = RoundedCornerShape(CpDimens.controlRadius),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(8.dp)) { Text(action) }
        }
    }
}

private data class BrowseOrder(val id: String, val label: String)

private val categoryOrders = listOf(
    BrowseOrder("mr", "最新"),
    BrowseOrder("mv", "热度"),
    BrowseOrder("mp", "最多图片"),
    BrowseOrder("tf", "最多收藏"),
)

private val rankingOrders = listOf(
    BrowseOrder("mv", "总榜"),
    BrowseOrder("mv_m", "月榜"),
    BrowseOrder("mv_w", "周榜"),
    BrowseOrder("mv_t", "日榜"),
    BrowseOrder("tf", "收藏榜"),
)

private fun compactTotal(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(java.util.Locale.US, value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(java.util.Locale.US, value / 1_000.0)
    else -> value.toString()
}
