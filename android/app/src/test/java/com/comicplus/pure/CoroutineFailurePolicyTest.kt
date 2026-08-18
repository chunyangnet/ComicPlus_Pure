package com.comicplus.pure

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CoroutineFailurePolicyTest {
    @Test
    fun nonFatalFailureRemainsAResult() {
        val error = IllegalStateException("disk unavailable")

        val result = runCatchingNonFatal<Unit> { throw error }

        assertTrue(result.isFailure)
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun cancellationIsNeverConvertedToAResult() {
        val error = CancellationException("closed")

        try {
            runCatchingNonFatal<Unit> { throw error }
            fail("CancellationException should escape")
        } catch (actual: CancellationException) {
            assertSame(error, actual)
        }
    }

    @Test
    fun outOfMemoryIsNeverConvertedToAResult() {
        val error = OutOfMemoryError("bitmap budget exhausted")

        try {
            runCatchingNonFatal<Unit> { throw error }
            fail("OutOfMemoryError should escape")
        } catch (actual: OutOfMemoryError) {
            assertSame(error, actual)
        }
    }
}
