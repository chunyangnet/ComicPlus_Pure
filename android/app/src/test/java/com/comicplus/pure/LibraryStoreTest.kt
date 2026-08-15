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

    private fun comic(id: String) = ComicUiItem(
        jmId = id,
        title = "Comic $id",
        subtitle = "",
        metric = "",
        accentIndex = id.toInt(),
    )
}
