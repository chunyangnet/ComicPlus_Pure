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
    private val progressLock = Any()

    fun load(): AppSettings {
        val storedTurboMode = preferences.getBoolean("reader_turbo_mode", false)
        val storedDataSaver = preferences.getBoolean("data_saver", false)
        val (normalizedTurboMode, normalizedDataSaver) = normalizeReaderModes(
            storedTurboMode,
            storedDataSaver,
        )
        val storedPrefetchPages = preferences.getInt("prefetch", 3).coerceIn(0, 6)
        val storedPrefetchMode = preferences.getString("prefetch_mode", null)
            ?.let { runCatching { ReaderPrefetchMode.valueOf(it) }.getOrNull() }
            ?: when (storedPrefetchPages) {
                0 -> ReaderPrefetchMode.Conservative
                5, 6 -> ReaderPrefetchMode.Aggressive
                else -> ReaderPrefetchMode.Smart
            }
        val normalizedPrefetchMode = normalizeReaderPrefetchMode(storedPrefetchMode, normalizedDataSaver)
        val normalizedPrefetchPages = if (
            normalizedPrefetchMode != storedPrefetchMode && normalizedDataSaver
        ) 1 else storedPrefetchPages
        // Older/corrupt preference files can contain both flags. Persist the
        // normalized pair so the contradiction is not reintroduced on restart.
        if (
            storedTurboMode != normalizedTurboMode ||
            storedDataSaver != normalizedDataSaver ||
            storedPrefetchMode != normalizedPrefetchMode ||
            storedPrefetchPages != normalizedPrefetchPages
        ) {
            preferences.edit {
                putBoolean("reader_turbo_mode", normalizedTurboMode)
                putBoolean("data_saver", normalizedDataSaver)
                putString("prefetch_mode", normalizedPrefetchMode.name)
                putInt("prefetch", normalizedPrefetchPages)
            }
        }
        return AppSettings(
            paletteKey = preferences.getString("palette", "ocean").orEmpty().take(MAX_SETTING_TEXT_LENGTH).ifBlank { "ocean" },
            darkMode = preferences.getBoolean("dark_mode", false),
            chapterDescending = preferences.getBoolean("chapter_descending", false),
            readerPrefetchPages = normalizedPrefetchPages,
            readerPrefetchMode = normalizedPrefetchMode,
            readerPageSpacingDp = preferences.getInt("page_spacing", 0).coerceIn(0, 16),
            readerMode = preferences.getString("reader_mode", ReaderMode.Vertical.name)
                ?.let { runCatching { ReaderMode.valueOf(it) }.getOrNull() } ?: ReaderMode.Vertical,
            readerDirection = preferences.getString("reader_direction", ReaderDirection.LeftToRight.name)
                ?.let { runCatching { ReaderDirection.valueOf(it) }.getOrNull() } ?: ReaderDirection.LeftToRight,
            readerImageQuality = preferences.getString("reader_image_quality", ReaderImageQuality.Medium.name)
                ?.let { runCatching { ReaderImageQuality.valueOf(it) }.getOrNull() } ?: ReaderImageQuality.Medium,
            readerTurboMode = normalizedTurboMode,
            readerBrightnessPercent = preferences.getInt("brightness", 0).coerceIn(0, 100),
            keepScreenOn = preferences.getBoolean("keep_screen_on", true),
            reduceMotion = preferences.getBoolean("reduce_motion", false),
            tapToToggleReaderMenu = preferences.getBoolean("tap_menu", true),
            autoResumeReading = preferences.getBoolean("auto_resume", true),
            dataSaver = normalizedDataSaver,
            autoSelectSource = preferences.getBoolean("auto_select_source", true),
            preferredSourceHost = preferences.getString("preferred_source_host", null)
                ?.take(MAX_HOST_LENGTH)?.takeIf(String::isNotBlank),
            preferredImageHost = preferences.getString("preferred_image_host", null)
                ?.take(MAX_HOST_LENGTH)?.takeIf(String::isNotBlank),
            autoUpdateSourceList = preferences.getBoolean("auto_update_source_list", true),
        )
    }

    fun save(settings: AppSettings) {
        val (normalizedTurboMode, normalizedDataSaver) = normalizeReaderModes(
            settings.readerTurboMode,
            settings.dataSaver,
        )
        preferences.edit {
            putString("palette", settings.paletteKey.take(MAX_SETTING_TEXT_LENGTH))
            putBoolean("dark_mode", settings.darkMode)
            putBoolean("chapter_descending", settings.chapterDescending)
            putInt("prefetch", settings.readerPrefetchPages.coerceIn(0, 6))
            putString("prefetch_mode", settings.readerPrefetchMode.name)
            putInt("page_spacing", settings.readerPageSpacingDp.coerceIn(0, 16))
            putString("reader_mode", settings.readerMode.name)
            putString("reader_direction", settings.readerDirection.name)
            putString("reader_image_quality", settings.readerImageQuality.name)
            putBoolean("reader_turbo_mode", normalizedTurboMode)
            putInt("brightness", settings.readerBrightnessPercent.coerceIn(0, 100))
            putBoolean("keep_screen_on", settings.keepScreenOn)
            putBoolean("reduce_motion", settings.reduceMotion)
            putBoolean("tap_menu", settings.tapToToggleReaderMenu)
            putBoolean("auto_resume", settings.autoResumeReading)
            putBoolean("data_saver", normalizedDataSaver)
            putBoolean("auto_select_source", settings.autoSelectSource)
            putString("preferred_source_host", settings.preferredSourceHost?.take(MAX_HOST_LENGTH))
            putString("preferred_image_host", settings.preferredImageHost?.take(MAX_HOST_LENGTH))
            putBoolean("auto_update_source_list", settings.autoUpdateSourceList)
        }
    }

    fun loadProgress(comicId: String): LocalReadingProgress? {
        if (!isSafeProgressId(comicId)) return null
        val raw = preferences.getString(progressKey(comicId), null) ?: return null
        return parseProgress(raw)
    }

    fun loadChapterProgress(comicId: String, chapterId: String): LocalReadingProgress? {
        if (!isSafeProgressId(comicId) || !isSafeProgressId(chapterId)) return null
        val raw = preferences.getString(progressChapterKey(comicId, chapterId), null) ?: return null
        return parseProgress(raw)
    }

    fun saveProgress(comicId: String, chapterId: String, pageIndex: Int, pageCount: Int) {
        if (!isSafeProgressId(comicId) || !isSafeProgressId(chapterId) || pageCount !in 1..MAX_PROGRESS_PAGE_COUNT) return
        synchronized(progressLock) {
            val updatedAt = System.currentTimeMillis()
            val value = listOf(
                chapterId,
                pageIndex.coerceIn(0, pageCount - 1).toString(),
                pageCount.toString(),
                updatedAt.toString(),
            ).joinToString("|")
            val pending = mapOf(
                progressKey(comicId) to value,
                progressChapterKey(comicId, chapterId) to value,
            )
            val progressEntries = preferences.all.asSequence().mapNotNull { (key, raw) ->
                if (!key.startsWith("reading_progress_") || raw !is String) return@mapNotNull null
                key to (parseProgress(raw)?.updatedAt ?: 0L)
            }.take(MAX_PROGRESS_SCAN_ENTRIES).toMap(HashMap()).toMutableMap().apply {
                pending.forEach { (key, raw) -> this[key] = parseProgress(raw)?.updatedAt ?: 0L }
            }
            val removeKeys = progressKeysToTrim(progressEntries, pending.keys)
            preferences.edit(commit = true) {
                pending.forEach { (key, raw) -> putString(key, raw) }
                removeKeys.forEach(::remove)
            }
        }
    }

    private fun progressKey(comicId: String) = "reading_progress_$comicId"
    private fun progressChapterKey(comicId: String, chapterId: String) = "reading_progress_chapter_${comicId}_$chapterId"

    private fun parseProgress(raw: String): LocalReadingProgress? = parseStoredProgress(raw)

    private fun progressKeysToTrim(progressEntries: Map<String, Long>, protectedKeys: Set<String> = emptySet()): List<String> {
        val latestEntries = progressEntries.filterNot { it.key.startsWith(CHAPTER_PROGRESS_PREFIX) }
        val chapterEntries = progressEntries.filter { it.key.startsWith(CHAPTER_PROGRESS_PREFIX) }
        return buildList {
            if (latestEntries.size > MAX_LATEST_PROGRESS_ENTRIES) {
                addAll(latestEntries.entries.filterNot { it.key in protectedKeys }.sortedBy { it.value }
                    .take(latestEntries.size - MAX_LATEST_PROGRESS_ENTRIES).map { it.key })
            }
            if (chapterEntries.size > MAX_CHAPTER_PROGRESS_ENTRIES) {
                addAll(chapterEntries.entries.filterNot { it.key in protectedKeys }.sortedBy { it.value }
                    .take(chapterEntries.size - MAX_CHAPTER_PROGRESS_ENTRIES).map { it.key })
            }
        }
    }

    companion object {
        private const val CHAPTER_PROGRESS_PREFIX = "reading_progress_chapter_"
        private const val MAX_LATEST_PROGRESS_ENTRIES = 80
        private const val MAX_CHAPTER_PROGRESS_ENTRIES = 160
        private const val MAX_PROGRESS_PAGE_COUNT = 20_000
        private const val MAX_PROGRESS_SCAN_ENTRIES = 1_000
        private const val MAX_SETTING_TEXT_LENGTH = 64
        private const val MAX_HOST_LENGTH = 253
        private const val MAX_PROGRESS_VALUE_LENGTH = 128
        private const val MAX_FUTURE_PROGRESS_MILLIS = 24L * 60L * 60L * 1_000L
        private val SAFE_PROGRESS_ID = Regex("^\\d{1,12}$")

        internal fun normalizeReaderModes(readerTurboMode: Boolean, dataSaver: Boolean): Pair<Boolean, Boolean> =
            if (readerTurboMode && dataSaver) true to false else readerTurboMode to dataSaver

        internal fun normalizeReaderPrefetchMode(
            mode: ReaderPrefetchMode,
            dataSaver: Boolean,
        ): ReaderPrefetchMode = if (dataSaver && mode == ReaderPrefetchMode.UltraAggressive) {
            ReaderPrefetchMode.Conservative
        } else {
            mode
        }

        internal fun parseStoredProgress(
            raw: String,
            nowMillis: Long = System.currentTimeMillis(),
        ): LocalReadingProgress? {
            if (raw.length !in 1..MAX_PROGRESS_VALUE_LENGTH || raw.count { it == '|' } !in 2..3) return null
            val parts = raw.split('|', limit = 4)
            if (parts.size < 3) return null
            val safeNow = nowMillis.coerceAtLeast(0L)
            val maxTimestamp = if (safeNow > Long.MAX_VALUE - MAX_FUTURE_PROGRESS_MILLIS) {
                Long.MAX_VALUE
            } else {
                safeNow + MAX_FUTURE_PROGRESS_MILLIS
            }
            return LocalReadingProgress(
                chapterId = parts[0],
                pageIndex = parts[1].toIntOrNull()?.coerceAtLeast(0) ?: 0,
                pageCount = parts[2].toIntOrNull()?.coerceIn(0, MAX_PROGRESS_PAGE_COUNT) ?: 0,
                updatedAt = parts.getOrNull(3)?.toLongOrNull()
                    ?.coerceIn(0L, maxTimestamp) ?: 0L,
            ).let { progress ->
                progress.copy(pageIndex = progress.pageIndex.coerceIn(0, (progress.pageCount - 1).coerceAtLeast(0)))
            }.takeIf { it.chapterId.matches(SAFE_PROGRESS_ID) }
        }
    }

    private fun isSafeProgressId(value: String): Boolean = SAFE_PROGRESS_ID.matches(value)
}

data class LocalReadingProgress(
    val chapterId: String,
    val pageIndex: Int,
    val pageCount: Int,
    val updatedAt: Long,
)
