package com.comicplus.pure

import android.content.Context
import androidx.core.content.edit
import com.comicplus.app.ui.ComicUiItem
import com.comicplus.app.ui.JmFavoriteFolderUiItem
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
    private val memoryFavoriteFolders = mutableMapOf<String, List<JmFavoriteFolderUiItem>>()
    private var favoritesLoaded = false
    private var historyLoaded = false
    private val favoriteFoldersLoaded = mutableSetOf<String>()

    fun loadFavorites(): List<ComicUiItem> = synchronized(lock) { readFavoritesLocked() }

    fun loadHistory(): List<ReadingHistoryItem> = synchronized(lock) {
        readHistoryLocked().sortedByDescending(ReadingHistoryItem::updatedAt)
    }

    /** Folder names are cached per JM account so a cold start does not hide them until sync finishes. */
    fun loadFavoriteFolders(ownerId: String): List<JmFavoriteFolderUiItem> = synchronized(lock) {
        val ownerKey = favoriteFolderOwnerKey(ownerId) ?: return@synchronized defaultFavoriteFolders()
        readFavoriteFoldersLocked(ownerKey)
    }

    fun replaceFavoriteFolders(ownerId: String, folders: List<JmFavoriteFolderUiItem>) = synchronized(lock) {
        val ownerKey = favoriteFolderOwnerKey(ownerId) ?: return@synchronized
        writeFavoriteFoldersLocked(ownerKey, folders)
    }

    /** Returns the new favorite state. */
    fun toggleFavorite(item: ComicUiItem): Boolean = synchronized(lock) {
        val safeItem = item.sanitized() ?: return@synchronized false
        val current = readFavoritesLocked()
        val alreadyFavorite = current.any { it.key == safeItem.key }
        val next = if (alreadyFavorite) current.filterNot { it.key == safeItem.key } else listOf(safeItem) + current
        writeFavoritesLocked(next.take(MAX_FAVORITES))
        !alreadyFavorite
    }

    fun setFavorite(item: ComicUiItem, favorite: Boolean) = synchronized(lock) {
        val safeItem = item.sanitized() ?: return@synchronized
        val current = readFavoritesLocked().filterNot { it.key == safeItem.key }
        val next = if (favorite) listOf(safeItem) + current else current
        writeFavoritesLocked(next.take(MAX_FAVORITES))
    }

    fun replaceFavorites(items: List<ComicUiItem>) = synchronized(lock) {
        writeFavoritesLocked(
            items.mapNotNull(ComicUiItem::sanitized).distinctBy(ComicUiItem::key).take(MAX_FAVORITES),
        )
    }

    fun recordHistory(
        item: ComicUiItem,
        chapterId: String? = null,
        chapterTitle: String? = null,
        pageIndex: Int = 0,
        pageCount: Int = 0,
        updatedAt: Long = System.currentTimeMillis(),
    ) = synchronized(lock) {
        val safeItem = item.sanitized() ?: return@synchronized
        val safeChapterId = chapterId?.trim()?.take(MAX_LIBRARY_ID_LENGTH)?.takeIf(String::isNotBlank)
        val safeChapterTitle = chapterTitle?.trim()?.take(MAX_LIBRARY_FIELD_LENGTH)?.takeIf(String::isNotBlank)
        val safePageCount = pageCount.coerceIn(0, MAX_HISTORY_PAGE_COUNT)
        val current = readHistoryLocked()
        val previous = current.firstOrNull { it.comic.key == safeItem.key }
        val sameChapter = safeChapterId != null && safeChapterId == previous?.chapterId
        val entry = ReadingHistoryItem(
            comic = safeItem,
            chapterId = safeChapterId ?: previous?.chapterId,
            chapterTitle = safeChapterTitle ?: previous?.chapterTitle?.takeIf { safeChapterId == null || sameChapter },
            pageIndex = when {
                safePageCount > 0 -> pageIndex.coerceIn(0, safePageCount - 1)
                safeChapterId != null -> pageIndex.coerceAtLeast(0).coerceAtMost(MAX_HISTORY_PAGE_COUNT - 1)
                else -> previous?.pageIndex ?: 0
            },
            pageCount = when {
                safePageCount > 0 -> safePageCount
                sameChapter -> previous.pageCount
                safeChapterId == null -> previous?.pageCount ?: 0
                else -> 0
            },
            updatedAt = sanitizeTimestamp(updatedAt),
        )
        writeHistoryLocked((listOf(entry) + current.filterNot { it.comic.key == safeItem.key })
            .sortedByDescending(ReadingHistoryItem::updatedAt)
            .take(MAX_HISTORY))
    }

    fun clearHistory() = synchronized(lock) {
        memoryHistory = emptyList()
        historyLoaded = true
        preferences?.edit(commit = true) { remove(HISTORY_KEY) }
    }

    private fun readFavoritesLocked(): List<ComicUiItem> {
        if (favoritesLoaded) return memoryFavorites
        val raw = preferences?.getString(FAVORITES_KEY, null)
        if (raw == null) {
            favoritesLoaded = true
            return memoryFavorites
        }
        val array = parseLibraryArray(raw)
        if (array == null) {
            favoritesLoaded = true
            memoryFavorites = emptyList()
            return memoryFavorites
        }
        val entryLimit = array.length().coerceAtMost(MAX_FAVORITES)
        memoryFavorites = runCatchingNonFatal {
            buildList(entryLimit) {
                for (index in 0 until entryLimit) {
                    array.optJSONObject(index)?.toComicItem()?.let(::add)
                }
            }.distinctBy(ComicUiItem::key)
        }.getOrElse { emptyList() }
        favoritesLoaded = true
        return memoryFavorites
    }

    private fun readHistoryLocked(): List<ReadingHistoryItem> {
        if (historyLoaded) return memoryHistory
        val raw = preferences?.getString(HISTORY_KEY, null)
        if (raw == null) {
            historyLoaded = true
            return memoryHistory
        }
        val array = parseLibraryArray(raw)
        if (array == null) {
            historyLoaded = true
            memoryHistory = emptyList()
            return memoryHistory
        }
        val entryLimit = array.length().coerceAtMost(MAX_HISTORY)
        memoryHistory = runCatchingNonFatal {
            buildList(entryLimit) {
                for (index in 0 until entryLimit) {
                    array.optJSONObject(index)?.toHistoryItem()?.let(::add)
                }
            }.distinctBy { it.comic.key }
        }.getOrElse { emptyList() }
        historyLoaded = true
        return memoryHistory
    }

    private fun readFavoriteFoldersLocked(ownerKey: String): List<JmFavoriteFolderUiItem> {
        if (ownerKey in favoriteFoldersLoaded) return memoryFavoriteFolders[ownerKey].orEmpty()
        val raw = preferences?.getString(favoriteFoldersPreferenceKey(ownerKey), null)
        val array = raw?.let(::parseLibraryArray)
        val folders = if (array == null) {
            defaultFavoriteFolders()
        } else {
            runCatchingNonFatal {
                val entryLimit = array.length().coerceAtMost(MAX_FAVORITE_FOLDERS)
                buildList(entryLimit) {
                    for (index in 0 until entryLimit) {
                        array.optJSONObject(index)?.toFavoriteFolder()?.let(::add)
                    }
                }
            }.getOrElse { emptyList() }
                .let(::normalizeFavoriteFolders)
        }
        memoryFavoriteFolders[ownerKey] = folders
        favoriteFoldersLoaded += ownerKey
        return folders
    }

    private fun writeFavoritesLocked(items: List<ComicUiItem>) {
        memoryFavorites = items
        favoritesLoaded = true
        if (preferences == null) return
        val value = JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()
        preferences.edit(commit = true) { putString(FAVORITES_KEY, value) }
    }

    private fun writeHistoryLocked(items: List<ReadingHistoryItem>) {
        memoryHistory = items
        historyLoaded = true
        if (preferences == null) return
        val value = JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()
        preferences.edit(commit = true) { putString(HISTORY_KEY, value) }
    }

    private fun writeFavoriteFoldersLocked(ownerKey: String, folders: List<JmFavoriteFolderUiItem>) {
        val normalized = normalizeFavoriteFolders(folders)
        memoryFavoriteFolders[ownerKey] = normalized
        favoriteFoldersLoaded += ownerKey
        if (preferences == null) return
        val value = JSONArray().apply { normalized.forEach { put(it.toJson()) } }.toString()
        preferences.edit(commit = true) { putString(favoriteFoldersPreferenceKey(ownerKey), value) }
    }

    private fun ComicUiItem.toJson(): JSONObject = JSONObject()
        .put("id", jmId.take(MAX_LIBRARY_ID_LENGTH))
        .put("title", title.take(MAX_LIBRARY_TITLE_LENGTH))
        .put("subtitle", subtitle.take(MAX_LIBRARY_FIELD_LENGTH))
        .put("metric", metric.take(MAX_LIBRARY_FIELD_LENGTH))
        .put("accent", accentIndex)
        .put("cover", coverUrl?.take(MAX_LIBRARY_URL_LENGTH) ?: JSONObject.NULL)
        .put("source", source.take(MAX_LIBRARY_FIELD_LENGTH))

    private fun JSONObject.toComicItem(): ComicUiItem? {
        val id = optString("id").trim().take(MAX_LIBRARY_ID_LENGTH).takeIf(String::isNotBlank) ?: return null
        return ComicUiItem(
            jmId = id,
            title = optString("title", "JM$id").take(MAX_LIBRARY_TITLE_LENGTH),
            subtitle = optString("subtitle").take(MAX_LIBRARY_FIELD_LENGTH),
            metric = optString("metric").take(MAX_LIBRARY_FIELD_LENGTH),
            accentIndex = optInt("accent", 0),
            coverUrl = optString("cover")
                .take(MAX_LIBRARY_URL_LENGTH)
                .takeIf { it.isNotBlank() && it != "null" }
                ?.let { JmGateway.normalizeRemoteHttpsUrl(it) },
            source = optString("source", "jm").take(MAX_LIBRARY_FIELD_LENGTH).ifBlank { "jm" },
        )
    }

    private fun ReadingHistoryItem.toJson(): JSONObject = JSONObject()
        .put("comic", comic.toJson())
        .put("chapterId", chapterId?.take(MAX_LIBRARY_ID_LENGTH) ?: JSONObject.NULL)
        .put("chapterTitle", chapterTitle?.take(MAX_LIBRARY_FIELD_LENGTH) ?: JSONObject.NULL)
        .put("pageIndex", pageIndex)
        .put("pageCount", pageCount)
        .put("updatedAt", sanitizeTimestamp(updatedAt))

    private fun JSONObject.toHistoryItem(): ReadingHistoryItem? {
        val comic = optJSONObject("comic")?.toComicItem() ?: return null
        val pageCount = optInt("pageCount", 0).coerceIn(0, MAX_HISTORY_PAGE_COUNT)
        return ReadingHistoryItem(
            comic = comic,
            chapterId = optString("chapterId").take(MAX_LIBRARY_ID_LENGTH).takeIf { it.isNotBlank() && it != "null" },
            chapterTitle = optString("chapterTitle").take(MAX_LIBRARY_FIELD_LENGTH).takeIf { it.isNotBlank() && it != "null" },
            pageIndex = optInt("pageIndex", 0).coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
            pageCount = pageCount,
            updatedAt = sanitizeTimestamp(optLong("updatedAt", 0L)),
        )
    }

    companion object {
        const val PREFERENCES_NAME = "comicplus_pure_library"
        const val FAVORITES_KEY = "favorites_v1"
        const val HISTORY_KEY = "history_v1"
        private const val FAVORITE_FOLDERS_KEY_PREFIX = "favorite_folders_v1_"
        private const val MAX_FAVORITE_FOLDERS = 100
        const val MAX_FAVORITES = 200
        const val MAX_HISTORY = 100
        internal const val MAX_LIBRARY_JSON_CHARS = 512 * 1024
        internal const val MAX_LIBRARY_ID_LENGTH = 128
        internal const val MAX_LIBRARY_TITLE_LENGTH = 500
        internal const val MAX_LIBRARY_FIELD_LENGTH = 512
        internal const val MAX_LIBRARY_URL_LENGTH = 2_048
        internal const val MAX_HISTORY_PAGE_COUNT = 20_000
        internal const val MAX_FUTURE_TIMESTAMP_MILLIS = 24L * 60L * 60L * 1_000L
    }

    fun replaceHistory(items: List<ReadingHistoryItem>) = synchronized(lock) {
        writeHistoryLocked(
            items.mapNotNull(ReadingHistoryItem::sanitized)
                .distinctBy { it.comic.key }
                .sortedByDescending(ReadingHistoryItem::updatedAt)
                .take(MAX_HISTORY),
        )
    }

    private fun JmFavoriteFolderUiItem.toJson(): JSONObject = JSONObject()
        .put("id", id.take(MAX_LIBRARY_ID_LENGTH))
        .put("name", name.take(MAX_LIBRARY_FIELD_LENGTH))

    private fun JSONObject.toFavoriteFolder(): JmFavoriteFolderUiItem? {
        val id = optString("id").trim().take(MAX_LIBRARY_ID_LENGTH).takeIf(String::isNotBlank) ?: return null
        val name = optString("name").trim().take(MAX_LIBRARY_FIELD_LENGTH)
            .ifBlank { if (id == "0") "全部" else "收藏夹 $id" }
        return JmFavoriteFolderUiItem(id = id, name = name)
    }
    private fun favoriteFolderOwnerKey(ownerId: String): String? {
        val normalized = ownerId.trim().take(MAX_LIBRARY_ID_LENGTH)
        if (normalized.isBlank()) return null
        return normalized.encodeToByteArray().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun favoriteFoldersPreferenceKey(ownerKey: String): String =
        "$FAVORITE_FOLDERS_KEY_PREFIX$ownerKey"

    private fun defaultFavoriteFolders(): List<JmFavoriteFolderUiItem> =
        listOf(JmFavoriteFolderUiItem(id = "0", name = "全部"))

    private fun normalizeFavoriteFolders(folders: List<JmFavoriteFolderUiItem>): List<JmFavoriteFolderUiItem> =
        (defaultFavoriteFolders() + folders)
            .map { folder ->
                val id = folder.id.trim().take(MAX_LIBRARY_ID_LENGTH)
                JmFavoriteFolderUiItem(
                    id = id,
                    name = folder.name.trim().take(MAX_LIBRARY_FIELD_LENGTH)
                        .ifBlank { if (id == "0") "全部" else "收藏夹 $id" },
                )
            }
            .filter { it.id.isNotBlank() }
            .distinctBy(JmFavoriteFolderUiItem::id)
            .take(MAX_FAVORITE_FOLDERS)
            .ifEmpty(::defaultFavoriteFolders)
}

