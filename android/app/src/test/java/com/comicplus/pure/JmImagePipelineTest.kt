package com.comicplus.pure

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class JmImagePipelineTest {
    @Test
    fun backgroundNetworkOnlyPausesDuringVisibleNetworkWork() {
        val gate = PageNetworkPriorityGate()
        val visibleKeys = mutableSetOf<String>()
        val isVisible = visibleKeys::contains

        gate.enter("next")
        assertEquals(false, gate.shouldPauseBackground("other", isVisible))

        // An in-flight prefetch can become the visible page. Other background work must yield,
        // while the promoted request itself continues without restarting.
        visibleKeys += "next"
        assertEquals(true, gate.shouldPauseBackground("other", isVisible))
        assertEquals(false, gate.shouldPauseBackground("next", isVisible))

        gate.leave("next")
        assertEquals(false, gate.shouldPauseBackground("other", isVisible))
    }

    @Test
    fun pageInputChunkFillsAcrossShortAndZeroLengthReads() {
        val source = object : InputStream() {
            private val delegate = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5))
            private var returnZero = true

            override fun read(): Int = delegate.read()

            override fun read(target: ByteArray, offset: Int, length: Int): Int {
                if (returnZero) {
                    returnZero = false
                    return 0
                }
                return delegate.read(target, offset, minOf(length, 2))
            }
        }
        val target = ByteArray(5)

        assertEquals(5, readPageInputChunk(source, target))
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5), target.toList())
        assertEquals(-1, readPageInputChunk(source, ByteArray(1)))
    }
}
