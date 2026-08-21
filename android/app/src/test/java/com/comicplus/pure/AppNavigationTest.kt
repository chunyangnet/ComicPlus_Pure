package com.comicplus.pure

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationTest {
    @Test
    fun tagSearchIsPlacedAboveTheDetailLayer() {
        assertEquals(
            listOf(AppLayer.Main, AppLayer.Detail, AppLayer.Search),
            appNavigationStack(
                settingsVisible = false,
                officialBrowseVisible = false,
                searchVisible = true,
                searchReturnToDetail = true,
                detailVisible = true,
                readerVisible = false,
            ),
        )
    }

    @Test
    fun ordinarySearchKeepsDetailAboveItsSourceSearchLayer() {
        assertEquals(
            listOf(AppLayer.Main, AppLayer.Search, AppLayer.Detail),
            appNavigationStack(
                settingsVisible = false,
                officialBrowseVisible = false,
                searchVisible = true,
                searchReturnToDetail = false,
                detailVisible = true,
                readerVisible = false,
            ),
        )
    }
}
