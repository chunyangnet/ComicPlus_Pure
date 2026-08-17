package com.comicplus.pure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PureViewModelCachesTest {
    @Test
    fun commentCacheExpiresSnapshotsAfterTtl() {
        var now = 1_000L
        val cache = CommentPageCache(maxEntries = 2, ttlMillis = 100L, clock = { now })
        val snapshot = snapshot(page = 1)

        cache.put("comic", "chapter", snapshot)
        assertEquals(snapshot, cache.get("comic", "chapter"))

        now += 101L
        assertNull(cache.get("comic", "chapter"))
    }

    @Test
    fun commentCacheEvictsTheLeastRecentlyUsedChapter() {
        var now = 1_000L
        val cache = CommentPageCache(maxEntries = 2, ttlMillis = 10_000L, clock = { now })
        cache.put("comic", "one", snapshot(page = 1))
        cache.put("comic", "two", snapshot(page = 2))
        cache.get("comic", "one")

        now++
        cache.put("comic", "three", snapshot(page = 3))

        assertEquals(1, cache.get("comic", "one")?.page)
        assertNull(cache.get("comic", "two"))
        assertEquals(3, cache.get("comic", "three")?.page)
    }

    private fun snapshot(page: Int) = CommentPageSnapshot(
        page = page,
        total = page.toLong(),
        items = emptyList(),
        hasMore = false,
    )
}
