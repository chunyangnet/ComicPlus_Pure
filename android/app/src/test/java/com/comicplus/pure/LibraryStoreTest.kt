package com.comicplus.pure

import com.comicplus.app.ui.ComicUiItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStoreTest {
    @Test
    fun favoriteTogglePersistsTheNewStateInMemory() {
        val store = LibraryStore()
        val comic = comic("12")

        assertTrue(store.toggleFavorite(comic))
        assertEquals(listOf(comic), store.loadFavorites())
        assertFalse(store.toggleFavorite(comic))
        assertTrue(store.loadFavorites().isEmpty())
    }

    @Test
    fun historyKeepsLatestProgressAndMovesComicToTheFront() {
        val store = LibraryStore()
        store.recordHistory(comic("1"), updatedAt = 10L)
        store.recordHistory(comic("2"), updatedAt = 20L)
        store.recordHistory(
            item = comic("1"),
            chapterId = "100",
            chapterTitle = "第 3 话",
            pageIndex = 4,
            pageCount = 10,
            updatedAt = 30L,
        )

        val history = store.loadHistory()
        assertEquals(listOf("1", "2"), history.map { it.comic.jmId })
        assertEquals("100", history.first().chapterId)
        assertEquals(4, history.first().pageIndex)
        assertEquals(10, history.first().pageCount)
    }

    @Test
    fun historyClampsCorruptProgressToTheChapterBounds() {
        val store = LibraryStore()

        store.recordHistory(
            item = comic("7"),
            chapterId = "70",
            pageIndex = Int.MAX_VALUE,
            pageCount = 3,
        )

        assertEquals(2, store.loadHistory().single().pageIndex)
        assertEquals(3, store.loadHistory().single().pageCount)
    }

    @Test
    fun favoriteSnapshotsBoundFieldsAndRejectBlankIds() {
        val store = LibraryStore()
        val oversized = comic("9").copy(title = "x".repeat(2_000), subtitle = "y".repeat(2_000))

        assertTrue(store.toggleFavorite(oversized))
        assertEquals(500, store.loadFavorites().single().title.length)
        assertEquals(512, store.loadFavorites().single().subtitle.length)
        assertFalse(store.toggleFavorite(oversized.copy(jmId = "  ")))
    }

    @Test
    fun persistedSnapshotsHaveSizeAndEntryLimits() {
        val oversizedArray = "[" + List(250) { "{}" }.joinToString(",") + "]"

        assertEquals(200, boundedLibraryEntryCount(oversizedArray, 200))
        assertEquals(0, boundedLibraryEntryCount("[]", 200))
        assertEquals(null, boundedLibraryEntryCount("not-json", 200))
        assertEquals(null, boundedLibraryEntryCount("x".repeat(512 * 1024 + 1), 200))
    }

    @Test
    fun replacingAFullSnapshotMakesRapidUiMutationsConverge() {
        val store = LibraryStore()
        store.setFavorite(comic("1"), true)

        store.replaceFavorites(listOf(comic("2"), comic("2"), comic("3")))

        assertEquals(listOf("2", "3"), store.loadFavorites().map(ComicUiItem::jmId))
    }

    @Test
    fun persistedCoverUrlsCannotSwitchToLocalOrCleartextSchemes() {
        val store = LibraryStore()
        store.toggleFavorite(comic("10").copy(coverUrl = "file:///data/user/0/private.jpg"))
        store.replaceFavorites(listOf(comic("10"), comic("11").copy(coverUrl = "http://example.com/cover.jpg")))
        assertEquals(listOf(null, null), store.loadFavorites().map(ComicUiItem::coverUrl))
    }

    private fun comic(id: String) = ComicUiItem(
        jmId = id,
        title = "Comic $id",
        subtitle = "",
        metric = "",
        accentIndex = id.toInt(),
    )
}
