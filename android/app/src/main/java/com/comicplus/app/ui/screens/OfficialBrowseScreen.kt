package com.comicplus.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import com.comicplus.app.ui.icons.ComicPlusIcons as Icons
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.comicplus.app.data.source.DirectJmCategory
import com.comicplus.app.ui.ComicUiItem
import com.comicplus.app.ui.key
import com.comicplus.app.ui.JmOfficialBrowseUiState
import com.comicplus.app.ui.JmTypeRankingUiState
import com.comicplus.app.ui.JmWeeklyUiState
import com.comicplus.app.ui.components.ComicPlusTopBar
import com.comicplus.app.ui.components.CpDimens
import com.comicplus.app.ui.components.PillRow
import com.comicplus.app.ui.components.RankingRow
import com.comicplus.app.ui.components.ShimmerBlock
import com.comicplus.app.ui.components.rememberShimmerBrush
import com.comicplus.app.ui.theme.Canvas
import com.comicplus.app.ui.theme.Ink
import com.comicplus.app.ui.theme.InkSoft
import com.comicplus.app.ui.theme.Muted
import com.comicplus.app.ui.theme.SurfaceSoft

private enum class OfficialBrowseSection(val label: String) {
    Weekly("每周必看"),
    Tags("标签分类"),
    TypeRanking("分类排行"),
}

private data class OfficialRankingOrder(val id: String, val label: String)

private val officialRankingOrders = listOf(
    OfficialRankingOrder("mv", "总榜"),
    OfficialRankingOrder("mv_m", "月榜"),
    OfficialRankingOrder("mv_w", "周榜"),
    OfficialRankingOrder("mv_t", "日榜"),
)

@Composable
fun OfficialBrowseScreen(
    state: JmOfficialBrowseUiState,
    categories: List<DirectJmCategory>,
    reduceMotion: Boolean,
    onBack: () -> Unit,
    onEnsure: () -> Unit,
    onSelectWeeklyCategory: (String) -> Unit,
    onSelectWeeklyType: (String) -> Unit,
    onRetryWeekly: () -> Unit,
    onOpenTag: (String) -> Unit,
    onSelectTypeRanking: (String, String) -> Unit,
    onRetryTypeRanking: () -> Unit,
    onResolve: (ComicUiItem) -> Unit,
    modifier: Modifier = Modifier,
    favoriteKeys: Set<String> = emptySet(),
    onToggleFavorite: (ComicUiItem) -> Unit = {},
) {
    var sectionName by rememberSaveable { mutableStateOf(OfficialBrowseSection.Weekly.name) }
    val section = OfficialBrowseSection.entries.firstOrNull { it.name == sectionName }
        ?: OfficialBrowseSection.Weekly
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { onEnsure() }

    Column(modifier.fillMaxSize().background(Canvas).navigationBarsPadding()) {
        ComicPlusTopBar(
            title = "JM 官方目录",
            subtitle = section.label,
            actions = emptyList(),
            leading = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Ink)
                }
            },
        )
        PillRow(
            labels = OfficialBrowseSection.entries.map(OfficialBrowseSection::label),
            selectedIndex = OfficialBrowseSection.entries.indexOf(section),
            onSelected = { index ->
                OfficialBrowseSection.entries.getOrNull(index)?.let { sectionName = it.name }
            },
        )
        Spacer(Modifier.height(10.dp))
        AnimatedContent(
            targetState = section,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val duration = if (reduceMotion) 70 else 170
                fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
            },
            label = "official-browse-section",
        ) { target ->
            when (target) {
                OfficialBrowseSection.Weekly -> WeeklyBrowse(
                    state = state.weekly,
                    reduceMotion = reduceMotion,
                    onSelectCategory = onSelectWeeklyCategory,
                    onSelectType = onSelectWeeklyType,
                    onRetry = onRetryWeekly,
                    onOpen = onResolve,
                    favoriteKeys = favoriteKeys,
                    onToggleFavorite = onToggleFavorite,
                )
                OfficialBrowseSection.Tags -> TagBrowse(
                    state = state,
                    reduceMotion = reduceMotion,
                    onOpenTag = onOpenTag,
                )
                OfficialBrowseSection.TypeRanking -> TypeRankingBrowse(
                    state = state.typeRanking,
                    categories = categories,
                    reduceMotion = reduceMotion,
                    onSelect = onSelectTypeRanking,
                    onRetry = onRetryTypeRanking,
                    onOpen = onResolve,
                    favoriteKeys = favoriteKeys,
                    onToggleFavorite = onToggleFavorite,
                )
            }
        }
    }
}

