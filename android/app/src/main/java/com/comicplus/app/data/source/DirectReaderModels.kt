package com.comicplus.app.data.source

import androidx.compose.runtime.Immutable

@Immutable
data class DirectJmCategory(
    val id: String,
    val name: String,
    val slug: String,
    val type: String,
    val totalAlbums: Long?,
)

@Immutable
data class DirectReaderPage(
    val index: Int,
    val photoId: String,
    val fileName: String,
    val scrambleId: String,
    val url: String,
    val alternativeUrls: List<String> = emptyList(),
    val referer: String,
    val localPath: String? = null,
)
