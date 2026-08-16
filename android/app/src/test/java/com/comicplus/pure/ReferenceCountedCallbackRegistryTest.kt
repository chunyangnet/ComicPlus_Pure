package com.comicplus.pure

import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceCountedCallbackRegistryTest {
    @Test
    fun sharedCallbackRemainsRegisteredUntilEveryCallerLeaves() {
        val registry = ReferenceCountedCallbackRegistry<String, () -> Unit>()
        var calls = 0
        val callback: () -> Unit = { calls += 1 }

        registry.add("page", callback)
        registry.add("page", callback)
        assertEquals(2, registry.referenceCount("page", callback))

        registry.remove("page", callback)
        registry.forEach("page") { it() }
        assertEquals(1, calls)
        assertEquals(1, registry.referenceCount("page", callback))

        registry.remove("page", callback)
        registry.forEach("page") { it() }
        assertEquals(1, calls)
        assertEquals(0, registry.referenceCount("page", callback))
    }

    @Test
    fun callbacksForDifferentPagesAndObjectsStayIndependent() {
        val registry = ReferenceCountedCallbackRegistry<String, String>()
        registry.add("a", "first")
        registry.add("a", "second")
        registry.add("b", "first")

        registry.remove("a", "first")

        val pageA = mutableSetOf<String>()
        val pageB = mutableSetOf<String>()
        registry.forEach("a") { pageA += it }
        registry.forEach("b") { pageB += it }
        assertEquals(setOf("second"), pageA)
        assertEquals(setOf("first"), pageB)
    }
}