private fun ComicUiItem.sanitized(): ComicUiItem? {
    val id = jmId.trim().take(LibraryStore.MAX_LIBRARY_ID_LENGTH).takeIf(String::isNotBlank) ?: return null
    return copy(
        jmId = id,
        title = title.trim().take(LibraryStore.MAX_LIBRARY_TITLE_LENGTH).ifBlank { "JM$id" },
        subtitle = subtitle.trim().take(LibraryStore.MAX_LIBRARY_FIELD_LENGTH),
        metric = metric.trim().take(LibraryStore.MAX_LIBRARY_FIELD_LENGTH),
        coverUrl = coverUrl?.trim()?.take(LibraryStore.MAX_LIBRARY_URL_LENGTH)
            ?.takeIf(String::isNotBlank)
            ?.let { JmGateway.normalizeRemoteHttpsUrl(it) },
        source = source.trim().take(LibraryStore.MAX_LIBRARY_FIELD_LENGTH).ifBlank { "jm" },
    )
}

private fun ReadingHistoryItem.sanitized(): ReadingHistoryItem? {
    val safeComic = comic.sanitized() ?: return null
    val safePageCount = pageCount.coerceIn(0, LibraryStore.MAX_HISTORY_PAGE_COUNT)
    return copy(
        comic = safeComic,
        chapterId = chapterId?.trim()?.take(LibraryStore.MAX_LIBRARY_ID_LENGTH)?.takeIf(String::isNotBlank),
        chapterTitle = chapterTitle?.trim()?.take(LibraryStore.MAX_LIBRARY_FIELD_LENGTH)?.takeIf(String::isNotBlank),
        pageIndex = pageIndex.coerceIn(0, (safePageCount - 1).coerceAtLeast(0)),
        pageCount = safePageCount,
        updatedAt = sanitizeTimestamp(updatedAt),
    )
}

private fun sanitizeTimestamp(value: Long): Long {
    val now = System.currentTimeMillis().coerceAtLeast(0L)
    val maximum = if (now > Long.MAX_VALUE - LibraryStore.MAX_FUTURE_TIMESTAMP_MILLIS) {
        Long.MAX_VALUE
    } else {
        now + LibraryStore.MAX_FUTURE_TIMESTAMP_MILLIS
    }
    return value.coerceIn(0L, maximum)
}

internal fun boundedLibraryEntryCount(raw: String, maxItems: Int): Int? {
    if (maxItems < 0) return null
    return parseLibraryArray(raw)?.length()?.coerceAtMost(maxItems)
}

private fun parseLibraryArray(raw: String): JSONArray? {
    if (raw.length !in 1..LibraryStore.MAX_LIBRARY_JSON_CHARS) return null
    return runCatchingNonFatal { JSONArray(raw) }.getOrNull()
}
