package com.comicplus.pure

import android.content.Context
import androidx.core.content.edit
import com.comicplus.app.ui.ComicUiItem
import com.comicplus.app.ui.ReadingHistoryItem
import com.comicplus.app.ui.key
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small, dependency-free local library store. A JSON snapshot is used instead of
 * a StringSet so insertion order and the cover metadata survive process restarts.
 * Passing a null context gives tests and previews an in-memory store.
 */
class LibraryStore(context: Context? = null) {
    private val preferences = context?.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private var memoryFavorites: List<ComicUiItem> = emptyList()
    private var memoryHistory: List<ReadingHistoryItem> = emptyList()

    fun loadFavorites(): List<ComicUiItem> = synchronized(lock) { readFavoritesLocked() }

    fun loadHistory(): List<ReadingHistoryItem> = synchronized(lock) {
        readHistoryLocked().sortedByDescending(ReadingHistoryItem::updatedAt)
    }

    /** Returns the new favorite state. */
    fun toggleFavorite(item: ComicUiItem): Boolean = synchronized(lock) {
        val current = readFavoritesLocked()
        val alreadyFavorite = current.any { it.key == item.key }
        val next = if (alreadyFavorite) current.filterNot { it.key == item.key } else listOf(item) + current
        writeFavoritesLocked(next.take(MAX_FAVORITES))
        !alreadyFavorite
    }

    fun setFavorite(item: ComicUiItem, favorite: Boolean) = synchronized(lock) {
        val current = readFavoritesLocked().filterNot { it.key == item.key }
        val next = if (favorite) listOf(item) + current else current
        writeFavoritesLocked(next.take(MAX_FAVORITES))
    }

    fun recordHistory(
        item: ComicUiItem,
        chapterId: String? = null,
        chapterTitle: String? = null,
        pageIndex: Int = 0,
        pageCount: Int = 0,
        updatedAt: Long = System.currentTimeMillis(),
    ) = synchronized(lock) {
        val current = readHistoryLocked()
        val previous = current.firstOrNull { it.comic.key == item.key }
        val sameChapter = chapterId != null && chapterId == previous?.chapterId
        val entry = ReadingHistoryItem(
            comic = item,
            chapterId = chapterId ?: previous?.chapterId,
            chapterTitle = chapterTitle ?: previous?.chapterTitle?.takeIf { chapterId == null || sameChapter },
            pageIndex = when {
                pageCount > 0 -> pageIndex.coerceIn(0, pageCount - 1)
                chapterId != null -> pageIndex.coerceAtLeast(0)
                else -> previous?.pageIndex ?: 0
            },
            pageCount = when {
                pageCount > 0 -> pageCount
                sameChapter -> previous?.pageCount ?: 0
                chapterId == null -> previous?.pageCount ?: 0
                else -> 0
            },
            updatedAt = updatedAt,
        )
        writeHistoryLocked((listOf(entry) + current.filterNot { it.comic.key == item.key })
            .sortedByDescending(ReadingHistoryItem::updatedAt)
            .take(MAX_HISTORY))
    }

    fun clearHistory() = synchronized(lock) {
        memoryHistory = emptyList()
        preferences?.edit(commit = true) { remove(HISTORY_KEY) }
    }

    private fun readFavoritesLocked(): List<ComicUiItem> {
        val raw = preferences?.getString(FAVORITES_KEY, null)
        if (raw == null) return memoryFavorites
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toComicItem()?.let(::add)
                }
            }.distinctBy(ComicUiItem::key)
        }.getOrElse { emptyList() }
    }

    private fun readHistoryLocked(): List<ReadingHistoryItem> {
        val raw = preferences?.getString(HISTORY_KEY, null)
        if (raw == null) return memoryHistory
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toHistoryItem()?.let(::add)
                }
            }.distinctBy { it.comic.key }
        }.getOrElse { emptyList() }
    }

    private fun writeFavoritesLocked(items: List<ComicUiItem>) {
        memoryFavorites = items
        if (preferences == null) return
        val value = JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()
        preferences.edit(commit = true) { putString(FAVORITES_KEY, value) }
    }

    private fun writeHistoryLocked(items: List<ReadingHistoryItem>) {
        memoryHistory = items
        if (preferences == null) return
        val value = JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()
        preferences.edit(commit = true) { putString(HISTORY_KEY, value) }
    }

    private fun ComicUiItem.toJson(): JSONObject = JSONObject()
        .put("id", jmId)
        .put("title", title)
        .put("subtitle", subtitle)
        .put("metric", metric)
        .put("accent", accentIndex)
        .put("cover", coverUrl ?: JSONObject.NULL)
        .put("source", source)

    private fun JSONObject.toComicItem(): ComicUiItem? {
        val id = optString("id").trim().takeIf(String::isNotBlank) ?: return null
        return ComicUiItem(
            jmId = id,
            title = optString("title", "JM$id"),
            subtitle = optString("subtitle"),
            metric = optString("metric"),
            accentIndex = optInt("accent", 0),
            coverUrl = optString("cover").takeIf { it.isNotBlank() && it != "null" },
            source = optString("source", "jm").ifBlank { "jm" },
        )
    }

    private fun ReadingHistoryItem.toJson(): JSONObject = JSONObject()
        .put("comic", comic.toJson())
        .put("chapterId", chapterId ?: JSONObject.NULL)
        .put("chapterTitle", chapterTitle ?: JSONObject.NULL)
        .put("pageIndex", pageIndex)
        .put("pageCount", pageCount)
        .put("updatedAt", updatedAt)

    private fun JSONObject.toHistoryItem(): ReadingHistoryItem? {
        val comic = optJSONObject("comic")?.toComicItem() ?: return null
        return ReadingHistoryItem(
            comic = comic,
            chapterId = optString("chapterId").takeIf { it.isNotBlank() && it != "null" },
            chapterTitle = optString("chapterTitle").takeIf { it.isNotBlank() && it != "null" },
            pageIndex = optInt("pageIndex", 0).coerceAtLeast(0),
            pageCount = optInt("pageCount", 0).coerceAtLeast(0),
            updatedAt = optLong("updatedAt", 0L),
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "comicplus_pure_library"
        const val FAVORITES_KEY = "favorites_v1"
        const val HISTORY_KEY = "history_v1"
        const val MAX_FAVORITES = 200
        const val MAX_HISTORY = 100
    }
}
