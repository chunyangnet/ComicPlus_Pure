package com.comicplus.app.ui

import com.comicplus.app.ui.components.collapsedTagItemCount
import org.junit.Assert.assertEquals
import org.junit.Test

class TagLayoutTest {
    @Test
    fun collapsedTagsStopAfterTheConfiguredRows() {
        assertEquals(
            6,
            collapsedTagItemCount(
                itemWidths = List(8) { 40 },
                availableWidth = 100,
                maxRows = 3,
                horizontalSpacing = 8,
            ),
        )
    }

    @Test
    fun oversizedTagStillOccupiesOneRow() {
        assertEquals(
            3,
            collapsedTagItemCount(
                itemWidths = listOf(160, 40, 40),
                availableWidth = 100,
                maxRows = 2,
                horizontalSpacing = 8,
            ),
        )
    }

    @Test
    fun invalidConstraintsShowNoCollapsedTags() {
        assertEquals(0, collapsedTagItemCount(listOf(40), 0, 3, 8))
        assertEquals(0, collapsedTagItemCount(listOf(40), 100, 0, 8))
    }
}
