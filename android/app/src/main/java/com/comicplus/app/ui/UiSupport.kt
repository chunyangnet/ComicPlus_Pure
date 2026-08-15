package com.comicplus.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

val LocalComicPlusReduceMotion = staticCompositionLocalOf { false }

object ComicPlusTestTags {
    const val HomeFeed = "comicplus_home_feed"
    const val BrowseScreen = "comicplus_browse_screen"
}

fun androidx.compose.ui.Modifier.comicPlusDeviceTestTag(tag: String): androidx.compose.ui.Modifier = this

fun androidx.compose.ui.Modifier.exposeComicPlusDeviceTestTags(): androidx.compose.ui.Modifier = this
