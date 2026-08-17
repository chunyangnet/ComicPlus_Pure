package com.comicplus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay

val LocalComicPlusReduceMotion = staticCompositionLocalOf { false }
val LocalFavoritePendingKeys = staticCompositionLocalOf<Set<String>> { emptySet() }

@Composable
internal fun rememberDelayedBusyIndicator(active: Boolean, delayMillis: Long = 220L): Boolean {
    var visible by remember(active) { mutableStateOf(false) }
    LaunchedEffect(active, delayMillis) {
        if (active) {
            delay(delayMillis)
            visible = true
        }
    }
    return active && visible
}

object ComicPlusTestTags {
    const val HomeFeed = "comicplus_home_feed"
    const val BrowseScreen = "comicplus_browse_screen"
}

fun androidx.compose.ui.Modifier.comicPlusDeviceTestTag(tag: String): androidx.compose.ui.Modifier = this

fun androidx.compose.ui.Modifier.exposeComicPlusDeviceTestTags(): androidx.compose.ui.Modifier = this
