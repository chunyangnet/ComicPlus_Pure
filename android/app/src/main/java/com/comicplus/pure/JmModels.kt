package com.comicplus.pure

import androidx.compose.runtime.Immutable
import java.io.IOException
import java.time.LocalDate

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

/** The signed-in user's official daily check-in calendar. */
@Immutable
data class JmDailyInfo(
    val dailyId: String,
    val eventName: String = "",
    val currentProgress: String = "",
    val records: List<JmDailyRecord> = emptyList(),
    val threeDaysCoin: Long? = null,
    val threeDaysExp: Long? = null,
    val sevenDaysCoin: Long? = null,
    val sevenDaysExp: Long? = null,
)

fun JmDailyInfo.isSignedToday(today: LocalDate = LocalDate.now()): Boolean = records.any { record ->
    if (!record.signed) return@any false
    val raw = record.date.trim()
    val parsed = runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    parsed == today || parsed == null && raw.toIntOrNull() == today.dayOfMonth
}

@Immutable
data class JmDailyRecord(
    val date: String = "",
    val signed: Boolean = false,
    val bonus: Boolean = false,
)

/** Result returned by the official daily check-in mutation. */
@Immutable
data class JmDailyCheckResult(
    val status: String = "",
    val message: String = "",
) {
    val alreadySigned: Boolean
        get() = message.contains("已签到") || message.contains("已簽到") ||
            message.contains("already", ignoreCase = true)

    val rejected: Boolean
        get() = !alreadySigned && (
            status.lowercase() in setOf("error", "fail", "failed", "false", "0", "400", "403") ||
                message.contains("失败") || message.contains("失敗") ||
                message.contains("错误") || message.contains("錯誤") ||
                message.contains("invalid", ignoreCase = true) ||
                message.contains("error", ignoreCase = true) ||
                message.contains("fail", ignoreCase = true)
            )

    val accepted: Boolean
        get() = !rejected && (alreadySigned || message.contains("成功") ||
            message.contains("success", ignoreCase = true) || status.isBlank() ||
            status.equals("ok", ignoreCase = true) ||
            status.equals("success", ignoreCase = true) ||
            status.equals("true", ignoreCase = true) ||
            status == "1" || status == "200")
}

/** Short-lived AVS session data restored across process restarts. */
data class JmSession(
    val uid: String,
    val username: String,
    val avs: String,
)

/** Credentials retained only when the user opts into automatic re-login. */
data class JmCredentials(
    val username: String,
    val password: String,
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
data class JmFavoriteFolder(
    val id: String,
    val name: String,
)

@Immutable
data class JmFavoritePage(
    val folderId: String,
    val page: Int,
    val total: Long,
    val items: List<JmFavoriteItem>,
    val folders: List<JmFavoriteFolder>,
    val hasMore: Boolean,
    /** Whether the upstream response explicitly supplied a total/count field. */
    val totalKnown: Boolean = true,
)

@Immutable
data class JmFavoriteCollection(
    val folderId: String,
    val total: Long,
    val items: List<JmFavoriteItem>,
    val folders: List<JmFavoriteFolder>,
)

class JmAuthException(message: String) : IOException(message)

class JmApiException(message: String, val statusCode: Int? = null) : IOException(message)
