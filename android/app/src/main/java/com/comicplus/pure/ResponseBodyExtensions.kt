package com.comicplus.pure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads a bounded UTF-8 response without using ResponseBody.string(), which
 * buffers an untrusted response before the caller can enforce its size.
 */
internal suspend fun ResponseBody.readStringLimited(maxBytes: Int): String = runInterruptible(Dispatchers.IO) {
    require(maxBytes > 0) { "maxBytes must be positive" }
    val declared = contentLength()
    byteStream().use { input -> readUtf8Limited(input, declared, maxBytes) }
}

internal fun readUtf8Limited(input: InputStream, declared: Long, maxBytes: Int): String {
    require(maxBytes > 0) { "maxBytes must be positive" }
    if (declared > maxBytes) throw IOException("响应超过大小限制")
    val output = ByteArrayOutputStream(if (declared in 1L..maxBytes.toLong()) declared.toInt() else 8 * 1024)
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) {
            val single = input.read()
            if (single < 0) break
            total += 1
            if (total > maxBytes) throw IOException("响应超过大小限制")
            output.write(single)
        } else {
            total += read
            if (total > maxBytes) throw IOException("响应超过大小限制")
            output.write(buffer, 0, read)
        }
    }
    return output.toString(Charsets.UTF_8.name())
}
