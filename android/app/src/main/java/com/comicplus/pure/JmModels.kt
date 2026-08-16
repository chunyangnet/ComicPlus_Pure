package com.comicplus.pure

import androidx.compose.runtime.Immutable
import java.io.IOException

@Immutable
data class JmRanking(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val views: Long? = null,
    val likes: Long? = null,
    val badge: String = "JM",
    val category: String = "",
)

@Immutable
data class JmCategory(
    val id: String,
    val name: String,
    val slug: String,
    val totalAlbums: Long? = null,
    val type: String = "slug",
)

@Immutable
data class JmTagGroup(
    val title: String,
    val tags: List<String>,
)

@Immutable
data class JmCategoryCatalog(
    val categories: List<JmCategory>,
    val tagGroups: List<JmTagGroup>,
)

@Immutable
data class JmWeekOption(
    val id: String,
    val title: String,
)

@Immutable
data class JmWeekCatalog(
    val categories: List<JmWeekOption>,
    val types: List<JmWeekOption>,
)

@Immutable
data class JmRankingPage(
    val page: Int,
    val total: Long,
    val items: List<JmRanking>,
    val hasMore: Boolean,
)

@Immutable
data class JmChapter(val id: String, val index: Int, val title: String)

@Immutable
data class JmComic(
    val id: String,
    val title: String,
    val description: String,
    val coverUrl: String?,
    val authors: List<String>,
    val tags: List<String>,
    val chapters: List<JmChapter>,
    val views: Long? = null,
    val likes: Long? = null,
)

@Immutable
data class JmPage(
    val index: Int,
    val photoId: String,
    val fileName: String,
    val scrambleId: String,
    val url: String,
    val alternativeUrls: List<String> = emptyList(),
    val referer: String,
    val localPath: String? = null,
)

@Immutable
data class JmChapterPages(val chapterId: String, val title: String, val pages: List<JmPage>)

@Immutable
data class JmSearchPage(
    val query: String,
    val page: Int,
    val total: Long,
    val redirectAid: String?,
    val items: List<JmRanking>,
    val hasMore: Boolean,
)

/** A read-only comment returned by the official JM forum endpoint. */
@Immutable
data class JmComment(
    val id: String,
    val userId: String? = null,
    val albumId: String? = null,
    val username: String = "",
    val nickname: String = "",
    val content: String = "",
    val avatarUrl: String? = null,
    val createdAt: String = "",
    val likes: Long = 0L,
    val parentId: String? = null,
    val spoiler: Boolean = false,
    val replies: List<JmComment> = emptyList(),
)

@Immutable
data class JmCommentPage(
    val page: Int,
    val total: Long,
    val comments: List<JmComment>,
    val hasMore: Boolean,
)

/** Account information returned by the official JM mobile API. */
@Immutable
data class JmAccount(
    val uid: String,
    val username: String,
    val avatarUrl: String? = null,
    val favoriteCount: Long? = null,
)

/** The only credential retained by the app is the short-lived AVS session cookie. */
data class JmSession(
    val uid: String,
    val username: String,
    val avs: String,
)

@Immutable
data class JmFavoriteItem(
    val id: String,
    val title: String,
    val description: String = "",
    val coverUrl: String? = null,
    val authors: List<String> = emptyList(),
)

@Immutable
data class JmFavoritePage(
    val page: Int,
    val total: Long,
    val items: List<JmFavoriteItem>,
    val hasMore: Boolean,
)

class JmAuthException(message: String) : IOException(message)

class JmApiException(message: String, val statusCode: Int? = null) : IOException(message)
