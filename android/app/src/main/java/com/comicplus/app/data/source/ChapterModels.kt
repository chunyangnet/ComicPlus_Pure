package com.comicplus.app.data.source

import androidx.compose.runtime.Immutable

@Immutable
data class SourceChapterDto(
    val sourceChapterId: String,
    val index: Int = 1,
    val title: String = "第 1 话",
)
