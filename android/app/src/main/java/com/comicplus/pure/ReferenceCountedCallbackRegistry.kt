package com.comicplus.pure

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps one callback entry per object while retaining how many callers use it.
 * This matters when Compose reuses the same lambda for overlapping page loads:
 * the first caller must not unregister the callback on behalf of the second.
 */
internal class ReferenceCountedCallbackRegistry<K : Any, T : Any> {
    private val values = ConcurrentHashMap<K, ConcurrentHashMap<T, AtomicInteger>>()

    fun add(key: K, callback: T) {
        values.compute(key) { _, current ->
            (current ?: ConcurrentHashMap()).also { callbacks ->
                callbacks.compute(callback) { _, count ->
                    (count ?: AtomicInteger()).also { it.incrementAndGet() }
                }
            }
        }
    }

    fun remove(key: K, callback: T) {
        values.computeIfPresent(key) { _, callbacks ->
            callbacks.computeIfPresent(callback) { _, count ->
                count.takeIf { it.decrementAndGet() > 0 }
            }
            callbacks.takeIf { it.isNotEmpty() }
        }
    }

    fun forEach(key: K, action: (T) -> Unit) {
        values[key]?.keys?.forEach(action)
    }

    fun clear() {
        values.clear()
    }

    internal fun referenceCount(key: K, callback: T): Int = values[key]?.get(callback)?.get() ?: 0

}
