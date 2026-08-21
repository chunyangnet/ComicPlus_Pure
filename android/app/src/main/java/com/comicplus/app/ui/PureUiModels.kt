package com.comicplus.app.ui

import androidx.compose.runtime.Immutable
import com.comicplus.app.data.source.DirectJmCategory
import com.comicplus.app.data.source.DirectReaderPage
import com.comicplus.app.data.source.SourceChapterDto
import com.comicplus.app.data.source.SourceIds
import com.comicplus.pure.DownloadedChapter
import com.comicplus.pure.JmDailyInfo
import java.time.LocalDate

@Immutable
data class ComicUiItem(
    val jmId: String,
    val title: String,
    val subtitle: String,
    val metric: String,
    val accentIndex: Int,
    val coverUrl: String? = null,
    val source: String = SourceIds.Jm,
)

val ComicUiItem.key: String get() = "$source:$jmId"

/** A locally persisted reading entry. The comic snapshot keeps the library useful offline. */
@Immutable
data class ReadingHistoryItem(
    val comic: ComicUiItem,
    val chapterId: String? = null,
    val chapterTitle: String? = null,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val updatedAt: Long = 0L,
)

@Immutable
data class JmSearchUiState(
    val query: String = "",
    val mainTag: Int = 0,
    val order: String = "mr",
    val items: List<ComicUiItem> = emptyList(),
    val page: Int = 0,
    val total: Long = 0,
    val submitted: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val redirectAid: String? = null,
    val error: String? = null,
)

@Immutable
data class RankingsUiState(
    val jmOrder: String = "mv",
    val jmItems: List<ComicUiItem> = emptyList(),
    val jmLoading: Boolean = false,
    val jmLoaded: Boolean = false,
    val jmError: String? = null,
)

@Immutable
data class JmBrowseOptionUi(
    val id: String,
    val title: String,
)

@Immutable
data class JmTagGroupUi(
    val title: String,
    val tags: List<String>,
)

