package com.comicplus.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.comicplus.app.ui.icons.ComicPlusIcons as Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.comicplus.app.ui.JmFavoriteFolderUiItem
import com.comicplus.app.ui.JmFavoriteFoldersUiState
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

private const val FAVORITE_FOLDER_NAME_LIMIT = 80

@Composable
fun LibraryScreen(
    favorites: List<ComicUiItem>,
    history: List<ReadingHistoryItem>,
    onOpen: (ComicUiItem) -> Unit,
    onToggleFavorite: (ComicUiItem) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteHistory: (ReadingHistoryItem) -> Unit,
    modifier: Modifier = Modifier,
    favoriteFolders: JmFavoriteFoldersUiState = JmFavoriteFoldersUiState(),
    onSelectFavoriteFolder: (String) -> Unit = {},
    onRetryFavoriteFolder: () -> Unit = {},
    onCreateFavoriteFolder: (String) -> Unit = {},
    onMoveFavoriteToFolder: (ComicUiItem, String) -> Unit = { _, _ -> },
    signedIn: Boolean = false,
) {
    var sectionName by rememberSaveable { mutableStateOf(LibrarySection.Favorites.name) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by rememberSaveable { mutableStateOf("") }
    var movingComic by remember { mutableStateOf<ComicUiItem?>(null) }
    var pendingHistoryDelete by remember { mutableStateOf<ReadingHistoryItem?>(null) }
    val section = LibrarySection.entries.firstOrNull { it.name == sectionName } ?: LibrarySection.Favorites
    val favoriteKeys = remember(favorites) { favorites.mapTo(hashSetOf(), ComicUiItem::key) }
    val selectedFolder = favoriteFolders.folders.firstOrNull { it.id == favoriteFolders.selectedFolderId }
        ?: favoriteFolders.folders.firstOrNull()
    val displayedFavorites = if (signedIn) favoriteFolders.items else favorites
    val canMoveFavorites = signedIn && favoriteFolders.folders.any {
        it.id != "0" && it.id != favoriteFolders.selectedFolderId
    }

    Column(modifier.fillMaxSize().background(Canvas)) {
        ComicPlusTopBar(
            title = "我的书架",
            subtitle = if (section == LibrarySection.Favorites) {
                if (signedIn) "JM 官方收藏 · ${selectedFolder?.name ?: "全部"} ${favoriteFolders.total} 部"
                else "本机收藏缓存 · 登录 JM 后同步"
            } else {
                "仅保存在本机 · 最近阅读 ${history.size} 部"
            },
            actions = when {
                section == LibrarySection.History && history.isNotEmpty() ->
                    listOf(AppBarAction(Icons.Outlined.DeleteSweep, "清空历史记录") { showClearDialog = true })
                section == LibrarySection.Favorites && signedIn ->
                    listOf(
                        AppBarAction(
                            icon = Icons.Outlined.Category,
                            label = if (favoriteFolders.creating) "创建中" else "新建收藏夹",
                            prominent = true,
                        ) {
                            if (!favoriteFolders.creating) showCreateFolderDialog = true
                        },
                    )
                else -> emptyList()
            },
        )
        PillRow(
            labels = listOf("收藏 ${favorites.size}", "历史记录 ${history.size}"),
            selectedIndex = if (section == LibrarySection.Favorites) 0 else 1,
            onSelected = { index ->
                sectionName = if (index == 0) LibrarySection.Favorites.name else LibrarySection.History.name
            },
        )
        Spacer(Modifier.height(10.dp))
        if (section == LibrarySection.Favorites && signedIn) {
            val folders = favoriteFolders.folders.ifEmpty {
                listOf(JmFavoriteFolderUiItem(id = "0", name = "全部"))
            }
            PillRow(
                labels = folders.map { it.name },
                selectedIndex = folders.indexOfFirst { it.id == favoriteFolders.selectedFolderId }.coerceAtLeast(0),
                onSelected = { index -> folders.getOrNull(index)?.let { onSelectFavoriteFolder(it.id) } },
            )
            Spacer(Modifier.height(10.dp))
        }
        when (section) {
            LibrarySection.Favorites -> FavoriteShelf(
                items = displayedFavorites,
                favoriteKeys = favoriteKeys,
                loading = signedIn && favoriteFolders.loading,
                error = favoriteFolders.error,
                customFolder = signedIn && favoriteFolders.selectedFolderId != "0",
                onOpen = onOpen,
                onToggleFavorite = onToggleFavorite,
                onRetry = onRetryFavoriteFolder,
                onMoveFavorite = if (canMoveFavorites) ({ comic -> movingComic = comic }) else null,
                movingKey = favoriteFolders.movingKey,
            )
            LibrarySection.History -> HistoryList(
                items = history,
                favoriteKeys = favoriteKeys,
                onOpen = onOpen,
                onToggleFavorite = onToggleFavorite,
                onDelete = { pendingHistoryDelete = it },
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

    pendingHistoryDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingHistoryDelete = null },
            title = { Text("删除历史记录") },
            text = { Text("确定删除《${entry.comic.title}》的阅读历史吗？收藏和阅读进度不会受影响。") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingHistoryDelete = null
                        onDeleteHistory(entry)
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingHistoryDelete = null }) { Text("取消") }
            },
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("新建收藏夹") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { value -> newFolderName = value.take(FAVORITE_FOLDER_NAME_LIMIT) },
                    label = { Text("收藏夹名称") },
                    supportingText = { Text("${newFolderName.length}/$FAVORITE_FOLDER_NAME_LIMIT") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    enabled = newFolderName.isNotBlank() && !favoriteFolders.creating,
                    onClick = {
                        val name = newFolderName.trim()
                        showCreateFolderDialog = false
                        newFolderName = ""
                        onCreateFavoriteFolder(name)
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("取消") }
            },
        )
    }

    movingComic?.let { comic ->
        val targets = favoriteFolders.folders.filter {
            it.id != "0" && it.id != favoriteFolders.selectedFolderId
        }
        AlertDialog(
            onDismissRequest = { movingComic = null },
            title = { Text("移动到收藏夹") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(targets, key = { it.id }) { folder ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                movingComic = null
                                onMoveFavoriteToFolder(comic, folder.id)
                            },
                        ) {
                            Text(folder.name, modifier = Modifier.fillMaxWidth(), color = Ink)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { movingComic = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun FavoriteShelf(
    items: List<ComicUiItem>,
    favoriteKeys: Set<String>,
    loading: Boolean,
    error: String?,
    customFolder: Boolean,
    onOpen: (ComicUiItem) -> Unit,
    onToggleFavorite: (ComicUiItem) -> Unit,
    onRetry: () -> Unit,
    onMoveFavorite: ((ComicUiItem) -> Unit)?,
    movingKey: String?,
) {
    if (loading && items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (error != null && items.isEmpty()) {
        LibraryErrorState(error, onRetry)
        return
    }
    if (items.isEmpty()) {
        if (customFolder) {
            LibraryEmptyState("这个收藏夹还是空的", "回到“全部”，点封面右下角的移动按钮即可归类。")
        } else {
            LibraryEmptyState("还没有收藏漫画", "在封面右上角点 ♥，把喜欢的作品留在这里。")
        }
        return
    }
    val rows = remember(items) { items.chunked(2) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = CpDimens.screenPadding, end = CpDimens.screenPadding, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(rows, key = { row -> row.joinToString("|") { it.key } }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { comic ->
                    ComicPosterCard(
                        comic = comic,
                        onClick = { onOpen(comic) },
                        modifier = Modifier.weight(1f),
                        isFavorite = comic.key in favoriteKeys,
                        onToggleFavorite = { onToggleFavorite(comic) },
                        onMoveFavorite = onMoveFavorite?.let { move -> { move(comic) } },
                        moveFavoriteLoading = movingKey == comic.key,
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LibraryErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(horizontal = CpDimens.screenPadding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("收藏夹读取失败", color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun HistoryList(
    items: List<ReadingHistoryItem>,
    favoriteKeys: Set<String>,
    onOpen: (ComicUiItem) -> Unit,
    onToggleFavorite: (ComicUiItem) -> Unit,
    onDelete: (ReadingHistoryItem) -> Unit,
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
                onDelete = { onDelete(entry) },
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
    onDelete: () -> Unit,
) {
    val summary = remember(entry.updatedAt, entry.pageIndex, entry.pageCount) {
        historySummary(entry)
    }
    // Keep history compact enough to scan while still exposing the resume context.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .combinedClickable(
                onClick = onOpen,
                onLongClickLabel = "删除历史记录",
                onLongClick = onDelete,
            ),
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
                    favoriteKey = entry.comic.key,
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
                    summary,
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
