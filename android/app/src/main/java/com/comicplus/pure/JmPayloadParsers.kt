package com.comicplus.pure

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

internal const val MAX_OFFICIAL_PAGE = 200
internal const val MAX_ACCOUNT_FIELD_LENGTH = 128
internal const val MAX_FAVORITE_SYNC_ITEMS = 200
internal const val MAX_FAVORITE_SYNC_PAGES = 10
private const val MAX_FAVORITE_AUTHORS = 12
private const val MAX_FAVORITE_FOLDERS = 100
private const val OFFICIAL_FAVORITE_PAGE_SIZE = 20
private const val MAX_FAVORITE_TITLE_LENGTH = 500
private const val MAX_FAVORITE_DESCRIPTION_LENGTH = 50_000
private const val MAX_FAVORITE_FIELD_LENGTH = 512
internal const val MAX_FAVORITE_FOLDER_NAME_LENGTH = 80
internal val safeNumericId = Regex("^\\d{1,12}$")
private val favoriteImageFilePattern = Regex(
    "^[A-Za-z0-9_-]{1,128}\\.(?:jpg|jpeg|png|webp)$",
    RegexOption.IGNORE_CASE,
)
private val compactLongPattern = Regex(
    "^([0-9]+(?:\\.[0-9]+)?)([KMB万億亿]?)$",
    RegexOption.IGNORE_CASE,
)

internal fun JSONObject.string(key: String): String = optString(key).takeUnless { it == "null" }.orEmpty()
internal fun JSONObject.int(key: String): Int? = parseJsonInt(opt(key))
internal fun JSONObject.long(key: String): Long? = when (val value = opt(key)) {
    is Number -> parseCompactLong(value.toString())
    is String -> parseCompactLong(value)
    else -> null
}
internal fun JSONObject.array(key: String): JSONArray = optJSONArray(key) ?: JSONArray()
internal fun JSONObject.obj(key: String): JSONObject = optJSONObject(key) ?: JSONObject()
internal fun JSONObject.stringList(key: String, limit: Int = 200): List<String> =
    array(key).objectsOrValues(limit).mapNotNull { it.primitiveContent() }
internal fun JSONArray.objectsOrValues(limit: Int = MAX_JSON_ARRAY_ITEMS): List<Any?> {
    val boundedLength = minOf(length(), limit.coerceAtLeast(0))
    return buildList(boundedLength) {
        for (index in 0 until boundedLength) add(opt(index))
    }
}
internal fun Any?.primitiveContent(): String? = this?.toString()?.takeUnless { it == "null" }

internal fun parseFavoritePage(
    payload: JSONObject,
    page: Int = 1,
    folderId: String = "0",
    coverResolver: (String) -> String = { id -> "https://cover.invalid/$id.jpg" },
): JmFavoritePage {
    val root = payload.optJSONObject("data") ?: payload
    val safePage = page.coerceIn(1, MAX_OFFICIAL_PAGE)
    val safeFolderId = folderId.takeIf(safeNumericId::matches) ?: "0"
    val items = firstJsonArray(root, "list", "content", "data", "albums", "favorites", "photos")
        ?.objectsOrValues(MAX_FAVORITE_SYNC_ITEMS)
        ?.mapNotNull { value ->
            val item = value as? JSONObject ?: return@mapNotNull null
            val id = firstJsonString(item, "id", "aid", "album_id")
                .take(MAX_ACCOUNT_FIELD_LENGTH)
                .takeIf(safeNumericId::matches)
                ?: return@mapNotNull null
            val title = firstJsonString(item, "name", "title")
                .take(MAX_FAVORITE_TITLE_LENGTH)
                .ifBlank { "JM$id" }
            val rawCover = firstJsonString(item, "image", "cover", "photo", "img", "image_url")
            JmFavoriteItem(
                id = id,
                title = title,
                description = firstJsonString(item, "description", "desc").take(MAX_FAVORITE_DESCRIPTION_LENGTH),
                coverUrl = normalizeFavoriteImage(rawCover, id, coverResolver),
                authors = favoriteAuthors(item),
            )
        }
        ?.distinctBy(JmFavoriteItem::id)
        .orEmpty()
    val reportedTotal = firstJsonLong(root, "total", "count")
    return JmFavoritePage(
        folderId = safeFolderId,
        page = safePage,
        total = JmGateway.normalizedPagedTotal(safePage, OFFICIAL_FAVORITE_PAGE_SIZE, items.size, reportedTotal),
        items = items,
        folders = favoriteFolders(root),
        hasMore = JmGateway.hasMorePagedResults(safePage, OFFICIAL_FAVORITE_PAGE_SIZE, items.size, reportedTotal),
        totalKnown = reportedTotal != null,
    )
}