@Immutable
data class JmWeeklyUiState(
    val categories: List<JmBrowseOptionUi> = emptyList(),
    val types: List<JmBrowseOptionUi> = emptyList(),
    val selectedCategoryId: String = "",
    val selectedTypeId: String = "",
    val items: List<ComicUiItem> = emptyList(),
    val total: Long = 0,
    val catalogLoading: Boolean = false,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

@Immutable
data class JmTypeRankingUiState(
    val selectedSlug: String = "doujin",
    val order: String = "mv",
    val items: List<ComicUiItem> = emptyList(),
    val total: Long = 0,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

@Immutable
data class JmOfficialBrowseUiState(
    val tagGroups: List<JmTagGroupUi> = emptyList(),
    val catalogLoading: Boolean = false,
    val catalogLoaded: Boolean = false,
    val weekly: JmWeeklyUiState = JmWeeklyUiState(),
    val typeRanking: JmTypeRankingUiState = JmTypeRankingUiState(),
)

@Immutable
data class JmCommentUiItem(
    val id: String,
    val userId: String? = null,
    val displayName: String = "JM 用户",
    val username: String = "",
    val content: String = "",
    val avatarUrl: String? = null,
    val createdAt: String = "",
    val likes: Long = 0L,
    val spoiler: Boolean = false,
    val replies: List<JmCommentUiItem> = emptyList(),
)

@Immutable
data class JmCommentsUiState(
    val comicId: String = "",
    val chapterId: String = "",
    val items: List<JmCommentUiItem> = emptyList(),
    val page: Int = 0,
    val total: Long = 0L,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val loaded: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
)

enum class JmAccountStatus {
    SignedOut,
    Restoring,
    SigningIn,
    SignedIn,
    Error,
}

@Immutable
data class JmAccountUiState(
    val status: JmAccountStatus = JmAccountStatus.SignedOut,
    val uid: String = "",
    val username: String = "",
    val favoriteCount: Long? = null,
    val error: String? = null,
    val syncing: Boolean = false,
) {
    val signedIn: Boolean get() = status == JmAccountStatus.SignedIn
}

enum class JmDailyStatus {
    Idle,
    Loading,
    Ready,
    Checking,
    Error,
}

@Immutable
data class JmDailyUiState(
    val status: JmDailyStatus = JmDailyStatus.Idle,
    val info: JmDailyInfo? = null,
    val confirmedEpochDay: Long? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val loading: Boolean get() = status == JmDailyStatus.Loading
    val checking: Boolean get() = status == JmDailyStatus.Checking
    val confirmedToday: Boolean get() = confirmedEpochDay == LocalDate.now().toEpochDay()
}

@Immutable
data class JmFavoriteFolderUiItem(
    val id: String,
    val name: String,
)

@Immutable
data class JmFavoriteFoldersUiState(
    val folders: List<JmFavoriteFolderUiItem> = listOf(JmFavoriteFolderUiItem(id = "0", name = "全部")),
    val selectedFolderId: String = "0",
    val items: List<ComicUiItem> = emptyList(),
    val total: Long = 0L,
    val loading: Boolean = false,
    val creating: Boolean = false,
    val movingKey: String? = null,
    val error: String? = null,
)

@Immutable
data class CategoryUiState(
    val selectedSlug: String = "0",
    val order: String = "mr",
    val items: List<ComicUiItem> = emptyList(),
    val page: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

@Immutable
sealed interface ComicResolveUiState {
    data object Idle : ComicResolveUiState
    data class Loading(val source: String, val jmId: String) : ComicResolveUiState
    data class Ready(
        val source: String,
        val jmId: String,
        val title: String,
        val description: String,
        val coverUrl: String?,
        val cacheState: String,
        val refreshing: Boolean,
        val chapters: List<SourceChapterDto>,
        val resumeChapterId: String? = null,
        val resumePageIndex: Int = 0,
        val tags: List<String> = emptyList(),
    ) : ComicResolveUiState
    data class Error(val source: String, val jmId: String, val message: String) : ComicResolveUiState
}

@Immutable
sealed interface ReaderUiState {
    data object Idle : ReaderUiState
    data class Loading(
        val source: String,
        val sourceId: String,
        val title: String,
        val chapterId: String,
        val chapterTitle: String,
        val initialPageIndex: Int,
    ) : ReaderUiState
    data class Ready(
        val source: String,
        val sourceId: String,
        val title: String,
        val chapterId: String,
        val chapterTitle: String,
        val pages: List<DirectReaderPage>,
        val chapters: List<SourceChapterDto>,
        val currentChapterIndex: Int,
        val initialPageIndex: Int,
        val changingChapterTitle: String? = null,
    ) : ReaderUiState
    data class Error(
        val source: String,
        val sourceId: String,
        val title: String,
        val chapterId: String,
        val chapterTitle: String,
        val chapters: List<SourceChapterDto>,
        val initialPageIndex: Int,
        val message: String,
    ) : ReaderUiState
}

@Immutable
data class ReaderChapterSegment(
    val chapterId: String,
    val chapterTitle: String,
    val chapterIndex: Int,
    val pages: List<DirectReaderPage>,
)

@Immutable
data class AppSettings(
    val paletteKey: String = "ocean",
    val darkMode: Boolean = false,
    val chapterDescending: Boolean = false,
    val readerPrefetchPages: Int = 3,
    val readerPrefetchMode: ReaderPrefetchMode = ReaderPrefetchMode.Smart,
    val readerPageSpacingDp: Int = 0,
    val readerMode: ReaderMode = ReaderMode.Vertical,
    val readerDirection: ReaderDirection = ReaderDirection.LeftToRight,
    val readerImageQuality: ReaderImageQuality = ReaderImageQuality.Medium,
    val readerTurboMode: Boolean = false,
    val readerBrightnessPercent: Int = 0,
    val keepScreenOn: Boolean = true,
    val reduceMotion: Boolean = false,
    val tapToToggleReaderMenu: Boolean = true,
    val autoResumeReading: Boolean = true,
    val sequentialPageLoading: Boolean = false,
    val dataSaver: Boolean = false,
    val autoSelectSource: Boolean = true,
    val preferredSourceHost: String? = null,
    val preferredImageHost: String? = null,
    val autoUpdateSourceList: Boolean = true,
)

enum class ReaderMode { Vertical, Paged }
enum class ReaderDirection { LeftToRight, RightToLeft }
enum class ReaderImageQuality { Low, Medium, High }
enum class ReaderPrefetchMode { Conservative, Smart, Aggressive, UltraAggressive, Custom }

fun readerBrightnessFraction(value: Int): Float? = value.takeIf { it in 1..100 }?.div(100f)

internal fun effectiveReaderPrefetchPages(
    configuredPages: Int,
    dataSaver: Boolean,
    memoryClassMb: Int = Int.MAX_VALUE,
    turboMode: Boolean = false,
    mode: ReaderPrefetchMode = ReaderPrefetchMode.Smart,
    imageQuality: ReaderImageQuality = ReaderImageQuality.Medium,
    pageVelocityPagesPerSecond: Float = 0f,
    networkLatencyMs: Long? = null,
): Int {
    if (mode == ReaderPrefetchMode.Conservative && configuredPages <= 0) return 0
    if (mode == ReaderPrefetchMode.UltraAggressive) {
        if (dataSaver) return 1
        val decodeProfileBudget = when {
            turboMode -> 12
            imageQuality == ReaderImageQuality.Low -> 10
            imageQuality == ReaderImageQuality.Medium -> 8
            else -> 6
        }
        val memoryBudget = when {
            memoryClassMb < 384 -> if (turboMode) 3 else 2
            memoryClassMb < 512 -> 4
            memoryClassMb < 768 -> 8
            else -> 12
        }
        return minOf(decodeProfileBudget, memoryBudget)
    }
    val modeBase = when (mode) {
        ReaderPrefetchMode.Conservative -> configuredPages.coerceIn(0, 2)
        ReaderPrefetchMode.Smart -> configuredPages.coerceIn(0, 6)
        ReaderPrefetchMode.Aggressive -> configuredPages.coerceAtLeast(5).coerceIn(0, 6)
        ReaderPrefetchMode.UltraAggressive -> error("handled above")
        ReaderPrefetchMode.Custom -> configuredPages.coerceIn(0, 6)
    }
    val velocityBoost = when {
        mode != ReaderPrefetchMode.Smart -> 0
        pageVelocityPagesPerSecond >= 2.2f -> 2
        pageVelocityPagesPerSecond >= .9f -> 1
        pageVelocityPagesPerSecond > 0f && pageVelocityPagesPerSecond < .15f -> -1
        else -> 0
    }
    // On a high-latency route, one extra page is cheaper than repeatedly
    // waiting at the boundary. The memory cap below still wins on small heaps.
    val latencyBoost = if (mode == ReaderPrefetchMode.Smart && networkLatencyMs != null && networkLatencyMs >= 450L) 1 else 0
    val bounded = (modeBase + velocityBoost + latencyBoost).coerceIn(0, 6).let {
        if (turboMode) it.coerceAtLeast(4).coerceIn(0, 6) else it
    }
    val memoryBounded = when {
        turboMode && memoryClassMb < 384 -> bounded.coerceAtMost(3)
        turboMode && memoryClassMb < 512 -> bounded.coerceAtMost(4)
        turboMode -> bounded.coerceAtMost(5)
        memoryClassMb < 384 -> bounded.coerceAtMost(1)
        memoryClassMb < 512 -> bounded.coerceAtMost(2)
        else -> bounded
    }
    return if (dataSaver) memoryBounded.coerceAtMost(1) else memoryBounded
}

@Immutable
data class PureUiState(
    val home: List<ComicUiItem> = emptyList(),
    val rankings: RankingsUiState = RankingsUiState(),
    val officialBrowse: JmOfficialBrowseUiState = JmOfficialBrowseUiState(),
    val categories: List<DirectJmCategory> = emptyList(),
    val category: CategoryUiState = CategoryUiState(),
    val loading: Boolean = true,
    val search: JmSearchUiState = JmSearchUiState(),
    val detail: ComicResolveUiState = ComicResolveUiState.Idle,
    val comments: JmCommentsUiState = JmCommentsUiState(),
    val account: JmAccountUiState = JmAccountUiState(),
    val daily: JmDailyUiState = JmDailyUiState(),
    val reader: ReaderUiState = ReaderUiState.Idle,
    val settings: AppSettings = AppSettings(),
    val message: String? = null,
    val downloads: List<DownloadedChapter> = emptyList(),
    val downloadProgress: Map<String, Float> = emptyMap(),
    val discoveryItems: List<ComicUiItem> = emptyList(),
    val discoveryLoading: Boolean = false,
    val discoveryExhausted: Boolean = false,
    val sourceStatus: JmSourceUiState = JmSourceUiState(),
    val appUpdate: AppUpdateUiState = AppUpdateUiState(),
    val favorites: List<ComicUiItem> = emptyList(),
    val favoriteFolders: JmFavoriteFoldersUiState = JmFavoriteFoldersUiState(),
    val favoritePendingKeys: Set<String> = emptySet(),
    val history: List<ReadingHistoryItem> = emptyList(),
)

@Immutable
data class JmSourceUiState(
    val items: List<JmSourceUiItem> = emptyList(),
    val selectedHost: String? = null,
    val updatedAt: Long = 0L,
    val imageItems: List<JmSourceUiItem> = emptyList(),
    val selectedImageHost: String? = null,
    val imageUpdatedAt: Long = 0L,
    val checking: Boolean = false,
    val error: String? = null,
)

@Immutable
data class JmSourceUiItem(
    val host: String,
    val latencyMs: Long?,
)

@Immutable
data class AppUpdateUiState(
    val currentVersion: String = "",
    val checking: Boolean = false,
    val checked: Boolean = false,
    val latestVersion: String? = null,
    val releaseName: String? = null,
    val notes: String = "",
    val publishedAt: String? = null,
    val releaseUrl: String? = null,
    val downloadUrl: String? = null,
    val assetSize: Long? = null,
    val updateAvailable: Boolean = false,
    val error: String? = null,
)
