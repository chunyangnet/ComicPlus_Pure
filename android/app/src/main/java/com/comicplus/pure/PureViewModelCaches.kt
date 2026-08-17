package com.comicplus.pure

import com.comicplus.app.ui.JmCommentUiItem

internal data class CommentPageSnapshot(
    val page: Int,
    val total: Long,
    val items: List<JmCommentUiItem>,
    val hasMore: Boolean,
)

/** A tiny synchronized access-order cache for ViewModel snapshots. */
internal class SynchronizedLruCache<K, V>(maxEntries: Int) {
    private val capacity = maxEntries.coerceAtLeast(1)
    private val values = object : LinkedHashMap<K, V>(capacity + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > capacity
    }

    @Synchronized
    operator fun get(key: K): V? = values[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        values[key] = value
    }

    @Synchronized
    fun remove(key: K, expected: V): Boolean = values.remove(key, expected)
}

internal class CommentPageCache(
    maxEntries: Int = 24,
    private val ttlMillis: Long = 5L * 60L * 1_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Key(val comicId: String, val chapterId: String)
    private data class CachedPage(val page: CommentPageSnapshot, val cachedAt: Long)

    private val pages = SynchronizedLruCache<Key, CachedPage>(maxEntries)

    fun get(comicId: String, chapterId: String): CommentPageSnapshot? {
        val key = Key(comicId, chapterId)
        val cached = pages[key] ?: return null
        if (clock() - cached.cachedAt <= ttlMillis) return cached.page
        pages.remove(key, cached)
        return null
    }

    fun put(comicId: String, chapterId: String, page: CommentPageSnapshot) {
        pages[Key(comicId, chapterId)] = CachedPage(page, clock())
    }
}

internal fun JmCommentPage.toUiSnapshot(): CommentPageSnapshot = CommentPageSnapshot(
    page = page,
    total = total,
    items = comments.map(JmComment::toUiItem).distinctBy(JmCommentUiItem::id),
    hasMore = hasMore,
)