private fun favoriteFolders(root: JSONObject): List<JmFavoriteFolder> = buildList {
    add(JmFavoriteFolder(id = "0", name = "全部"))
    firstJsonArray(root, "folder_list", "folders")
        ?.objectsOrValues(MAX_FAVORITE_FOLDERS)
        ?.forEach { value ->
            val item = value as? JSONObject ?: return@forEach
            val id = firstJsonString(item, "FID", "fid", "id", "folder_id")
                .take(MAX_ACCOUNT_FIELD_LENGTH)
                .takeIf(safeNumericId::matches)
                ?.takeUnless { it == "0" }
                ?: return@forEach
            val name = firstJsonString(item, "name", "folder_name", "title")
                .trim()
                .take(MAX_FAVORITE_FOLDER_NAME_LENGTH)
                .ifBlank { "收藏夹 $id" }
            add(JmFavoriteFolder(id = id, name = name))
        }
}.distinctBy(JmFavoriteFolder::id)

private fun favoriteAuthors(item: JSONObject): List<String> {
    val values = item.opt("author").takeUnless { it == null || it == JSONObject.NULL } ?: item.opt("authors")
    return when (values) {
        is JSONArray -> values.objectsOrValues(MAX_FAVORITE_AUTHORS)
            .mapNotNull(Any?::primitiveContent)
            .map { it.take(MAX_FAVORITE_FIELD_LENGTH) }
            .filter(String::isNotBlank)
        is String -> values.split(',', '、', ';')
            .map { it.trim().take(MAX_FAVORITE_FIELD_LENGTH) }
            .filter(String::isNotBlank)
        else -> emptyList()
    }.distinct().take(MAX_FAVORITE_AUTHORS)
}

private fun normalizeFavoriteImage(raw: String, id: String, coverResolver: (String) -> String): String? {
    val value = raw.trim()
    val fallback = coverResolver(id)
    if (value.isBlank()) return fallback
    JmGateway.normalizeRemoteHttpsUrl(value)?.let { return it }
    val fallbackHost = fallback.toHttpUrlOrNull()?.host ?: return null
    if (value.startsWith('/') && !value.contains("..") && !value.contains('?') && !value.contains('#')) {
        return "https://$fallbackHost$value"
    }
    return if (value.matches(favoriteImageFilePattern)) {
        "https://$fallbackHost/media/albums/$value"
    } else {
        null
    }
}

/**
 * The forum payload has changed field casing over time (CID vs cid, replys vs replies),
 * so keep the compatibility rules in one parser instead of leaking them into the UI.
 */
internal fun parseJmCommentPage(payload: JSONObject, page: Int = 1): JmCommentPage {
    val root = payload.optJSONObject("data") ?: payload
    val items = firstJsonArray(root, "list", "comments", "content", "data")
        ?.objectsOrValues(MAX_COMMENT_ITEMS)
        ?.mapIndexedNotNull { index, value ->
            (value as? JSONObject)?.let { parseJmComment(it, "${page.coerceAtLeast(1)}-${index + 1}", 0) }
        }
        ?.distinctBy(JmComment::id)
        .orEmpty()
    val reportedTotal = firstJsonLong(root, "total", "count")
    val safePage = page.coerceIn(1, MAX_OFFICIAL_PAGE)
    val total = JmGateway.normalizedPagedTotal(
        page = safePage,
        pageSize = OFFICIAL_COMMENT_PAGE_SIZE,
        loaded = items.size,
        reportedTotal = reportedTotal,
    )
    return JmCommentPage(
        page = safePage,
        total = total,
        comments = items,
        hasMore = JmGateway.hasMorePagedResults(
            page = page,
            pageSize = OFFICIAL_COMMENT_PAGE_SIZE,
            loaded = items.size,
            total = reportedTotal,
        ),
    )
}

private const val MAX_COMMENT_REPLY_DEPTH = 2
private const val OFFICIAL_COMMENT_PAGE_SIZE = 10
private const val MAX_COMMENT_ITEMS = 50
private const val MAX_COMMENT_REPLIES = 50
private const val MAX_COMMENT_TEXT_LENGTH = 20_000
private const val MAX_COMMENT_FIELD_LENGTH = 512
private const val MAX_JSON_ARRAY_ITEMS = 20_000
private const val MAX_EMBEDDED_JSON_ARRAY_CHARS = 512 * 1024

