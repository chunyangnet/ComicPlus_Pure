package com.comicplus.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comicplus.app.search.JmIdParser
import com.comicplus.app.data.source.SourceIds
import com.comicplus.app.ui.ComicUiItem
import com.comicplus.app.ui.JmSearchUiState
import com.comicplus.app.ui.key
import com.comicplus.app.ui.components.CpDimens
import com.comicplus.app.ui.components.PillRow
import com.comicplus.app.ui.components.RankingRow
import com.comicplus.app.ui.components.SearchCapsule
import com.comicplus.app.ui.components.ShimmerBlock
import com.comicplus.app.ui.components.rememberShimmerBrush
import com.comicplus.app.ui.theme.Canvas
import com.comicplus.app.ui.theme.Ink
import com.comicplus.app.ui.theme.InkSoft
import com.comicplus.app.ui.theme.Muted
import com.comicplus.app.ui.theme.SurfaceSoft
import com.comicplus.app.ui.theme.White

private data class JmSearchScope(val id: Int, val label: String)
private data class JmSearchOrder(val id: String, val label: String)

private val jmSearchScopes = listOf(
    JmSearchScope(0, "综合"), JmSearchScope(1, "作品"), JmSearchScope(2, "作者"),
    JmSearchScope(3, "标签"), JmSearchScope(4, "角色"),
)
private val jmSearchOrders = listOf(
    JmSearchOrder("mr", "最新"), JmSearchOrder("mv", "最多点击"),
    JmSearchOrder("mp", "最多图片"), JmSearchOrder("tf", "最多爱心"),
)

