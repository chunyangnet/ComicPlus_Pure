package com.comicplus.pure

import java.io.IOException

data class JmSourceEndpoint(
    val host: String,
    val latencyMs: Long?,
)

data class JmSourceSnapshot(
    val endpoints: List<JmSourceEndpoint>,
    val selectedHost: String?,
    val updatedAt: Long,
    val imageEndpoints: List<JmSourceEndpoint> = emptyList(),
    val selectedImageHost: String? = null,
    val imageUpdatedAt: Long = 0L,
)

/**
 * Source probing is relatively expensive and competes with the first reader
 * request for sockets. A persisted snapshot is sufficient until either side
 * is stale or has never produced a usable latency measurement.
 */
internal fun sourceSnapshotNeedsRefresh(
    snapshot: JmSourceSnapshot,
    nowMillis: Long,
    maxAgeMillis: Long,
): Boolean {
    if (maxAgeMillis <= 0L) return true
    fun stale(timestamp: Long): Boolean =
        timestamp <= 0L || timestamp > nowMillis || nowMillis - timestamp >= maxAgeMillis
    return stale(snapshot.updatedAt) ||
        stale(snapshot.imageUpdatedAt) ||
        snapshot.endpoints.none { it.latencyMs != null } ||
        snapshot.imageEndpoints.none { it.latencyMs != null }
}

internal fun orderSourceEndpoints(
    endpoints: List<JmSourceEndpoint>,
    autoSelect: Boolean,
    preferredHosts: List<String> = endpoints.map(JmSourceEndpoint::host),
): List<JmSourceEndpoint> {
    val preferredOrder = preferredHosts.withIndex().associate { (index, host) -> host to index }
    val stableOrder = endpoints.sortedWith(
        compareBy<JmSourceEndpoint>({ preferredOrder[it.host] ?: Int.MAX_VALUE }, { it.host }),
    )
    if (!autoSelect || stableOrder.none { it.latencyMs != null }) return stableOrder
    return stableOrder.sortedWith(
        compareBy<JmSourceEndpoint>({ it.latencyMs == null }, { it.latencyMs ?: Long.MAX_VALUE }),
    )
}

/**
 * Image hosts are interchangeable mirrors of the same immutable comic page.
 * Keep cache identity tied to the logical page so changing or auto-selecting a
 * faster host can still reuse downloaded bytes and decoded bitmaps.
 */
internal fun pageContentIdentity(page: JmPage): String =
    if (page.localPath != null || page.url.startsWith("file:", ignoreCase = true)) {
        "local|${page.localPath ?: page.url}"
    } else {
        "remote|${page.photoId}|${page.fileName}"
    }

class JmSourceException : IOException("JM 官方源暂时不可用")
