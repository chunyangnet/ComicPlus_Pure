package com.comicplus.app.ui

import com.comicplus.app.ui.components.canonicalCoverCacheIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicCoverCacheTest {
    @Test
    fun cacheIdentityIgnoresEquivalentCdnHosts() {
        assertEquals(
            canonicalCoverCacheIdentity("https://cdn-a.example/media/albums/123_3x4.jpg"),
            canonicalCoverCacheIdentity("https://cdn-b.example/media/albums/123_3x4.jpg"),
        )
    }

    @Test
    fun cacheIdentityKeepsDifferentCoverPathsSeparate() {
        val first = canonicalCoverCacheIdentity("https://cdn.example/media/albums/123_3x4.jpg")
        val second = canonicalCoverCacheIdentity("https://cdn.example/media/albums/456_3x4.jpg")

        check(first != second)
    }

    @Test
    fun unrelatedImageUrlsKeepTheirHostIdentity() {
        val first = canonicalCoverCacheIdentity("https://cdn-a.example/custom/cover.jpg")
        val second = canonicalCoverCacheIdentity("https://cdn-b.example/custom/cover.jpg")

        check(first != second)
    }
}
