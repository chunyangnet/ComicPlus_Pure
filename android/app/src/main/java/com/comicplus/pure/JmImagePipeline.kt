package com.comicplus.pure

import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks the network phase of page requests and evaluates their visibility dynamically.
 *
 * A visible page can spend considerably longer decoding than downloading. Background pages may
 * use the otherwise idle socket during that decode, while still yielding as soon as another
 * visible page actually starts network I/O.
 */
internal class PageNetworkPriorityGate {
    private val activeRequests = ConcurrentHashMap.newKeySet<String>()

    fun enter(key: String) {
        check(activeRequests.add(key)) { "Page network request already active: $key" }
    }

    fun leave(key: String) {
        activeRequests.remove(key)
    }

    fun shouldPauseBackground(key: String, isPageVisible: (String) -> Boolean): Boolean =
        !isPageVisible(key) && activeRequests.any(isPageVisible)
}

/**
 * Fills one bounded network chunk before returning. OkHttp's stream can expose data in small
 * segments; batching them here avoids one coroutine interruption wrapper and progress callback
 * per internal segment without turning the whole page into one non-preemptible read.
 */
internal fun readPageInputChunk(
    input: InputStream,
    target: ByteArray,
    offset: Int = 0,
    length: Int = target.size - offset,
): Int {
    require(offset >= 0 && length >= 0 && offset + length <= target.size)
    if (length == 0) return 0

    var total = 0
    while (total < length) {
        val read = input.read(target, offset + total, length - total)
        when {
            read > 0 -> total += read
            read < 0 -> break
            else -> {
                val byte = input.read()
                if (byte < 0) break
                target[offset + total] = byte.toByte()
                total++
            }
        }
    }
    return if (total == 0) -1 else total
}
