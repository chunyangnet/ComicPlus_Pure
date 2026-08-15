package com.comicplus.pure

import androidx.compose.runtime.Immutable

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