private fun parseJmComment(value: JSONObject, fallbackId: String, depth: Int): JmComment {
    val id = firstJsonString(value, "CID", "cid", "comment_id", "id")
        .take(MAX_COMMENT_FIELD_LENGTH)
        .ifBlank { fallbackId }
    val replies = if (depth >= MAX_COMMENT_REPLY_DEPTH) {
        emptyList()
    } else {
        firstJsonArray(value, "replys", "replies", "reply")
            ?.objectsOrValues(MAX_COMMENT_REPLIES)
            ?.mapIndexedNotNull { index, child ->
                (child as? JSONObject)?.let { parseJmComment(it, "$id-r${index + 1}", depth + 1) }
            }
            .orEmpty()
    }
    return JmComment(
        id = id,
        userId = firstJsonString(value, "UID", "uid", "user_id").take(MAX_COMMENT_FIELD_LENGTH).takeIf(String::isNotBlank),
        albumId = firstJsonString(value, "AID", "aid", "album_id").take(MAX_COMMENT_FIELD_LENGTH).takeIf(String::isNotBlank),
        username = firstJsonString(value, "username", "user_name", "name").take(MAX_COMMENT_FIELD_LENGTH),
        nickname = firstJsonString(value, "nickname", "display_name").take(MAX_COMMENT_FIELD_LENGTH),
        content = firstJsonString(value, "content", "comment", "text").take(MAX_COMMENT_TEXT_LENGTH),
        avatarUrl = firstJsonString(value, "photo", "avatar", "avatar_url")
            .take(MAX_COMMENT_FIELD_LENGTH)
            .takeIf(String::isNotBlank),
        createdAt = firstJsonString(value, "addtime", "update_at", "created_at", "time").take(MAX_COMMENT_FIELD_LENGTH),
        likes = (firstJsonLong(value, "likes", "like", "like_count") ?: 0L).coerceAtLeast(0L),
        parentId = firstJsonString(value, "parent_CID", "parent_cid", "parent_id")
            .take(MAX_COMMENT_FIELD_LENGTH)
            .takeIf(String::isNotBlank),
        spoiler = isSpoilerFlag(firstJsonString(value, "spoiler", "is_spoiler")),
        replies = replies,
    )
}

private fun isSpoilerFlag(value: String): Boolean =
    value == "1" || value == "true" || value == "TRUE" || value == "yes"

internal fun JmComment.withAvatarHost(host: String?): JmComment = copy(
    avatarUrl = avatarUrl?.trim()?.let { raw ->
        when {
            raw.startsWith("https://", ignoreCase = true) -> JmGateway.normalizeRemoteHttpsUrl(raw)
            host.isNullOrBlank() -> null
            else -> {
                val safeHost = JmGateway.normalizeRemoteDomain(host) ?: return@let null
                val path = if (raw.startsWith('/')) raw else "/media/users/${raw.trimStart('/')}"
                if (path.length > MAX_COMMENT_FIELD_LENGTH ||
                    path.contains("..") || path.contains('\\') || path.contains('?') || path.contains('#')
                ) null else "https://$safeHost$path"
            }
        }
    },
    replies = replies.map { reply -> reply.withAvatarHost(host) },
)

internal fun firstJsonArray(value: JSONObject, vararg keys: String): JSONArray? {
    keys.forEach { key ->
        value.optJSONArray(key)?.let { return it }
        val encoded = value.optString(key).trim()
        if (encoded.startsWith("[") && encoded.length <= MAX_EMBEDDED_JSON_ARRAY_CHARS) {
            runCatchingNonFatal { JSONArray(encoded) }.getOrNull()?.let { return it }
        }
    }
    return null
}

internal fun firstJsonString(value: JSONObject, vararg keys: String): String {
    keys.forEach { key ->
        val candidate = value.string(key).trim()
        if (candidate.isNotBlank()) return candidate
    }
    return ""
}

internal fun firstJsonLong(value: JSONObject, vararg keys: String): Long? {
    keys.forEach { key ->
        value.long(key)?.let { return it }
    }
    return null
}

internal fun parseJsonInt(value: Any?): Int? {
    val parsed = when (value) {
        is Number, is String -> value.toString().trim().toLongOrNull()
        else -> null
    } ?: return null
    return parsed.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
}

internal fun parseCompactLong(raw: String): Long? {
    val trimmed = raw.trim()
    val normalized = if (',' in trimmed) trimmed.replace(",", "") else trimmed
    normalized.toLongOrNull()?.let { return it }
    val match = compactLongPattern.matchEntire(normalized) ?: return null
    val number = match.groupValues[1].toDoubleOrNull() ?: return null
    val multiplier = when (match.groupValues[2].uppercase()) {
        "K" -> 1_000L
        "M" -> 1_000_000L
        "B" -> 1_000_000_000L
        "万" -> 10_000L
        "億", "亿" -> 100_000_000L
        else -> 1L
    }
    val result = number * multiplier
    return result.takeIf(Double::isFinite)?.takeIf { it <= Long.MAX_VALUE }?.toLong()
}