@Composable
fun SearchScreen(
    state: JmSearchUiState,
    initialQuery: String,
    initialScope: Int,
    reduceMotion: Boolean,
    onBack: () -> Unit,
    onSearch: (String, Int, String) -> Unit,
    onLoadMore: () -> Unit,
    onResolve: (ComicUiItem) -> Unit,
    onRedirectConsumed: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    favoriteKeys: Set<String> = emptySet(),
    onToggleFavorite: (ComicUiItem) -> Unit = {},
) {
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    var scopeId by rememberSaveable(initialQuery, initialScope) { mutableIntStateOf(initialScope.coerceIn(0, 4)) }
    var orderId by rememberSaveable(initialQuery) { mutableStateOf(state.order) }
    val parsed = remember(query) { JmIdParser.parse(query) }
    BackHandler(onBack = onBack)
    LaunchedEffect(state.redirectAid) {
        state.redirectAid?.let { id ->
            onRedirectConsumed()
            onResolve(ComicUiItem(id, "JM$id", "", "", 0, source = SourceIds.Jm))
        }
    }

    Column(modifier.fillMaxSize().background(Canvas).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Ink)
            }
            Column(Modifier.weight(1f)) {
                Text("JM 官方搜索", color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("JM 官方接口 · 原生筛选与搜索语法", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        SearchCapsule(
            query = query,
            onQueryChange = { query = it.take(160) },
            onSearch = {
                parsed.sourceId?.let { id -> onResolve(ComicUiItem(id, "JM$id", "", "", 0, source = SourceIds.Jm)) }
                    ?: if (query.isBlank()) onMessage("输入作品、作者、标签或 JM ID")
                else onSearch(query, scopeId, orderId)
            },
            hint = parsed.sourceId?.let { "JM$it · 直接打开" },
            placeholder = "搜索漫画或输入 JM ID",
            modifier = Modifier.padding(horizontal = CpDimens.screenPadding),
        )
        Spacer(Modifier.height(13.dp))
        PillRow(
            labels = jmSearchScopes.map(JmSearchScope::label),
            selectedIndex = jmSearchScopes.indexOfFirst { it.id == scopeId }.coerceAtLeast(0),
            onSelected = { index ->
                scopeId = jmSearchScopes.getOrNull(index)?.id ?: return@PillRow
                if (state.submitted && query.isNotBlank()) onSearch(query, scopeId, orderId)
            },
        )
        Spacer(Modifier.height(8.dp))
        PillRow(
            labels = jmSearchOrders.map(JmSearchOrder::label),
            selectedIndex = jmSearchOrders.indexOfFirst { it.id == orderId }.coerceAtLeast(0),
            onSelected = { index ->
                orderId = jmSearchOrders.getOrNull(index)?.id ?: return@PillRow
                if (state.submitted && query.isNotBlank()) onSearch(query, scopeId, orderId)
            },
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f)) {
            when {
                state.loading && state.items.isEmpty() -> SearchSkeleton(reduceMotion)
                !state.submitted -> SearchLanding(
                    onExample = { example -> query = example; onSearch(example, scopeId, orderId) },
                )
                else -> SearchResultList(
                    state = state,
                    onOpen = onResolve,
                    onLoadMore = onLoadMore,
                    favoriteKeys = favoriteKeys,
                    onToggleFavorite = onToggleFavorite,
                    onRetry = {
                        if (state.page <= 0) onSearch(query, scopeId, orderId) else onLoadMore()
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchLanding(onExample: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = CpDimens.screenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("syntax") {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(CpDimens.cardRadius)) {
                Column(Modifier.padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(19.dp))
                        }
                        Spacer(Modifier.width(9.dp))
                        Text("官方搜索语法", color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "空格连接多个关键词；+ 表示必须包含，- 表示排除。输入 JM 车号会直接打开详情。",
                        color = InkSoft,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item("examples") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全彩 +人妻", "全彩 -人妻", "MANA 神里", "JM123456").forEach { example ->
                    val interaction = remember(example) { MutableInteractionSource() }
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable(interactionSource = interaction, indication = LocalIndication.current) { onExample(example) },
                        color = SurfaceSoft,
                        shape = RoundedCornerShape(CpDimens.controlRadius),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(example, color = Ink, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultList(
    state: JmSearchUiState,
    onOpen: (ComicUiItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    favoriteKeys: Set<String>,
    onToggleFavorite: (ComicUiItem) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentCanLoadMore = rememberUpdatedState(
        state.items.isNotEmpty() && state.hasMore && !state.loadingMore && state.error == null,
    )
    val currentOnLoadMore = rememberUpdatedState(onLoadMore)
    // Loading and item-count changes must not restart this collector: if the
    // viewport is still near the end, a restart would immediately chain pages.
    LaunchedEffect(listState, state.query, state.mainTag, state.order) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 5
        }.collect { nearEnd ->
            if (nearEnd && currentCanLoadMore.value) currentOnLoadMore.value()
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = CpDimens.screenPadding, vertical = 4.dp),
    ) {
        item("summary", contentType = "summary") {
            Surface(color = SurfaceSoft, shape = RoundedCornerShape(CpDimens.controlRadius), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(
                    if (state.items.isEmpty()) "没有找到“${state.query}”" else "找到 ${state.total} 个结果 · 已加载 ${state.items.size}",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    color = if (state.error == null) InkSoft else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        itemsIndexed(state.items, key = { _, item -> item.key }, contentType = { _, _ -> "jm-search-row" }) { index, comic ->
			RankingRow(
				rank = index + 1,
				comic = comic,
				onClick = { onOpen(comic) },
				prominent = false,
				supportingText = listOfNotNull(
					comic.subtitle.takeIf(String::isNotBlank),
					comic.metric.takeIf(String::isNotBlank),
				).joinToString("  ·  "),
				isFavorite = comic.key in favoriteKeys,
				onToggleFavorite = { onToggleFavorite(comic) },
			)
        }
        if (state.loadingMore) item("loading-more") {
            Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
        }
        state.error?.let { message ->
            item("error") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onRetry, shape = RoundedCornerShape(8.dp)) { Text("重新加载") }
                }
            }
        }
        item("bottom-space") { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun SearchSkeleton(reduceMotion: Boolean) {
    val brush = rememberShimmerBrush(animated = !reduceMotion)
    Column(Modifier.padding(horizontal = CpDimens.screenPadding, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(7) { ShimmerBlock(brush, Modifier.fillMaxWidth().height(82.dp), RoundedCornerShape(CpDimens.controlRadius)) }
    }
}

