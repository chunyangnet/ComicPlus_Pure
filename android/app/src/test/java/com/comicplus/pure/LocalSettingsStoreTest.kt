package com.comicplus.pure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalSettingsStoreTest {
    @Test
    fun storedProgressClampsPageAndFutureTimestamp() {
        val progress = LocalSettingsStore.parseStoredProgress(
            raw = "123|999999|3|999999999999",
            nowMillis = 1_000L,
        )!!

        assertEquals(2, progress.pageIndex)
        assertEquals(3, progress.pageCount)
        assertEquals(86_401_000L, progress.updatedAt)
    }

    @Test
    fun storedProgressRejectsInjectedOrOversizedChapterIds() {
        assertNull(LocalSettingsStore.parseStoredProgress("12|34|1|2|3"))
        assertNull(LocalSettingsStore.parseStoredProgress("1234567890123|0|1|0"))
        assertNull(LocalSettingsStore.parseStoredProgress("chapter|0|1|0"))
    }

    @Test
    fun storedProgressHandlesExtremeClockWithoutOverflow() {
        val progress = LocalSettingsStore.parseStoredProgress(
            raw = "123|0|1|9223372036854775807",
            nowMillis = Long.MAX_VALUE,
        )!!

        assertEquals(Long.MAX_VALUE, progress.updatedAt)
    }

    @Test
    fun corruptedMutuallyExclusiveReaderModesPreferTurbo() {
        assertEquals(true to false, LocalSettingsStore.normalizeReaderModes(true, true))
        assertEquals(false to true, LocalSettingsStore.normalizeReaderModes(false, true))
        assertEquals(true to false, LocalSettingsStore.normalizeReaderModes(true, false))
    }
}
