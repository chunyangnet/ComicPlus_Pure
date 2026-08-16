package com.comicplus.pure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.io.InputStream

class GitHubReleaseClientTest {
    @Test
    fun releaseVersionNormalizationRemovesOnlyVersionPrefix() {
        assertEquals("1.2.3", normalizeReleaseVersion("v1.2.3"))
        assertEquals("1.2.3-beta", normalizeReleaseVersion(" V1.2.3-beta "))
    }

    @Test
    fun updateComparisonHandlesDebugSuffixAndMultiDigitSegments() {
        assertEquals(false, isRemoteVersionNewer("1.1.2-dev", "v1.1.2"))
        assertEquals(false, isRemoteVersionNewer("1.1-dev", "v1.1.0"))
        assertEquals(true, isRemoteVersionNewer("1.1.2", "v1.2.0"))
        assertEquals(true, isRemoteVersionNewer("1.9", "1.10"))
        assertEquals(false, isRemoteVersionNewer("2.0.0", "1.99.99"))
        assertEquals(true, isRemoteVersionNewer("1.2.0-beta1", "1.2.0"))
        assertEquals(false, isRemoteVersionNewer("1.2.0", "1.2.0-beta1"))
        assertEquals(true, isRemoteVersionNewer("1.2.0-beta1", "1.2.0-beta2"))
    }

    @Test
    fun releaseLinksMustStayOnGithubHttpsHosts() {
        assertEquals("https://github.com/chunyangnet/ComicPlus_Pure/releases", trustedGitHubUrl("https://github.com/chunyangnet/ComicPlus_Pure/releases"))
        assertEquals("https://objects.githubusercontent.com/a", trustedGitHubUrl("https://objects.githubusercontent.com/a"))
        assertEquals(null, trustedGitHubUrl("http://github.com/a"))
        assertEquals(null, trustedGitHubUrl("https://github.com.evil.example/a"))
        assertEquals(null, trustedGitHubUrl("https://user:pass@github.com/a"))
        assertEquals(null, trustedGitHubUrl("https://github.com/" + "x".repeat(4_096)))
    }

    @Test
    fun boundedResponseReaderRejectsOversizedDeclaredPayloads() {
        val error = assertThrows(IOException::class.java) {
            runBlocking {
                "0123456789".toResponseBody().use { it.readStringLimited(4) }
            }
        }
        assertEquals("响应超过大小限制", error.message)
    }

    @Test
    fun boundedResponseReaderMakesProgressWhenStreamReturnsZero() {
        val input = object : InputStream() {
            private var position = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (position == 0) {
                    position++
                    return 0
                }
                if (position > 3) return -1
                buffer[offset] = ('a'.code + position - 1).toByte()
                position++
                return 1
            }

            override fun read(): Int = if (position <= 3) {
                val value = 'a'.code + position - 1
                position++
                value
            } else {
                -1
            }
        }
        assertEquals("abc", readUtf8Limited(input, declared = -1L, maxBytes = 8))
    }
}
