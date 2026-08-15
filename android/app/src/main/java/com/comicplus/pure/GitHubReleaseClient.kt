package com.comicplus.pure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

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
    suspend fun latest(): GitHubRelease = runInterruptible(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "ComicPlus-Pure")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub 返回 HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.length > MAX_RESPONSE_CHARS) throw IOException("GitHub 响应过大")
            parseRelease(JSONObject(body))
        }
    }

    companion object {
        private const val RELEASE_URL = "https://api.github.com/repos/chunyangnet/ComicPlus_Pure/releases/latest"
        private const val MAX_RESPONSE_CHARS = 512 * 1024

        internal fun parseRelease(json: JSONObject): GitHubRelease {
            val tag = json.optString("tag_name").trim().takeIf(String::isNotBlank)
                ?: throw IOException("GitHub release 缺少版本号")
            val version = normalizeReleaseVersion(tag)
            val releaseUrl = json.optString("html_url").trim().takeIf(String::isNotBlank)
                ?: throw IOException("GitHub release 缺少详情地址")
            val assets = json.optJSONArray("assets")
            var downloadUrl: String? = null
            var assetSize: Long? = null
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name").trim()
                    val contentType = asset.optString("content_type").trim()
                    if (!name.endsWith(".apk", ignoreCase = true) && contentType != "application/vnd.android.package-archive") continue
                    val candidate = asset.optString("browser_download_url").trim().takeIf(String::isNotBlank) ?: continue
                    downloadUrl = candidate
                    assetSize = asset.optLong("size").takeIf { it > 0L }
                    break
                }
            }
            return GitHubRelease(
                version = version,
                name = json.optString("name").trim().ifBlank { "Comic Plus $version" },
                notes = json.optString("body").trim(),
                publishedAt = json.optString("published_at").trim().takeIf(String::isNotBlank),
                releaseUrl = releaseUrl,
                downloadUrl = downloadUrl,
                assetSize = assetSize,
            )
        }
    }
}

internal fun normalizeReleaseVersion(raw: String): String = raw.trim().removePrefix("v").removePrefix("V")

internal fun isRemoteVersionNewer(current: String, latest: String): Boolean {
    val currentParts = versionParts(current)
    val latestParts = versionParts(latest)
    val size = maxOf(currentParts.size, latestParts.size)
    for (index in 0 until size) {
        val currentPart = currentParts.getOrElse(index) { 0 }
        val latestPart = latestParts.getOrElse(index) { 0 }
        if (latestPart != currentPart) return latestPart > currentPart
    }
    return false
}

private fun versionParts(raw: String): List<Int> = Regex("\\d+")
    .findAll(normalizeReleaseVersion(raw))
    .map { it.value.toIntOrNull() ?: 0 }
    .toList()
    .ifEmpty { listOf(0) }
