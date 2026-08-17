package com.comicplus.pure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class GitHubRelease(
    val version: String,
    val name: String,
    val notes: String,
    val publishedAt: String?,
    val releaseUrl: String,
    val downloadUrl: String?,
    val assetSize: Long?,
)

class GitHubReleaseClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) {
    private val closed = AtomicBoolean(false)
    private val removeRouteChangeListener = SystemVpnMonitor.registerRouteChangeListener {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }

    suspend fun latest(): GitHubRelease = withContext(Dispatchers.IO) {
        if (closed.get()) throw CancellationException("更新客户端已关闭")
        val request = Request.Builder()
            .url(RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "ComicPlus-Pure")
            .get()
            .build()
        val call = client.newCall(request)
        if (closed.get()) {
            call.cancel()
            throw CancellationException("更新客户端已关闭")
        }
        try {
            runInterruptible(Dispatchers.IO) { call.execute() }.use { response ->
                if (!response.isSuccessful) throw IOException("GitHub 返回 HTTP ${response.code}")
                val body = response.body.readStringLimited(MAX_RESPONSE_BYTES)
                parseRelease(JSONObject(body))
            }
        } catch (error: CancellationException) {
            call.cancel()
            throw error
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        removeRouteChangeListener()
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    companion object {
        private const val RELEASE_URL = "https://api.github.com/repos/chunyangnet/ComicPlus_Pure/releases/latest"
        private const val MAX_RESPONSE_BYTES = 512 * 1024
        private const val MAX_RELEASE_TEXT_LENGTH = 300
        private const val MAX_RELEASE_NOTES_LENGTH = 40_000
        private const val MAX_RELEASE_ASSETS = 200
        private const val MAX_RELEASE_VERSION_LENGTH = 128
        private const val MAX_RELEASE_FIELD_LENGTH = 512

        internal fun parseRelease(json: JSONObject): GitHubRelease {
            val tag = json.optString("tag_name").trim().take(MAX_RELEASE_VERSION_LENGTH).takeIf(String::isNotBlank)
                ?: throw IOException("GitHub release 缺少版本号")
            val version = normalizeReleaseVersion(tag)
            val releaseUrl = trustedGitHubUrl(json.optString("html_url"))
                ?: throw IOException("GitHub release 缺少详情地址")
            val assets = json.optJSONArray("assets")
            var downloadUrl: String? = null
            var assetSize: Long? = null
            if (assets != null) {
                for (index in 0 until minOf(assets.length(), MAX_RELEASE_ASSETS)) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name").trim().take(MAX_RELEASE_FIELD_LENGTH)
                    val contentType = asset.optString("content_type").trim().take(MAX_RELEASE_FIELD_LENGTH)
                    if (!name.endsWith(".apk", ignoreCase = true) && contentType != "application/vnd.android.package-archive") continue
                    val candidate = trustedGitHubUrl(asset.optString("browser_download_url")) ?: continue
                    downloadUrl = candidate
                    assetSize = asset.opt("size")?.toString()?.toLongOrNull()?.takeIf { it > 0L }
                    break
                }
            }
            return GitHubRelease(
                version = version,
                name = json.optString("name").trim().take(MAX_RELEASE_TEXT_LENGTH).ifBlank { "Comic Plus $version" },
                notes = json.optString("body").trim().take(MAX_RELEASE_NOTES_LENGTH),
                publishedAt = json.optString("published_at").trim().take(MAX_RELEASE_FIELD_LENGTH).takeIf(String::isNotBlank),
                releaseUrl = releaseUrl,
                downloadUrl = downloadUrl,
                assetSize = assetSize,
            )
        }
    }
}

internal fun normalizeReleaseVersion(raw: String): String = raw.trim().removePrefix("v").removePrefix("V")

internal fun isRemoteVersionNewer(current: String, latest: String): Boolean {
    val currentVersion = parseComparableVersion(current)
    val latestVersion = parseComparableVersion(latest)
    // Local debug builds use a -dev suffix. Do not nag a developer that the
    // same numeric release is available when the installed build is already
    // that release compiled with a development suffix.
    if (
        compareVersionCore(currentVersion.core, latestVersion.core) == 0 &&
        currentVersion.pre.isNotEmpty() &&
        latestVersion.pre.isEmpty() &&
        currentVersion.pre.all { it.equals("dev", ignoreCase = true) || it.equals("debug", ignoreCase = true) }
    ) {
        return false
    }
    return compareComparableVersions(latestVersion, currentVersion) > 0
}

private data class ComparableVersion(
    val core: List<Long>,
    val pre: List<String>,
)

private val comparableVersionPattern = Regex(
    """(?i)(\d+(?:\.\d+)*)(?:-([0-9a-z-]+(?:\.[0-9a-z-]+)*))?""",
)

private fun parseComparableVersion(raw: String): ComparableVersion {
    val match = comparableVersionPattern.find(normalizeReleaseVersion(raw))
        ?: return ComparableVersion(listOf(0L), emptyList())
    val core = match.groupValues[1].split('.').map { token ->
        token.toLongOrNull() ?: Long.MAX_VALUE
    }
    val pre = match.groupValues.getOrNull(2).orEmpty().takeIf(String::isNotBlank)
        ?.split('.')
        .orEmpty()
    return ComparableVersion(core.ifEmpty { listOf(0L) }, pre)
}

private fun compareComparableVersions(left: ComparableVersion, right: ComparableVersion): Int {
    compareVersionCore(left.core, right.core).takeIf { it != 0 }?.let { return it }
    if (left.pre.isEmpty() && right.pre.isEmpty()) return 0
    if (left.pre.isEmpty()) return 1
    if (right.pre.isEmpty()) return -1
    val preSize = maxOf(left.pre.size, right.pre.size)
    for (index in 0 until preSize) {
        if (index >= left.pre.size) return -1
        if (index >= right.pre.size) return 1
        val leftPart = left.pre[index]
        val rightPart = right.pre[index]
        if (leftPart == rightPart) continue
        val leftNumber = leftPart.toLongOrNull()
        val rightNumber = rightPart.toLongOrNull()
        return when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> leftPart.compareTo(rightPart)
        }
    }

    return 0
}

private fun compareVersionCore(left: List<Long>, right: List<Long>): Int {
    val coreSize = maxOf(left.size, right.size)
    for (index in 0 until coreSize) {
        val comparison = left.getOrElse(index) { 0L }.compareTo(right.getOrElse(index) { 0L })
        if (comparison != 0) return comparison
    }
    return 0
}

/** Accept only the GitHub-hosted URLs returned by the release API. */
internal fun trustedGitHubUrl(raw: String?): String? {
    val url = raw?.trim()?.toHttpUrlOrNull() ?: return null
    val host = url.host.lowercase()
    val trustedHost = host == "github.com" ||
        host.endsWith(".github.com") ||
        host == "githubusercontent.com" ||
        host.endsWith(".githubusercontent.com")
    return url.toString().takeIf {
        it.length <= 4_096 && trustedHost && url.scheme == "https" && url.port == 443 &&
            url.username.isEmpty() && url.password.isEmpty()
    }
}
