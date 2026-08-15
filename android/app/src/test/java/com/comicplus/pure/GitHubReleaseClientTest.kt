package com.comicplus.pure

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubReleaseClientTest {
    @Test
    fun releaseVersionNormalizationRemovesOnlyVersionPrefix() {
        assertEquals("1.2.3", normalizeReleaseVersion("v1.2.3"))
        assertEquals("1.2.3-beta", normalizeReleaseVersion(" V1.2.3-beta "))
    }

    @Test
    fun updateComparisonHandlesDebugSuffixAndMultiDigitSegments() {
        assertEquals(false, isRemoteVersionNewer("1.1.2-dev", "v1.1.2"))
        assertEquals(true, isRemoteVersionNewer("1.1.2", "v1.2.0"))
        assertEquals(true, isRemoteVersionNewer("1.9", "1.10"))
        assertEquals(false, isRemoteVersionNewer("2.0.0", "1.99.99"))
    }
}
