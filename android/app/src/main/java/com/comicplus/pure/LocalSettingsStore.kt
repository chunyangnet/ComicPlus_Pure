package com.comicplus.pure

import android.content.Context
import androidx.core.content.edit
import com.comicplus.app.ui.AppSettings
import com.comicplus.app.ui.ReaderDirection
import com.comicplus.app.ui.ReaderImageQuality
import com.comicplus.app.ui.ReaderMode
import com.comicplus.app.ui.ReaderPrefetchMode

class LocalSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("comicplus_pure_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        paletteKey = preferences.getString("palette", "ocean").orEmpty().ifBlank { "ocean" },
        darkMode = preferences.getBoolean("dark_mode", false),
        readerPrefetchPages = preferences.getInt("prefetch", 3).coerceIn(0, 6),
        readerPrefetchMode = preferences.getString("prefetch_mode", null)
            ?.let { runCatching { ReaderPrefetchMode.valueOf(it) }.getOrNull() }
            ?: when (preferences.getInt("prefetch", 3).coerceIn(0, 6)) {
                0 -> ReaderPrefetchMode.Conservative
                5, 6 -> ReaderPrefetchMode.Aggressive
                else -> ReaderPrefetchMode.Smart
            },
        readerPageSpacingDp = preferences.getInt("page_spacing", 0).coerceIn(0, 16),
        readerMode = preferences.getString("reader_mode", ReaderMode.Vertical.name)
            ?.let { runCatching { ReaderMode.valueOf(it) }.getOrNull() } ?: ReaderMode.Vertical,
        readerDirection = preferences.getString("reader_direction", ReaderDirection.LeftToRight.name)
            ?.let { runCatching { ReaderDirection.valueOf(it) }.getOrNull() } ?: ReaderDirection.LeftToRight,
        readerImageQuality = preferences.getString("reader_image_quality", ReaderImageQuality.Medium.name)
            ?.let { runCatching { ReaderImageQuality.valueOf(it) }.getOrNull() } ?: ReaderImageQuality.Medium,
        readerTurboMode = preferences.getBoolean("reader_turbo_mode", false),
        readerBrightnessPercent = preferences.getInt("brightness", 0).coerceIn(0, 100),
        keepScreenOn = preferences.getBoolean("keep_screen_on", true),
        reduceMotion = preferences.getBoolean("reduce_motion", false),
        tapToToggleReaderMenu = preferences.getBoolean("tap_menu", true),
        autoResumeReading = preferences.getBoolean("auto_resume", true),
        dataSaver = preferences.getBoolean("data_saver", false),
        autoSelectSource = preferences.getBoolean("auto_select_source", true),
        preferredSourceHost = preferences.getString("preferred_source_host", null)?.takeIf(String::isNotBlank),
        preferredImageHost = preferences.getString("preferred_image_host", null)?.takeIf(String::isNotBlank),
        autoUpdateSourceList = preferences.getBoolean("auto_update_source_list", true),
    )

    fun save(settings: AppSettings) {
        preferences.edit {
            putString("palette", settings.paletteKey)
            putBoolean("dark_mode", settings.darkMode)
            putInt("prefetch", settings.readerPrefetchPages)
            putString("prefetch_mode", settings.readerPrefetchMode.name)
            putInt("page_spacing", settings.readerPageSpacingDp)
            putString("reader_mode", settings.readerMode.name)
            putString("reader_direction", settings.readerDirection.name)
            putString("reader_image_quality", settings.readerImageQuality.name)
            putBoolean("reader_turbo_mode", settings.readerTurboMode)
            putInt("brightness", settings.readerBrightnessPercent)
            putBoolean("keep_screen_on", settings.keepScreenOn)
            putBoolean("reduce_motion", settings.reduceMotion)
            putBoolean("tap_menu", settings.tapToToggleReaderMenu)
            putBoolean("auto_resume", settings.autoResumeReading)
            putBoolean("data_saver", settings.dataSaver)
            putBoolean("auto_select_source", settings.autoSelectSource)
            putString("preferred_source_host", settings.preferredSourceHost)
            putString("preferred_image_host", settings.preferredImageHost)
            putBoolean("auto_update_source_list", settings.autoUpdateSourceList)
        }
    }

    fun loadProgress(comicId: String): LocalReadingProgress? {
        val raw = preferences.getString(progressKey(comicId), null) ?: return null
        return parseProgress(raw)
    }

    fun loadChapterProgress(comicId: String, chapterId: String): LocalReadingProgress? {
        val raw = preferences.getString(progressChapterKey(comicId, chapterId), null) ?: return null
        return parseProgress(raw)
    }

    fun saveProgress(comicId: String, chapterId: String, pageIndex: Int, pageCount: Int) {
        if (comicId.isBlank() || chapterId.isBlank() || pageCount <= 0) return
        val value = listOf(
            chapterId,
            pageIndex.coerceIn(0, pageCount - 1).toString(),
            pageCount.toString(),
            System.currentTimeMillis().toString(),
        ).joinToString("|")
        preferences.edit(commit = true) {
            putString(progressKey(comicId), value)
            putString(progressChapterKey(comicId, chapterId), value)
        }
        trimProgressEntries()
    }

    private fun progressKey(comicId: String) = "reading_progress_$comicId"
    private fun progressChapterKey(comicId: String, chapterId: String) = "reading_progress_chapter_${comicId}_$chapterId"

    private fun parseProgress(raw: String): LocalReadingProgress? {
        val parts = raw.split('|', limit = 4)
        if (parts.size < 3) return null
        return LocalReadingProgress(
            chapterId = parts[0],
            pageIndex = parts[1].toIntOrNull()?.coerceAtLeast(0) ?: 0,
            pageCount = parts[2].toIntOrNull()?.coerceAtLeast(0) ?: 0,
            updatedAt = parts.getOrNull(3)?.toLongOrNull() ?: 0L,
        ).takeIf { it.chapterId.isNotBlank() }
    }

    private fun trimProgressEntries() {
        val progressEntries = preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith("reading_progress_") || value !is String) return@mapNotNull null
            key to (parseProgress(value)?.updatedAt ?: 0L)
        }
        val latestEntries = progressEntries.filterNot { it.first.startsWith(CHAPTER_PROGRESS_PREFIX) }
        val chapterEntries = progressEntries.filter { it.first.startsWith(CHAPTER_PROGRESS_PREFIX) }
        val removeKeys = buildList {
            if (latestEntries.size > MAX_LATEST_PROGRESS_ENTRIES) {
                addAll(latestEntries.sortedBy { it.second }
                    .take(latestEntries.size - MAX_LATEST_PROGRESS_ENTRIES).map { it.first })
            }
            if (chapterEntries.size > MAX_CHAPTER_PROGRESS_ENTRIES) {
                addAll(chapterEntries.sortedBy { it.second }
                    .take(chapterEntries.size - MAX_CHAPTER_PROGRESS_ENTRIES).map { it.first })
            }
        }
        if (removeKeys.isEmpty()) return
        preferences.edit(commit = true) { removeKeys.forEach(::remove) }
    }

    private companion object {
        private const val CHAPTER_PROGRESS_PREFIX = "reading_progress_chapter_"
        private const val MAX_LATEST_PROGRESS_ENTRIES = 80
        private const val MAX_CHAPTER_PROGRESS_ENTRIES = 160
    }
}

data class LocalReadingProgress(
    val chapterId: String,
    val pageIndex: Int,
    val pageCount: Int,
    val updatedAt: Long,
)
