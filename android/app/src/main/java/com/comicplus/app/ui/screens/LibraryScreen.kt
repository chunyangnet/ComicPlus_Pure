package com.comicplus.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicplus.app.ui.ComicUiItem
import com.comicplus.app.ui.ReadingHistoryItem
import com.comicplus.app.ui.key
import com.comicplus.app.ui.components.AppBarAction
import com.comicplus.app.ui.components.ComicCover
import com.comicplus.app.ui.components.ComicPlusTopBar
import com.comicplus.app.ui.components.ComicPosterCard
import com.comicplus.app.ui.components.CpDimens
import com.comicplus.app.ui.components.PillRow
import com.comicplus.app.ui.theme.Canvas
import com.comicplus.app.ui.theme.Ink
import com.comicplus.app.ui.theme.InkSoft
import com.comicplus.app.ui.theme.Muted
import com.comicplus.app.ui.theme.SurfaceSoft

private enum class LibrarySection(val label: String) {
    Favorites("收藏"),
    History("历史记录"),
}

@Composable
fun LibraryScreen(
    favorites: List<ComicUiItem>,
    history: List<ReadingHistoryItem>,
    onOpen: (ComicUiItem) -> Unit,
    onToggleFavorite: (ComicUiItem) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sectionName by rememberSaveable { mutableStateOf(LibrarySection.Favorites.name) }
    var showClearDialog by remember { mutableStateOf(false) }
    val section = LibrarySection.entries.firstOrNull { it.name == sectionName } ?: LibrarySection.Favorites
    val favoriteKeys = remember(favorites) { favorites.mapTo(hashSetOf(), ComicUiItem::key) }

    Column(modifier.fillMaxSize().background(Canvas)) {
        ComicPlusTopBar(
            title = "我的书架",
            subtitle = if (section == LibrarySection.Favorites) {
                "仅保存在本机 · 收藏 ${favorites.size} 部"
            } else {
                "仅保存在本机 · 最近阅读 ${history.size} 部"
            },
            actions = if (section == LibrarySection.History && history.isNotEmpty()) {
                listOf(AppBarAction(Icons.Outlined.DeleteSweep, "清空历史记录") { showClearDialog = true })
            } else {
                emptyList()
            },
        )
        PillRow(
            labels = listOf("收藏 ${favorites.size}", "历史记录 ${history.size}"),
            selected = if (section == LibrarySection.Favorites) "收藏 ${favorites.size}" else "历史记录 ${history.size}",
            onSelected = { label ->
                sectionName = if (label.startsWith("收藏")) LibrarySection.Favorites.name else LibrarySection.History.name
            },
        )
        Spacer(Modifier.height(10.dp))
        when (section) {
            LibrarySection.Favorites -> FavoriteShelf(
                items = favorites,
                onOpen = onOpen,
                onToggleFavorite = onToggleFavorite,
            )
            LibrarySection.History -> HistoryList(
                items = history,
                favoriteKeys = favoriteKeys,
                onOpen = onOpen,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空历史记录") },
            text = { Text("将删除本机保存的阅读历史，但不会影响收藏和阅读进度。") },
            confirmButton = {
                Button(onClick = { showClearDialog = false; onClearHistory() }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun FavoriteShelf(
    items: List<ComicUiItem>,
    onOpen: (ComicUiItem) -> Unit,
    onToggleFavorite: (ComicUiItem) -> Unit,
) {
    if (items.isEmpty()) {
        LibraryEmptyState("还没有收藏漫画", "在封面右上角点 ♥，把喜欢的作品留在这里。")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = CpDimens.screenPadding, end = CpDimens.screenPadding, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(items.chunked(2), key = { row -> row.joinToString("|") { it.key } }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { comic ->
                    ComicPosterCard(
                        comic = comic,
                        onClick = { onOpen(comic) },
                        modifier = Modifier.weight(1f),
                        isFavorite = true,
                        onToggleFavorite = { onToggleFavorite(comic) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HistoryList(
    items: List<ReadingHistoryItem>,
    favoriteKeys: Set<String>,
    onOpen: (ComicUiItem) -> Unit,
    onToggleFavorite: (ComicUiItem) -> Unit,
) {
    if (items.isEmpty()) {
        LibraryEmptyState("还没有阅读历史", "打开一部漫画后，它会自动出现在这里。")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = CpDimens.screenPadding, end = CpDimens.screenPadding, bottom = 28.dp),
    ) {
        items(items, key = { it.comic.key }, contentType = { "history-row" }) { entry ->
            HistoryRow(
                entry = entry,
                isFavorite = entry.comic.key in favoriteKeys,
                onOpen = { onOpen(entry.comic) },
                onToggleFavorite = { onToggleFavorite(entry.comic) },
            )
        }
    }
}

@Composable
private fun HistoryRow(
    entry: ReadingHistoryItem,
    isFavorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    // Keep history compact enough to scan while still exposing the resume context.
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable(onClick = onOpen),
        shape = RoundedCornerShape(CpDimens.controlRadius),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(62.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(10.dp)),
            ) {
                ComicCover(entry.comic.coverUrl, entry.comic.title, entry.comic.accentIndex, Modifier.fillMaxSize())
                com.comicplus.app.ui.components.FavoriteButton(
                    isFavorite = isFavorite,
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    compact = true,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.comic.title, color = Ink, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                val chapter = entry.chapterTitle?.takeIf(String::isNotBlank) ?: "已打开详情"
                Text(chapter, color = InkSoft, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    historySummary(entry),
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (entry.pageCount > 0) {
                    Spacer(Modifier.height(7.dp))
                    Surface(
                        Modifier.fillMaxWidth().height(4.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = SurfaceSoft,
                    ) {
                        Box(
                            Modifier.fillMaxWidth(
                                ((entry.pageIndex + 1f) / entry.pageCount.coerceAtLeast(1)).coerceIn(0f, 1f),
                            ),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(title: String, message: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = CpDimens.screenPadding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun historySummary(entry: ReadingHistoryItem): String {
    val time = if (entry.updatedAt > 0L) {
        java.text.SimpleDateFormat("MM月dd日 HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.updatedAt))
    } else {
        "刚刚"
    }
    return if (entry.pageCount > 0) {
        "$time · 第 ${entry.pageIndex + 1}/${entry.pageCount} 页"
    } else {
        time
    }
}