@Composable
private fun WeeklyBrowse(
    state: JmWeeklyUiState,
    reduceMotion: Boolean,
    onSelectCategory: (String) -> Unit,
    onSelectType: (String) -> Unit,
    onRetry: () -> Unit,
    onOpen: (ComicUiItem) -> Unit,
    favoriteKeys: Set<String>,
    onToggleFavorite: (ComicUiItem) -> Unit,
) {
    if (state.catalogLoading && state.categories.isEmpty()) {
        OfficialListSkeleton(reduceMotion)
        return
    }
    if (state.categories.isEmpty() || state.types.isEmpty()) {
        OfficialEmptyState(state.error ?: "每周必看目录暂不可用", onRetry)
        return
    }
    val category = state.categories.firstOrNull { it.id == state.selectedCategoryId } ?: state.categories.first()
    val type = state.types.firstOrNull { it.id == state.selectedTypeId } ?: state.types.first()
    Column(Modifier.fillMaxSize()) {
        PillRow(
            labels = state.categories.map { it.title },
            selectedIndex = state.categories.indexOf(category),
            onSelected = { index -> state.categories.getOrNull(index)?.let { onSelectCategory(it.id) } },
        )
        Spacer(Modifier.height(7.dp))
        PillRow(
            labels = state.types.map { it.title },
            selectedIndex = state.types.indexOf(type),
            onSelected = { index -> state.types.getOrNull(index)?.let { onSelectType(it.id) } },
        )
        Spacer(Modifier.height(8.dp))
        OfficialRankingResults(
            items = state.items,
            total = state.total,
            loading = state.loading,
            error = state.error,
            reduceMotion = reduceMotion,
            emptyText = "当前每周必看暂无内容",
            onRetry = onRetry,
            onOpen = onOpen,
            favoriteKeys = favoriteKeys,
            onToggleFavorite = onToggleFavorite,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagBrowse(
    state: JmOfficialBrowseUiState,
    reduceMotion: Boolean,
    onOpenTag: (String) -> Unit,
) {
    when {
        state.catalogLoading && state.tagGroups.isEmpty() -> OfficialListSkeleton(reduceMotion)
        state.tagGroups.isEmpty() -> OfficialEmptyState("官方标签目录暂不可用", null)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = CpDimens.screenPadding, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(state.tagGroups, key = { it.title }, contentType = { "tag-group" }) { group ->
                Column {
                    Text(group.title, color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(9.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        group.tags.forEach { tag ->
                            val interaction = remember(tag) { MutableInteractionSource() }
                            Surface(
                                modifier = Modifier.clickable(
                                    interactionSource = interaction,
                                    indication = LocalIndication.current,
                                ) { onOpenTag(tag) },
                                shape = RoundedCornerShape(8.dp),
                                color = SurfaceSoft,
                            ) {
                                Text(
                                    tag,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                    color = InkSoft,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
            item("tag-bottom-space") { Spacer(Modifier.height(22.dp)) }
        }
    }
}

@Composable
private fun TypeRankingBrowse(
    state: JmTypeRankingUiState,
    categories: List<DirectJmCategory>,
    reduceMotion: Boolean,
    onSelect: (String, String) -> Unit,
    onRetry: () -> Unit,
    onOpen: (ComicUiItem) -> Unit,
    favoriteKeys: Set<String>,
    onToggleFavorite: (ComicUiItem) -> Unit,
) {
    val rankableCategories = categories.filter { it.slug != "0" && (it.type == "slug" || it.type.isBlank()) }
    if (rankableCategories.isEmpty()) {
        OfficialEmptyState("分类目录暂不可用", onRetry)
        return
    }
    val category = rankableCategories.firstOrNull { it.slug == state.selectedSlug } ?: rankableCategories.first()
    val order = officialRankingOrders.firstOrNull { it.id == state.order } ?: officialRankingOrders.first()
    LaunchedEffect(category.slug, state.selectedSlug) {
        if (category.slug != state.selectedSlug) onSelect(category.slug, state.order)
    }
    Column(Modifier.fillMaxSize()) {
        PillRow(
            labels = rankableCategories.map { it.name },
            selectedIndex = rankableCategories.indexOf(category),
            onSelected = { index ->
                rankableCategories.getOrNull(index)?.let { onSelect(it.slug, state.order) }
            },
        )
        Spacer(Modifier.height(7.dp))
        PillRow(
            labels = officialRankingOrders.map(OfficialRankingOrder::label),
            selectedIndex = officialRankingOrders.indexOf(order),
            onSelected = { index ->
                officialRankingOrders.getOrNull(index)?.let { onSelect(category.slug, it.id) }
            },
        )
        Spacer(Modifier.height(8.dp))
        OfficialRankingResults(
            items = state.items,
            total = state.total,
            loading = state.loading,
            error = state.error,
            reduceMotion = reduceMotion,
            emptyText = "当前分类榜单暂无内容",
            onRetry = onRetry,
            onOpen = onOpen,
            favoriteKeys = favoriteKeys,
            onToggleFavorite = onToggleFavorite,
        )
    }
}

@Composable
private fun OfficialRankingResults(
    items: List<ComicUiItem>,
    total: Long,
    loading: Boolean,
    error: String?,
    reduceMotion: Boolean,
    emptyText: String,
    onRetry: () -> Unit,
    onOpen: (ComicUiItem) -> Unit,
    favoriteKeys: Set<String>,
    onToggleFavorite: (ComicUiItem) -> Unit,
) {
    when {
        loading && items.isEmpty() -> OfficialListSkeleton(reduceMotion)
        items.isEmpty() -> OfficialEmptyState(error ?: emptyText, onRetry)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = CpDimens.screenPadding, vertical = 4.dp),
        ) {
            item("summary", contentType = "summary") {
                Text(
                    "共 $total 部 · 已加载 ${items.size}",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            itemsIndexed(items, key = { _, item -> item.key }, contentType = { _, _ -> "official-ranking-row" }) { index, comic ->
                RankingRow(
                    rank = index + 1,
                    comic = comic,
                    onClick = { onOpen(comic) },
                    prominent = index < 3,
                    isFavorite = comic.key in favoriteKeys,
                    onToggleFavorite = { onToggleFavorite(comic) },
                )
            }
            error?.let { message ->
                item("error") {
                    Text(
                        message,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item("bottom-space") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun OfficialListSkeleton(reduceMotion: Boolean) {
    val brush = rememberShimmerBrush(animated = !reduceMotion)
    Column(
        Modifier.fillMaxSize().padding(horizontal = CpDimens.screenPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        repeat(7) { index ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ShimmerBlock(brush, Modifier.size(29.dp, 18.dp), RoundedCornerShape(6.dp))
                Spacer(Modifier.size(10.dp))
                ShimmerBlock(brush, Modifier.size(if (index < 3) 68.dp else 54.dp, 76.dp), RoundedCornerShape(8.dp))
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    ShimmerBlock(brush, Modifier.fillMaxWidth(.8f).height(15.dp), RoundedCornerShape(6.dp))
                    Spacer(Modifier.height(9.dp))
                    ShimmerBlock(brush, Modifier.fillMaxWidth(.5f).height(12.dp), RoundedCornerShape(6.dp))
                }
            }
        }
    }
}

@Composable
private fun OfficialEmptyState(text: String, onRetry: (() -> Unit)?) {
    Box(Modifier.fillMaxSize().padding(CpDimens.screenPadding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            if (onRetry != null) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry, shape = RoundedCornerShape(8.dp)) { Text("重新加载") }
            }
        }
    }
}
