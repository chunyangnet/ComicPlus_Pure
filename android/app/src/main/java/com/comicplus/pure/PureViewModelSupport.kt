package com.comicplus.pure

import com.comicplus.app.data.source.DirectJmCategory
import com.comicplus.app.data.source.DirectReaderPage
import com.comicplus.app.data.source.SourceChapterDto
import com.comicplus.app.data.source.SourceIds
import com.comicplus.app.ui.ComicResolveUiState
import com.comicplus.app.ui.ComicUiItem
import com.comicplus.app.ui.JmCommentUiItem
import com.comicplus.app.ui.JmSourceUiItem
import com.comicplus.app.ui.JmSourceUiState
import com.comicplus.app.ui.key
import kotlinx.coroutines.CancellationException
import java.io.File
import java.net.URI

internal data class FavoriteMutationSnapshot(
    val wasFavorite: Boolean,
    val originalIndex: Int,
)

internal fun favoriteMutationSnapshot(
    items: List<ComicUiItem>,
    item: ComicUiItem,
): FavoriteMutationSnapshot {
    val index = items.indexOfFirst { it.key == item.key }
    return FavoriteMutationSnapshot(wasFavorite = index >= 0, originalIndex = index.coerceAtLeast(0))
}

internal fun favoriteItemsWithMembership(
    items: List<ComicUiItem>,
    item: ComicUiItem,
    shouldBeFavorite: Boolean,
    insertionIndex: Int = 0,
    maxEntries: Int = Int.MAX_VALUE,
): List<ComicUiItem> {
    val existingIndex = items.indexOfFirst { it.key == item.key }
    if (!shouldBeFavorite) {
        return if (existingIndex < 0) items else items.filterNot { it.key == item.key }
    }
    if (existingIndex >= 0) return items
    val next = items.toMutableList()
    next.add(insertionIndex.coerceIn(0, next.size), item)
    return next.take(maxEntries.coerceAtLeast(0))
}

internal fun adjustedFavoriteCount(
    currentCount: Long?,
    loadedCount: Int,
    adding: Boolean,
): Long {
    val base = currentCount ?: loadedCount.toLong()
    return if (adding) base + 1L else (base - 1L).coerceAtLeast(0L)
}

/** UI and transport mappings kept out of the request/orchestration class. */
internal fun JmRanking.toUiItem() = ComicUiItem(
    jmId = id,
    title = title,
    subtitle = listOf(category, badge).filter(String::isNotBlank).joinToString(" · "),
    metric = when {
        likes != null -> "JM 收藏 ${likes.compact()}"
        views != null -> "JM 浏览 ${views.compact()}"
        else -> "JM 官方源"
    },
    accentIndex = id.takeLast(4).toIntOrNull() ?: title.hashCode(),
    coverUrl = coverUrl,
)

internal fun JmFavoriteItem.toUiItem() = ComicUiItem(
    jmId = id,
    title = title,
    subtitle = authors.take(2).joinToString(" · ").ifBlank { "JM 官方收藏" },
    metric = "JM 官方收藏",
    accentIndex = id.takeLast(4).toIntOrNull() ?: title.hashCode(),
    coverUrl = coverUrl,
)

internal fun JmComic.toUiItem() = ComicUiItem(
    jmId = id,
    title = title,
    subtitle = authors.take(2).joinToString(" · ").ifBlank { "JM 官方源" },
    metric = likes?.let { "JM 收藏 ${it.compact()}" }
        ?: views?.let { "JM 浏览 ${it.compact()}" }
        ?: "",
    accentIndex = id.takeLast(4).toIntOrNull() ?: title.hashCode(),
    coverUrl = coverUrl,
)

internal fun ComicResolveUiState.Ready.toUiItem() = ComicUiItem(
    jmId = jmId,
    title = title,
    subtitle = "JM 官方源",
    metric = "",
    accentIndex = jmId.takeLast(4).toIntOrNull() ?: title.hashCode(),
    coverUrl = coverUrl,
    source = source,
)

internal fun JmComment.toUiItem(): JmCommentUiItem {
    val normalizedUsername = username.trim()
    val normalizedNickname = nickname.trim()
    return JmCommentUiItem(
        id = id,
        userId = userId,
        displayName = normalizedNickname
            .ifBlank { normalizedUsername }
            .ifBlank { userId?.let { "JM$it" }.orEmpty() }
            .ifBlank { "JM 用户" },
        username = normalizedUsername,
        content = content.trim(),
        avatarUrl = avatarUrl?.trim()?.takeIf { it.startsWith("https://") },
        createdAt = createdAt.trim(),
        likes = likes.coerceAtLeast(0L),
        spoiler = spoiler,
        replies = replies.map(JmComment::toUiItem),
    )
}

internal fun JmCategory.toDirectCategory() = DirectJmCategory(id, name, slug, type, totalAlbums)

internal fun JmComic.toResolveState(progress: LocalReadingProgress?) = ComicResolveUiState.Ready(
    source = SourceIds.Jm,
    jmId = id,
    title = title,
    description = description,
    coverUrl = coverUrl,
    cacheState = "direct",
    refreshing = false,
    chapters = chapters.map { SourceChapterDto(it.id, it.index, it.title) },
    resumeChapterId = progress?.chapterId,
    resumePageIndex = progress?.pageIndex ?: 0,
)

internal fun JmPage.toDirectReaderPage() = DirectReaderPage(
    index = index,
    photoId = photoId,
    fileName = fileName,
    scrambleId = scrambleId,
    url = url,
    alternativeUrls = alternativeUrls,
    referer = referer,
    localPath = localPath,
)

internal fun DirectReaderPage.toJmPage() = JmPage(
    index = index,
    photoId = photoId,
    fileName = fileName,
    scrambleId = scrambleId,
    url = url,
    alternativeUrls = alternativeUrls,
    referer = referer,
    localPath = localPath ?: if (url.startsWith("file:")) {
        runCatching { File(URI(url)).absolutePath }.getOrNull()
    } else {
        null
    },
)

internal fun JmSourceSnapshot.toUiState(
    checking: Boolean = false,
    error: String? = null,
) = JmSourceUiState(
    items = endpoints.map { endpoint -> JmSourceUiItem(endpoint.host, endpoint.latencyMs) },
    selectedHost = selectedHost,
    updatedAt = updatedAt,
    imageItems = imageEndpoints.map { endpoint -> JmSourceUiItem(endpoint.host, endpoint.latencyMs) },
    selectedImageHost = selectedImageHost,
    imageUpdatedAt = imageUpdatedAt,
    checking = checking,
    error = error,
)

internal fun Long.compact(): String = when {
    this >= 1_000_000 -> "%.1fM".format(this / 1_000_000.0)
    this >= 1_000 -> "%.1fK".format(this / 1_000.0)
    else -> toString()
}

internal fun Throwable.readable(): String = message?.take(120).orEmpty().ifBlank { "JM 官方源连接失败" }

internal fun Throwable.rethrowCancellation() {
    if (this is CancellationException) throw this
}

internal fun parseJmId(raw: String): String? {
    val value = raw.trim()
    return when {
        value.matches(SAFE_JM_ID) -> value
        else -> JM_ID_INPUT.matchEntire(value)?.groupValues?.getOrNull(1)
    }
}

internal val SAFE_JM_ID = Regex("^\\d{1,12}$")
private val JM_ID_INPUT = Regex("(?i)^jm\\s*[:#-]?\\s*(\\d{1,12})$")
