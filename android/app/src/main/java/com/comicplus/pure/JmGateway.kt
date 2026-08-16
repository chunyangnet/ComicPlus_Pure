package com.comicplus.pure

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.LruCache
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import com.comicplus.app.ui.ReaderImageQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

private data class FetchedImage(val url: String, val bytes: ByteArray)
private data class OpenedImage(val url: String, val response: Response)
private data class PageDecodeProfile(
    val maxWidth: Int,
    val maxPixels: Long,
    val cacheWebpQuality: Int,
    val hedgeDelayMillis: Long,
    val cacheToken: String,
    val turboMode: Boolean,
)

private const val MAX_OFFICIAL_PAGE = 200
private const val MAX_CHAPTER_PAGE_ITEMS = 20_000
private const val MAX_ACCOUNT_FIELD_LENGTH = 128
private const val MAX_PASSWORD_LENGTH = 512
private const val MAX_AVS_LENGTH = 4 * 1024
private const val MAX_ERROR_MESSAGE_LENGTH = 240
private const val MAX_FAVORITE_SYNC_ITEMS = 200
private const val MAX_FAVORITE_SYNC_PAGES = 10
private const val MAX_FAVORITE_AUTHORS = 12
private const val OFFICIAL_FAVORITE_PAGE_SIZE = 20
// Kept at file scope because the favorite parser lives outside JmGateway's
// companion object and must apply the same bounds to untrusted server data.
private const val MAX_FAVORITE_TITLE_LENGTH = 500
private const val MAX_FAVORITE_DESCRIPTION_LENGTH = 50_000
private const val MAX_FAVORITE_FIELD_LENGTH = 512

/** JM 官方移动端协议适配器。 */
class JmGateway(context: Context) {
    private val appContext = context.applicationContext
    private val sourcePreferences = appContext.getSharedPreferences(SOURCE_PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val initialOfficialDomains = loadOfficialDomains()
    private val initialSourceSnapshot = loadSourceSnapshot(initialOfficialDomains)
    private val cookies = MemoryCookieJar()
    private val client = OkHttpClient.Builder()
        .cookieJar(cookies)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val imageWarmupClient = client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()
    private val discoveryClient = client.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .build()
    private val sourceProbeClient = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(SOURCE_PROBE_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(SOURCE_PROBE_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(SOURCE_PROBE_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
    private val cacheDir = File(appContext.cacheDir, "jm-pure-pages").apply { mkdirs() }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val maxConcurrentImageWork = imageWorkPermits(context)
    private val maxDecodedPagePixels = decodedPagePixelLimit(context)
    private val chapterLocks = List(CHAPTER_LOCK_STRIPES) { Mutex() }
    private val imageWorkLimiter = Semaphore(permits = maxConcurrentImageWork)
    private val backgroundImageWorkLimiter = Semaphore(permits = (maxConcurrentImageWork - 1).coerceAtLeast(1))
    private val backgroundNetworkLimiter = Semaphore(permits = 1)
    private val turboBackgroundNetworkLimiter = Semaphore(permits = 2)
    private val diskCacheMutex = Mutex()
    private val rawCacheWriteLimiter = Semaphore(permits = 1)
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pageCacheGeneration = AtomicInteger()
    private val pageCacheWrites = AtomicInteger(PAGE_CACHE_TRIM_INTERVAL - 1)
    private val pageCacheWritesInFlight = ConcurrentHashMap.newKeySet<String>()
    private val closed = AtomicInteger(0)
    private val pageLoadsInFlight = ConcurrentHashMap<String, Deferred<Bitmap>>()
    private val sourceLoadsInFlight = ConcurrentHashMap<String, Deferred<ByteArray>>()
    private val pageLoadWaiters = ConcurrentHashMap<String, AtomicInteger>()
    private val sourceLoadWaiters = ConcurrentHashMap<String, AtomicInteger>()
    private val pageProgressCallbacks = ReferenceCountedCallbackRegistry<String, (Long, Long) -> Unit>()
    private val pageAspectRatioCallbacks = ReferenceCountedCallbackRegistry<String, (Float) -> Unit>()
    private val visiblePageWaiters = ConcurrentHashMap<String, AtomicInteger>()
    private val hedgePageWaiters = ConcurrentHashMap<String, AtomicInteger>()
    private val turboPageWaiters = ConcurrentHashMap<String, AtomicInteger>()
    private val visiblePageRequestCount = AtomicInteger()
    private val bitmapCache = object : LruCache<String, Bitmap>(bitmapCacheSizeKb(context)) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }
    private val chapterCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, JmChapterPages>(CHAPTER_CACHE_LIMIT, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JmChapterPages>?): Boolean =
                size > CHAPTER_CACHE_LIMIT
        },
    )
    private val pageAspectRatioCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Float>(PAGE_ASPECT_RATIO_CACHE_LIMIT, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean =
                size > PAGE_ASPECT_RATIO_CACHE_LIMIT
        },
    )
    private val cookieMutex = Mutex()
    private val domainDiscoveryMutex = Mutex()
    private val sourceRefreshMutex = Mutex()
    private val scrambleMutex = Mutex()
    private val sourceStateLock = Any()
    private val initializedCookieHosts = ConcurrentHashMap.newKeySet<String>()
    private val failedImageHosts = ConcurrentHashMap<String, Long>()
    private val warmedImageHosts = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var officialDomains: List<String> = initialOfficialDomains
    @Volatile private var sourceSnapshot: JmSourceSnapshot = initialSourceSnapshot
    @Volatile private var domains: List<String> = initialSourceSnapshot.endpoints.map(JmSourceEndpoint::host)
        .ifEmpty { initialOfficialDomains }
    @Volatile private var lastDomainDiscoveryAt = 0L
    @Volatile private var cachedScrambleId: String? = null
    @Volatile private var cachedScramblePhotoId: String? = null
    @Volatile private var cachedScrambleAt = 0L
    @Volatile private var preferredImageHost: String? = initialSourceSnapshot.selectedImageHost
        ?.takeIf(safeHost::matches)
    @Volatile private var autoSelectSource = true
    @Volatile private var preferredSourceHost: String? = null
    @Volatile private var authenticatedSession: JmSession? = null

    fun setSourcePreferences(
        autoSelect: Boolean,
        preferredHost: String? = null,
        preferredImageHost: String? = null,
    ): JmSourceSnapshot = synchronized(sourceStateLock) {
        val previousImageHost = sourceSnapshot.selectedImageHost
        autoSelectSource = autoSelect
        preferredSourceHost = normalizeRemoteDomain(preferredHost)
        this.preferredImageHost = normalizeDomain(preferredImageHost)
            ?.takeIf { host -> imageDomains.any { it == host } }
        val ordered = orderSourceEndpoints(
            sourceSnapshot.endpoints,
            autoSelect,
            preferredSourceOrder(officialDomains),
        )
        val imageCandidates = sourceSnapshot.imageEndpoints.ifEmpty {
            imageDomains.map { host -> JmSourceEndpoint(host, null) }
        }
        val orderedImages = orderSourceEndpoints(
            imageCandidates,
            autoSelect,
            preferredImageSourceOrder(imageDomains),
        )
        domains = ordered.map(JmSourceEndpoint::host).ifEmpty { officialDomains }
        val selectedImage = when {
            !autoSelect && this.preferredImageHost != null -> this.preferredImageHost
            else -> orderedImages.firstOrNull { it.latencyMs != null }?.host
                ?: orderedImages.firstOrNull()?.host
        }
        sourceSnapshot = sourceSnapshot.copy(
            endpoints = ordered,
            selectedHost = domains.firstOrNull(),
            imageEndpoints = orderedImages,
            selectedImageHost = selectedImage,
        )
        this.preferredImageHost = selectedImage
        if (selectedImage != previousImageHost) {
            sourceLoadsInFlight.entries.toList().forEach { (key, load) ->
                sourceLoadsInFlight.remove(key, load)
                load.cancel()
            }
            pageLoadsInFlight.entries.toList().forEach { (key, load) ->
                pageLoadsInFlight.remove(key, load)
                load.cancel()
            }
        }
        sourceSnapshot
    }

    fun cachedSourceSnapshot(): JmSourceSnapshot = sourceSnapshot

    fun clearSession() {
        authenticatedSession = null
        cookies.clear()
        initializedCookieHosts.clear()
    }

    /** Stop gateway-owned work when the ViewModel is permanently destroyed. */
    fun close() {
        if (!closed.compareAndSet(0, 1)) return
        cacheScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        pageLoadsInFlight.values.forEach { it.cancel() }
        sourceLoadsInFlight.values.forEach { it.cancel() }
        pageLoadsInFlight.clear()
        sourceLoadsInFlight.clear()
        pageProgressCallbacks.clear()
        pageAspectRatioCallbacks.clear()
        bitmapCache.evictAll()
        client.dispatcher.cancelAll()
        sourceProbeClient.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        sourceProbeClient.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        sourceProbeClient.dispatcher.executorService.shutdown()
    }

    fun warmImageConnections(comicId: String, chapterId: String) {
        if (closed.get() != 0 || !comicId.matches(safeNumericId) || !chapterId.matches(safeNumericId)) return
        buildList {
            sourceSnapshot.selectedImageHost?.let(::add)
            preferredImageHost?.let(::add)
            addAll(rotatedImageDomains(chapterId))
        }.distinct().take(IMAGE_WARMUP_HOST_COUNT).forEach { host ->
            if (!warmedImageHosts.add(host)) return@forEach
            cacheScope.launch {
                var call: okhttp3.Call? = null
                try {
                    val request = Request.Builder()
                        .url("https://$host/media/albums/${comicId}_3x4.jpg")
                        .header("Referer", "https://${domains.firstOrNull() ?: builtInDomains.first()}/")
                        .header("X-Requested-With", "com.JMComic3.app")
                        .header("User-Agent", APP_USER_AGENT)
                        .head()
                        .build()
                    val warmupCall = imageWarmupClient.newCall(request)
                    call = warmupCall
                    runInterruptible(Dispatchers.IO) { warmupCall.execute().close() }
                } catch (error: CancellationException) {
                    call?.cancel()
                    warmedImageHosts.remove(host)
                    throw error
                } catch (_: Exception) {
                    warmedImageHosts.remove(host)
                }
            }
        }
    }

    suspend fun refreshSourceList(
        force: Boolean = false,
        updateOfficialList: Boolean = true,
    ): JmSourceSnapshot = sourceRefreshMutex.withLock {
        if (closed.get() != 0) throw CancellationException("JM 网关已关闭")
        val candidates = (if (updateOfficialList) discoverDomains(force) else domains)
            .distinct()
            .take(MAX_SOURCE_PROBE_CANDIDATES)
        val probed = coroutineScope {
            val limiter = Semaphore(MAX_SOURCE_PROBE_CONCURRENCY)
            candidates.map { domain ->
                async {
                    limiter.withPermit { JmSourceEndpoint(domain, probeSourceLatency(domain)) }
                }
            }.awaitAll()
        }
        if (probed.none { it.latencyMs != null }) throw JmSourceException()
        val imageProbed = coroutineScope {
            val limiter = Semaphore(MAX_SOURCE_PROBE_CONCURRENCY)
            imageDomains.map { domain ->
                async {
                    limiter.withPermit { JmSourceEndpoint(domain, probeImageLatency(domain)) }
                }
            }.awaitAll()
        }
        synchronized(sourceStateLock) {
            // Read preferences and publish the snapshot under one short lock.
            // A manual source choice made while probes were running therefore
            // participates in this result instead of being overwritten by it.
            val ordered = orderSourceEndpoints(probed, autoSelectSource, preferredSourceOrder(candidates))
            val orderedImages = orderSourceEndpoints(
                imageProbed,
                autoSelectSource,
                preferredImageSourceOrder(imageDomains),
            )
            domains = ordered.map(JmSourceEndpoint::host).ifEmpty { officialDomains }
            val selectedImage = when {
                !autoSelectSource && preferredImageHost != null -> preferredImageHost
                else -> orderedImages.firstOrNull { it.latencyMs != null }?.host
                    ?: orderedImages.firstOrNull()?.host
            }
            preferredImageHost = selectedImage
            val now = System.currentTimeMillis()
            JmSourceSnapshot(
                endpoints = ordered,
                selectedHost = domains.firstOrNull(),
                updatedAt = now,
                imageEndpoints = orderedImages,
                selectedImageHost = selectedImage,
                imageUpdatedAt = now,
            ).also { snapshot ->
                sourceSnapshot = snapshot
                saveSourceState(snapshot)
            }
        }
    }

    suspend fun home(): List<JmRanking> = withContext(Dispatchers.IO) {
        (category("0", "mv_t") + category("0", "mr")).distinctBy(JmRanking::id).take(40)
    }

    suspend fun category(slug: String, order: String = "mr", page: Int = 1): List<JmRanking> =
        categoryPage(slug, order, page).items

    suspend fun categoryPage(slug: String, order: String = "mr", page: Int = 1): JmRankingPage {
        val safeSlug = slug.trim().take(MAX_OPTION_ID_LENGTH).ifBlank { "0" }
        val safePage = page.coerceIn(1, MAX_OFFICIAL_PAGE)
        val payload = requestJson(
            "/categories/filter?page=$safePage&order=&c=${encode(safeSlug)}&o=${order.allowedOrder()}",
        )
        val items = payload.rankingItems("JM 分类")
        val reportedTotal = payload.long("total")
        val pageSize = payload.pageSize(OFFICIAL_LIST_PAGE_SIZE)
        val total = normalizedPagedTotal(safePage, pageSize, items.size, reportedTotal)
        return JmRankingPage(
            page = safePage,
            total = total,
            items = items,
            hasMore = payload.hasMorePage(safePage, items.size, reportedTotal),
        )
    }

    suspend fun categories(): List<JmCategory> = categoryCatalog().categories

    suspend fun categoryCatalog(): JmCategoryCatalog = try {
        val payload = requestJson("/categories")
        val categories = payload.array("categories").objectsOrValues(MAX_CATALOG_ITEMS).mapNotNull { item ->
            val obj = item as? JSONObject ?: return@mapNotNull null
            val name = obj.string("name").trim().take(MAX_TITLE_LENGTH)
            val slug = obj.string("slug").trim().take(MAX_OPTION_ID_LENGTH).ifBlank { "0" }
            if (name.isBlank()) null else JmCategory(
                obj.string("id").trim().take(MAX_OPTION_ID_LENGTH).ifBlank { slug },
                name,
                slug,
                obj.long("total_albums")?.coerceAtLeast(0L),
                obj.string("type").trim().take(MAX_OPTION_ID_LENGTH).ifBlank { "slug" },
            )
        }.distinctBy(JmCategory::slug).ifEmpty { fallbackCategories }
        val tagGroups = payload.array("blocks").objectsOrValues(MAX_TAG_GROUPS).mapNotNull { item ->
            val obj = item as? JSONObject ?: return@mapNotNull null
            val tags = obj.stringList("content", MAX_LIST_ITEMS)
                .map { it.trim().take(MAX_FIELD_LENGTH) }
                .filter(String::isNotBlank)
                .distinct()
            if (tags.isEmpty()) null else JmTagGroup(
                obj.string("title").trim().take(MAX_TITLE_LENGTH).ifBlank { "标签" },
                tags,
            )
        }.distinctBy(JmTagGroup::title)
        JmCategoryCatalog(categories, tagGroups)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        JmCategoryCatalog(fallbackCategories, emptyList())
    }

    suspend fun weekCatalog(): JmWeekCatalog {
        val payload = requestJson("/week")
        val categories = payload.array("categories").objectsOrValues(MAX_CATALOG_ITEMS).mapNotNull { item ->
            val obj = item as? JSONObject ?: return@mapNotNull null
            val id = obj.string("id").trim().take(MAX_OPTION_ID_LENGTH)
                .takeIf(String::isNotBlank) ?: return@mapNotNull null
            val title = obj.string("title").trim().take(MAX_TITLE_LENGTH)
                .ifBlank { obj.string("time").trim().take(MAX_TITLE_LENGTH) }
                .ifBlank { id }
            JmWeekOption(id, title)
        }.distinctBy(JmWeekOption::id)
        val typeArray = payload.array("type").takeIf { it.length() > 0 } ?: payload.array("types")
        val types = typeArray.objectsOrValues(MAX_CATALOG_ITEMS).mapNotNull { item ->
            val obj = item as? JSONObject ?: return@mapNotNull null
            val id = obj.string("id").trim().take(MAX_OPTION_ID_LENGTH)
                .takeIf(String::isNotBlank) ?: return@mapNotNull null
            JmWeekOption(
                id,
                obj.string("title").trim().take(MAX_TITLE_LENGTH)
                    .ifBlank { obj.string("name").trim().take(MAX_TITLE_LENGTH) }
                    .ifBlank { id },
            )
        }.distinctBy(JmWeekOption::id)
        if (categories.isEmpty() || types.isEmpty()) throw JmSourceException()
        return JmWeekCatalog(categories, types)
    }

    suspend fun week(categoryId: String, typeId: String, page: Int = 1): JmRankingPage {
        val safeCategoryId = categoryId.trim().take(MAX_OPTION_ID_LENGTH)
        val safeTypeId = typeId.trim().take(MAX_OPTION_ID_LENGTH)
        require(safeCategoryId.isNotBlank()) { "每周必看分类无效" }
        require(safeTypeId.isNotBlank()) { "每周必看类型无效" }
        val safePage = page.coerceIn(1, MAX_OFFICIAL_PAGE)
        val payload = requestJson(
            "/week/filter?page=$safePage&id=${encode(safeCategoryId)}&type=${encode(safeTypeId)}",
        )
        val items = payload.rankingItemsFrom("list", "JM 每周必看", preferResultImage = true)
        val reportedTotal = payload.long("total")
        val pageSize = payload.pageSize(OFFICIAL_LIST_PAGE_SIZE)
        val total = normalizedPagedTotal(safePage, pageSize, items.size, reportedTotal)
        return JmRankingPage(
            page = safePage,
            total = total,
            items = items,
            hasMore = payload.hasMorePage(safePage, items.size, reportedTotal),
        )
    }

    suspend fun discoverDomains(force: Boolean = false): List<String> {
        if (closed.get() != 0) throw CancellationException("JM 网关已关闭")
        val now = System.currentTimeMillis()
        if (!force && now - lastDomainDiscoveryAt < DOMAIN_DISCOVERY_COOLDOWN_MILLIS) return officialDomains
        return domainDiscoveryMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (!force && lockedNow - lastDomainDiscoveryAt < DOMAIN_DISCOVERY_COOLDOWN_MILLIS) return@withLock officialDomains
            try {
                for (url in discoveryUrls.shuffled()) {
                    try {
                        val call = discoveryClient.newCall(Request.Builder().url(url).get().build())
                        val encoded = try {
                            runInterruptible(Dispatchers.IO) { call.execute() }.use { response ->
                                if (!response.isSuccessful) return@use null
                                response.body.readStringLimited(MAX_API_RESPONSE_BYTES)
                                    .dropWhile { it.code > 127 }
                                    .trim()
                            }
                        } catch (error: CancellationException) {
                            call.cancel()
                            throw error
                        }
                        if (encoded.isNullOrBlank()) continue
                        val decoded = protocolDecrypt(encoded, "", DOMAIN_SERVER_PROTOCOL_KEY)
                        val discovered = JSONObject(decoded).array("Server").objectsOrValues(MAX_SOURCE_PROBE_CANDIDATES)
                            .mapNotNull { normalizeDomain(it.primitiveContent()) }
                            .distinct()
                        if (discovered.isNotEmpty()) {
                            val resolved = synchronized(sourceStateLock) {
                                officialDomains = (discovered + builtInDomains)
                                    .distinct()
                                    .take(MAX_SOURCE_PROBE_CANDIDATES)
                                domains = preferredSourceOrder(
                                    (domains + officialDomains).distinct().take(MAX_SOURCE_PROBE_CANDIDATES),
                                )
                                saveOfficialDomains()
                                officialDomains
                            }
                            return@withLock resolved
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                    }
                }
                officialDomains
            } finally {
                lastDomainDiscoveryAt = lockedNow
            }
        }
    }

    suspend fun search(query: String, page: Int = 1, mainTag: Int = 0, order: String = "mr"): JmSearchPage {
        val normalized = query.trim().take(160)
        require(normalized.isNotBlank()) { "请输入搜索内容" }
        val safePage = page.coerceIn(1, MAX_OFFICIAL_PAGE)
        val payload = requestJson("/search?main_tag=${mainTag.coerceIn(0, 4)}&search_query=${encode(normalized)}&page=$safePage&o=${order.allowedSearchOrder()}&t=a")
        val items = payload.rankingItems("JM 搜索", preferResultImage = true)
        val reportedTotal = payload.long("total")
        val size = payload.pageSize(OFFICIAL_SEARCH_PAGE_SIZE)
        val total = normalizedPagedTotal(safePage, size, items.size, reportedTotal)
        val redirectAid = payload.string("redirect_aid").takeIf { it.matches(Regex("\\d{1,12}")) }
        return JmSearchPage(
            query = payload.string("search_query").trim().take(160).ifBlank { normalized },
            page = safePage,
            total = total,
            redirectAid = redirectAid,
            items = items,
            hasMore = hasMoreSearchResults(safePage, size, items.size, reportedTotal, redirectAid),
        )
    }

    suspend fun comic(id: String): JmComic {
        require(id.matches(Regex("\\d{1,12}"))) { "JM 编号无效" }
        val data = requestJson("/album?id=$id")
        val actualId = data.string("id").takeIf(safeNumericId::matches) ?: id
        val series = data.array("series").objectsOrValues(MAX_SERIES_ITEMS).mapIndexedNotNull { index, element ->
            val item = element as? JSONObject ?: return@mapIndexedNotNull null
            val chapterId = item.string("id").takeIf { it.matches(Regex("\\d{1,12}")) } ?: return@mapIndexedNotNull null
            val sort = item.int("sort")?.takeIf { it > 0 } ?: index + 1
            JmChapter(chapterId, sort, formatChapterTitle(item.string("name"), sort))
        }.distinctBy(JmChapter::id)
            .sortedWith(compareBy(JmChapter::index, JmChapter::id))
            .ifEmpty { listOf(JmChapter(actualId, 1, "第 1 话")) }
        return JmComic(
            actualId,
            data.string("name").take(MAX_TITLE_LENGTH).ifBlank { "JM$actualId" },
            data.string("description").take(MAX_DESCRIPTION_LENGTH),
            coverUrl(actualId),
            data.stringList("author", MAX_LIST_ITEMS).map { it.take(MAX_FIELD_LENGTH) }.filter(String::isNotBlank).distinct().take(MAX_LIST_ITEMS),
            data.stringList("tags", MAX_LIST_ITEMS).map { it.take(MAX_FIELD_LENGTH) }.filter(String::isNotBlank).distinct().take(MAX_LIST_ITEMS),
            series,
            data.long("total_views"),
            data.long("likes"),
        )
    }

    /** Read the official, anonymous forum for an album. Posting is intentionally out of scope. */
    suspend fun comments(albumId: String, page: Int = 1): JmCommentPage {
        require(albumId.matches(Regex("\\d{1,12}"))) { "JM 编号无效" }
        val safePage = page.coerceIn(1, MAX_OFFICIAL_PAGE)
        val payload = requestJson(
            "/forum?page=$safePage&aid=${encode(albumId)}&mode=manhua",
        )
        val parsed = parseJmCommentPage(payload, safePage)
        return parsed.copy(
            comments = parsed.comments.map { comment ->
                comment.withAvatarHost(domains.firstOrNull())
            },
        )
    }

    /** Sign in through the official JM mobile API. Passwords never leave this call. */
    suspend fun login(username: String, password: String): JmAccount = withContext(Dispatchers.IO) {
        val safeUsername = username.trim().take(MAX_ACCOUNT_FIELD_LENGTH)
        require(safeUsername.isNotBlank()) { "请输入 JM 用户名" }
        require(password.isNotEmpty() && password.length <= MAX_PASSWORD_LENGTH) { "JM 密码无效" }
        clearSession()
        val payload = requestPostJson(
            path = "/login",
            form = mapOf("username" to safeUsername, "password" to password),
            allowUnauthenticated = true,
        )
        val accountPayload = payload.optJSONObject("data") ?: payload
        val avs = firstJsonString(accountPayload, "s", "AVS", "avs").take(MAX_AVS_LENGTH)
        val status = firstJsonString(accountPayload, "status", "result").lowercase()
        if (avs.isBlank() || status in setOf("error", "fail", "failed", "false")) {
            throw JmAuthException(
                firstJsonString(accountPayload, "message", "msg", "errorMsg").take(MAX_ERROR_MESSAGE_LENGTH)
                    .ifBlank { "JM 登录失败，请检查账号或密码" },
            )
        }
        val uid = firstJsonString(accountPayload, "uid", "UID", "user_id").take(MAX_ACCOUNT_FIELD_LENGTH).ifBlank { "0" }
        val resolvedUsername = firstJsonString(accountPayload, "username", "user_name").take(MAX_ACCOUNT_FIELD_LENGTH)
            .ifBlank { safeUsername }
        val session = JmSession(uid = uid, username = resolvedUsername, avs = avs)
        authenticatedSession = session
        domains.firstOrNull()?.let(::installSessionCookie)
        JmAccount(
            uid = uid,
            username = resolvedUsername,
            avatarUrl = firstJsonString(accountPayload, "photo", "avatar", "avatar_url")
                .take(MAX_ACCOUNT_FIELD_LENGTH)
                .takeIf(String::isNotBlank)
                ?.let { raw -> normalizeRemoteHttpsUrl(raw) ?: domains.firstOrNull()?.let { host -> "https://$host/media/users/${raw.trimStart('/')}" } },
            favoriteCount = firstJsonLong(accountPayload, "album_favorites", "favorite_count"),
        )
    }

    fun restoreSession(session: JmSession) {
        if (
            session.uid.isBlank() || session.username.isBlank() || session.avs.isBlank() ||
            session.uid.length > MAX_ACCOUNT_FIELD_LENGTH || session.username.length > MAX_ACCOUNT_FIELD_LENGTH ||
            session.avs.length > MAX_AVS_LENGTH
        ) return
        authenticatedSession = session
        domains.forEach(::installSessionCookie)
    }

    fun session(): JmSession? = authenticatedSession

    suspend fun logout() {
        runCatching {
            if (authenticatedSession != null) {
                requestPostJson("/logout", emptyMap(), allowUnauthenticated = false)
            }
        }.onFailure { if (it is CancellationException) throw it }
        clearSession()
    }

    /** Fetches one page of the signed-in user's official collection. */
    suspend fun favoritePage(page: Int = 1): JmFavoritePage {
        requireAuthenticated()
        val safePage = page.coerceIn(1, MAX_OFFICIAL_PAGE)
        val payload = requestAuthenticatedJson("/favorite?page=$safePage&folder_id=0&o=mr")
        requireAuthenticatedPayload(payload)
        return parseFavoritePage(
            payload,
            safePage,
            ::coverUrl,
        )
    }

    suspend fun allFavorites(maxPages: Int = MAX_FAVORITE_SYNC_PAGES): List<JmFavoriteItem> {
        requireAuthenticated()
        val result = ArrayList<JmFavoriteItem>()
        var page = 1
        while (page <= maxPages) {
            val current = favoritePage(page)
            result += current.items
            if (!current.hasMore || current.items.isEmpty()) break
            page++
        }
        return result.distinctBy(JmFavoriteItem::id).take(MAX_FAVORITE_SYNC_ITEMS)
    }

    /** The official endpoint toggles based on the current server state. */
    suspend fun toggleFavorite(albumId: String) {
        requireAuthenticated()
        require(albumId.matches(Regex("\\d{1,12}"))) { "JM 编号无效" }
        requireMutationSucceeded(
            requestPostJson(
                path = "/favorite",
                form = mapOf("aid" to albumId),
                retryAcrossDomains = false,
            ),
        )
    }

    /** Official album like toggle, kept in the same authenticated request chain. */
    suspend fun toggleLike(albumId: String) {
        requireAuthenticated()
        require(albumId.matches(Regex("\\d{1,12}"))) { "JM 编号无效" }
        requireMutationSucceeded(
            requestPostJson(
                path = "/like",
                form = mapOf("id" to albumId),
                retryAcrossDomains = false,
            ),
            success = setOf("success", "ok", "1"),
        )
    }

    private fun requireAuthenticated() {
        if (authenticatedSession == null) throw JmAuthException("请先登录 JM 官方账号")
    }

    private fun requireAuthenticatedPayload(payload: JSONObject) {
        val status = firstJsonString(payload, "status", "result").lowercase()
        if (status in setOf("401", "403", "unauthorized", "not_logged_in", "login_required")) {
            throw JmAuthException(
                firstJsonString(payload, "msg", "message", "errorMsg")
                    .take(MAX_ERROR_MESSAGE_LENGTH)
                    .ifBlank { "JM 登录已失效，请重新登录" },
            )
        }
    }

    private fun requireMutationSucceeded(payload: JSONObject, success: Set<String> = setOf("ok", "success", "1")) {
        val result = payload.optJSONObject("data") ?: payload
        val accepted = success + setOf("true", "200")
        val status = firstJsonString(result, "status", "result", "code").lowercase()
        if (status !in accepted) {
            throw JmApiException(
                firstJsonString(result, "msg", "message", "errorMsg").take(MAX_ERROR_MESSAGE_LENGTH)
                    .ifBlank { "JM 官方操作失败" },
            )
        }
    }

    suspend fun chapter(id: String): JmChapterPages {
        require(id.matches(Regex("\\d{1,12}"))) { "章节编号无效" }
        chapterCache[id]?.let { return it }
        val lock = chapterLocks[lockStripeIndex(id, chapterLocks.size)]
        return lock.withLock {
            chapterCache[id]?.let { return@withLock it }
            coroutineScope {
                val speculativeScramble = id.toLongOrNull()
                    ?.takeIf { it >= DEFAULT_SCRAMBLE_ID.toLong() }
                    ?.let { async { fetchScramble(id) } }
                try {
                    val data = requestJson("/chapter?id=$id")
                    val photoId = data.string("id").takeIf(safeNumericId::matches) ?: id
                    val title = data.string("name").take(MAX_TITLE_LENGTH).ifBlank { "第 1 话" }
                    val serverScramble = data.string("scramble_id").takeIf(safeNumericId::matches)
                    val scramble = when {
                        serverScramble != null -> serverScramble.also { speculativeScramble?.cancel() }
                        photoId.toLongOrNull()?.let { it < DEFAULT_SCRAMBLE_ID.toLong() } == true ->
                            DEFAULT_SCRAMBLE_ID.also { speculativeScramble?.cancel() }
                        photoId == id -> speculativeScramble?.await() ?: fetchScramble(photoId)
                        else -> fetchScramble(photoId).also { speculativeScramble?.cancel() }
                    }
                    val hosts = rotatedImageDomains(photoId)
                    val imageArray = data.array("images")
                    if (imageArray.length() > MAX_CHAPTER_PAGE_ITEMS) throw JmSourceException()
                    val files = normalizeChapterImageFiles(
                        imageArray.objectsOrValues(MAX_CHAPTER_PAGE_ITEMS).mapNotNull { it.primitiveContent() },
                    )
                    val refererHost = domains.firstOrNull() ?: builtInDomains.first()
                    val pages = files.mapIndexed { index, file -> JmPage(index + 1, photoId, file, scramble, "https://${hosts.first()}/media/photos/$photoId/$file", hosts.drop(1).map { "https://$it/media/photos/$photoId/$file" }, "https://$refererHost/") }
                    if (pages.isEmpty()) throw JmSourceException()
                    JmChapterPages(photoId, title, pages).also { chapterCache[id] = it }
                } catch (error: Throwable) {
                    speculativeScramble?.cancel()
                    throw error
                }
            }
        }
    }

    suspend fun loadPage(
        page: JmPage,
        quality: ReaderImageQuality = ReaderImageQuality.Medium,
        turboMode: Boolean = false,
        hedgeImageHosts: Boolean = true,
        onAspectRatio: (Float) -> Unit = {},
    ): Bitmap = requestPage(
        page,
        profile = pageDecodeProfile(quality, turboMode),
        visible = true,
        // Turbo mode already pins the fastest measured image host. Opening a
        // second response for every page only duplicates connection work and
        // competes with the page currently being read.
        hedgeImageHosts = hedgeImageHosts && !turboMode,
        onAspectRatio = onAspectRatio,
    )

    suspend fun prefetchPage(
        page: JmPage,
        quality: ReaderImageQuality = ReaderImageQuality.Medium,
        turboMode: Boolean = false,
    ) {
        val profile = pageDecodeProfile(quality, turboMode)
        if (maxConcurrentImageWork > 1) {
            requestPage(
                page,
                profile = profile,
                visible = false,
                hedgeImageHosts = false,
            )
        } else {
            prefetchPageSource(page, profile)
        }
    }

    private suspend fun prefetchPageSource(page: JmPage, profile: PageDecodeProfile) {
        if (page.localPath != null) return
        val generation = pageCacheGeneration.get()
        val key = pageCacheKey(page, profile)
        val sourceKey = pageSourceCacheKey(page)
        if (bitmapCache.get(key)?.takeUnless(Bitmap::isRecycled) != null) return
        if (isValidDecodedPageFile(File(cacheDir, "$key.webp"))) return
        val rawCacheFile = File(cacheDir, "$sourceKey.source")
        if (rawCacheFile.length() in 1..MAX_PAGE_BYTES.toLong()) {
            rawCacheFile.setLastModified(System.currentTimeMillis())
            return
        }
        if (profile.turboMode) incrementPageWaiter(turboPageWaiters, sourceKey)
        try {
            requestSourceBytes(
                page = page,
                sourceKey = sourceKey,
                generation = generation,
                hedgeImageHosts = false,
                hedgeDelayMillis = profile.hedgeDelayMillis,
            )
        } finally {
            if (profile.turboMode) decrementPageWaiter(turboPageWaiters, sourceKey)
        }
    }

    private suspend fun requestPage(
        page: JmPage,
        profile: PageDecodeProfile,
        visible: Boolean,
        hedgeImageHosts: Boolean,
        onProgress: ((Long, Long) -> Unit)? = null,
        onAspectRatio: ((Float) -> Unit)? = null,
        cacheGeneration: Int = pageCacheGeneration.get(),
    ): Bitmap {
        if (closed.get() != 0) throw CancellationException("JM 网关已关闭")
        val key = pageCacheKey(page, profile)
        val sourceKey = pageSourceCacheKey(page)
        if (onProgress != null) pageProgressCallbacks.add(key, onProgress)
        if (onAspectRatio != null) {
            pageAspectRatioCallbacks.add(key, onAspectRatio)
            pageAspectRatioCache[key]?.let { ratio ->
                postAspectRatioIfSubscribed(key, onAspectRatio, ratio)
            }
        }
        if (closed.get() != 0) {
            if (onProgress != null) pageProgressCallbacks.remove(key, onProgress)
            if (onAspectRatio != null) pageAspectRatioCallbacks.remove(key, onAspectRatio)
            throw CancellationException("JM 网关已关闭")
        }
        if (visible) {
            visiblePageRequestCount.incrementAndGet()
            incrementPageWaiter(visiblePageWaiters, key)
            incrementPageWaiter(visiblePageWaiters, sourceKey)
        }
        if (visible && hedgeImageHosts) {
            incrementPageWaiter(hedgePageWaiters, sourceKey)
        }
        if (profile.turboMode) {
            incrementPageWaiter(turboPageWaiters, key)
            incrementPageWaiter(turboPageWaiters, sourceKey)
        }
        incrementPageWaiter(pageLoadWaiters, key)
        try {
            bitmapCache.get(key)?.takeUnless(Bitmap::isRecycled)?.let { bitmap ->
                onAspectRatio?.let { callback ->
                    postAspectRatioIfSubscribed(
                        key,
                        callback,
                        bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1),
                    )
                }
                return bitmap
            }
            val created = cacheScope.async(start = CoroutineStart.LAZY) {
                performPageLoad(page, key, sourceKey, profile, hedgeImageHosts, cacheGeneration)
            }
            val existing = pageLoadsInFlight.putIfAbsent(key, created)
            val selected = existing ?: created.also { deferred ->
                deferred.invokeOnCompletion { pageLoadsInFlight.remove(key, deferred) }
                deferred.start()
            }
            if (existing != null) created.cancel()
            val bitmap = selected.await()
            // A caller may have joined work that started before cache cleanup.
            // Re-publish the result under the caller's generation so cleanup
            // cannot force every joined request to download/decode again.
            cacheBitmapIfCurrent(key, bitmap, cacheGeneration)
            cachePageAspectRatioIfCurrent(key, bitmap.width, bitmap.height, cacheGeneration)
            if (!profile.turboMode && page.localPath == null) {
                scheduleDecodedPageCacheWrite(
                    key = key,
                    file = File(cacheDir, "$key.webp"),
                    bitmap = bitmap,
                    webpQuality = profile.cacheWebpQuality,
                    generation = cacheGeneration,
                )
            }
            return bitmap
        } finally {
            if (onProgress != null) pageProgressCallbacks.remove(key, onProgress)
            if (onAspectRatio != null) pageAspectRatioCallbacks.remove(key, onAspectRatio)
            if (visible) {
                visiblePageRequestCount.decrementAndGet()
                decrementPageWaiter(visiblePageWaiters, key)
                decrementPageWaiter(visiblePageWaiters, sourceKey)
            }
            if (visible && hedgeImageHosts) {
                decrementPageWaiter(hedgePageWaiters, sourceKey)
            }
            if (profile.turboMode) {
                decrementPageWaiter(turboPageWaiters, key)
                decrementPageWaiter(turboPageWaiters, sourceKey)
            }
            if (decrementPageWaiter(pageLoadWaiters, key)) {
                cancelUnobservedPageLoadAfterGrace(key)
            }
        }
    }

    private fun incrementPageWaiter(waiters: ConcurrentHashMap<String, AtomicInteger>, key: String) {
        waiters.compute(key) { _, current ->
            (current ?: AtomicInteger()).also { it.incrementAndGet() }
        }
    }

    private fun decrementPageWaiter(waiters: ConcurrentHashMap<String, AtomicInteger>, key: String): Boolean {
        var becameEmpty = false
        waiters.computeIfPresent(key) { _, current ->
            if (current.decrementAndGet() <= 0) {
                becameEmpty = true
                null
            } else {
                current
            }
        }
        return becameEmpty
    }

    private fun cancelUnobservedPageLoadAfterGrace(key: String) {
        val load = pageLoadsInFlight[key] ?: return
        cacheScope.launch {
            delay(UNOBSERVED_PAGE_LOAD_GRACE_MILLIS)
            if ((pageLoadWaiters[key]?.get() ?: 0) <= 0 && !isPageVisible(key)) {
                load.cancel()
            }
        }
    }

    private suspend fun performPageLoad(
        page: JmPage,
        key: String,
        sourceKey: String,
        profile: PageDecodeProfile,
        hedgeImageHosts: Boolean,
        generation: Int,
    ): Bitmap =
        withContext(Dispatchers.IO) {
            bitmapCache.get(key)?.takeUnless(Bitmap::isRecycled)?.let { return@withContext it }
            val decodedCacheFile = File(cacheDir, "$key.webp")
            val rawCacheFile = File(cacheDir, "$sourceKey.source")

            page.localPath?.let { localPath ->
                return@withContext withImageDecodeTurn(key) {
                    decodeFilePage(localPath, key, profile, generation) ?: throw JmSourceException()
                }.also { cacheBitmapIfCurrent(key, it, generation) }
            }
            decodeFilePageWithTurn(decodedCacheFile, key, profile, generation)?.let { bitmap ->
                decodedCacheFile.setLastModified(System.currentTimeMillis())
                cacheBitmapIfCurrent(key, bitmap, generation)
                return@withContext bitmap
            }
            decodeRawPageFileWithTurn(rawCacheFile, page, key, profile, generation)?.let { bitmap ->
                rawCacheFile.setLastModified(System.currentTimeMillis())
                cacheBitmapIfCurrent(key, bitmap, generation)
                if (!profile.turboMode) {
                    scheduleDecodedPageCacheWrite(
                        key,
                        decodedCacheFile,
                        bitmap,
                        profile.cacheWebpQuality,
                        generation,
                    )
                }
                return@withContext bitmap
            }

            val bytes = requestSourceBytes(
                page = page,
                sourceKey = sourceKey,
                generation = generation,
                onProgress = { done, total ->
                    pageProgressCallbacks.forEach(key) { callback ->
                        if (closed.get() == 0 && pageProgressCallbacks.referenceCount(key, callback) > 0) {
                            runCatching { callback(done, total) }
                        }
                    }
                },
                hedgeImageHosts = hedgeImageHosts,
                hedgeDelayMillis = profile.hedgeDelayMillis,
            )
            val decoded = withImageDecodeTurn(key) { decodePage(bytes, page, key, profile, generation) }
            cacheBitmapIfCurrent(key, decoded, generation)
            if (!profile.turboMode) {
                scheduleDecodedPageCacheWrite(
                    key,
                    decodedCacheFile,
                    decoded,
                    profile.cacheWebpQuality,
                    generation,
                )
            }
            decoded
        }

    private suspend fun requestSourceBytes(
        page: JmPage,
        sourceKey: String,
        generation: Int,
        hedgeImageHosts: Boolean,
        hedgeDelayMillis: Long,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): ByteArray {
        incrementPageWaiter(sourceLoadWaiters, sourceKey)
        try {
            val created = cacheScope.async(start = CoroutineStart.LAZY) {
                fetchImage(
                    page = page,
                    key = sourceKey,
                    onProgress = onProgress,
                    hedgeImageHosts = hedgeImageHosts,
                    hedgeDelayMillis = hedgeDelayMillis,
                )
            }
            val existing = sourceLoadsInFlight.putIfAbsent(sourceKey, created)
            val selected = existing ?: created.also { deferred ->
                deferred.invokeOnCompletion { cause ->
                    if (cause != null) {
                        sourceLoadsInFlight.remove(sourceKey, deferred)
                    } else {
                        cacheScope.launch {
                            delay(SOURCE_LOAD_REUSE_MILLIS)
                            sourceLoadsInFlight.remove(sourceKey, deferred)
                        }
                    }
                }
                deferred.start()
            }
            if (existing != null) created.cancel()
            return selected.await().also { bytes ->
                // Schedule from the caller's generation as well as the producer's
                // generation. A request that joined a reused in-flight load after
                // cache cleanup must still be able to repopulate the new cache.
                scheduleRawPageCacheWrite(sourceKey, File(cacheDir, "$sourceKey.source"), bytes, generation)
            }
        } finally {
            if (decrementPageWaiter(sourceLoadWaiters, sourceKey)) {
                cancelUnobservedSourceLoadAfterGrace(sourceKey)
            }
        }
    }

    private fun cancelUnobservedSourceLoadAfterGrace(sourceKey: String) {
        val load = sourceLoadsInFlight[sourceKey] ?: return
        cacheScope.launch {
            delay(UNOBSERVED_PAGE_LOAD_GRACE_MILLIS)
            if (
                !load.isCompleted &&
                (sourceLoadWaiters[sourceKey]?.get() ?: 0) <= 0 &&
                !isPageVisible(sourceKey)
            ) {
                load.cancel()
            }
        }
    }

    fun cachedPage(
        page: JmPage,
        quality: ReaderImageQuality = ReaderImageQuality.Medium,
        turboMode: Boolean = false,
    ): Bitmap? = bitmapCache.get(pageCacheKey(page, pageDecodeProfile(quality, turboMode)))
        ?.takeUnless(Bitmap::isRecycled)

    suspend fun downloadPage(page: JmPage, target: File, onProgress: (Long, Long) -> Unit = { _, _ -> }) = withContext(Dispatchers.IO) {
        if (closed.get() != 0) throw CancellationException("JM 网关已关闭")
        if (isValidDecodedPageFile(target)) return@withContext
        target.delete()
        val profile = pageDecodeProfile(ReaderImageQuality.High, turboMode = false)
        val decodedKey = pageCacheKey(page, profile)
        val sourceKey = pageSourceCacheKey(page)
        val decodedCacheFile = File(cacheDir, "$decodedKey.webp")
        val generation = pageCacheGeneration.get()
        if (isValidDecodedPageFile(decodedCacheFile)) {
            awaitVisiblePageIdle()
            target.parentFile?.mkdirs()
            decodedCacheFile.copyTo(target, overwrite = true)
            target.setLastModified(System.currentTimeMillis())
            return@withContext
        }
        val decoded = requestPage(
            page,
            profile = profile,
            visible = false,
            hedgeImageHosts = false,
            onProgress = onProgress,
            cacheGeneration = generation,
        )
        awaitVisiblePageIdle()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        try {
            target.parentFile?.mkdirs()
            runAtBackgroundPriority {
                temporary.outputStream().buffered().use { output ->
                    if (!decoded.compress(Bitmap.CompressFormat.WEBP, 92, output)) throw JmSourceException()
                }
            }
            if (!temporary.renameTo(target)) temporary.copyTo(target, overwrite = true)
            val cacheWritten = diskCacheMutex.withLock {
                if (generation != pageCacheGeneration.get() || decodedCacheFile.isFile) return@withLock false
                target.copyTo(decodedCacheFile, overwrite = true)
                File(cacheDir, "$sourceKey.source").delete()
                true
            }
            if (cacheWritten) trimPageCacheIfNeeded()
        } finally {
            temporary.delete()
        }
    }

    fun coverUrl(id: String): String {
        val safeId = id.trim().takeIf(safeNumericId::matches) ?: "0"
        val selected = sourceSnapshot.selectedImageHost
            ?.takeIf { host -> imageDomains.any { it == host } }
        val host = selected ?: imageDomains[lockStripeIndex(safeId, imageDomains.size)]
        return "https://$host/media/albums/${safeId}_3x4.jpg"
    }

    suspend fun clearPageCache(): Long = withContext(Dispatchers.IO) {
        if (closed.get() != 0) return@withContext 0L
        pageCacheGeneration.incrementAndGet()
        bitmapCache.evictAll()
        pageAspectRatioCache.clear()
        diskCacheMutex.withLock {
            cacheDir.listFiles().orEmpty().sumOf { file ->
                val bytes = file.length()
                if (file.delete()) bytes else 0L
            }
        }
    }

    private suspend fun trimPageCacheIfNeeded() {
        if (pageCacheWrites.incrementAndGet() % PAGE_CACHE_TRIM_INTERVAL != 0) return
        diskCacheMutex.withLock {
            val files = cacheDir.listFiles { file ->
                file.isFile && (file.extension.equals("webp", ignoreCase = true) || file.extension.equals("source", ignoreCase = true))
            }
                .orEmpty()
            var total = files.sumOf(File::length)
            if (total <= MAX_DISK_PAGE_CACHE_BYTES) return@withLock
            files.sortedBy(File::lastModified).forEach { file ->
                if (total <= TARGET_DISK_PAGE_CACHE_BYTES) return@forEach
                val bytes = file.length()
                if (file.delete()) total -= bytes
            }
        }
    }

    private fun scheduleRawPageCacheWrite(key: String, file: File, bytes: ByteArray, generation: Int) {
        val writeId = "$generation:$key"
        if (!pageCacheWritesInFlight.add(writeId)) return
        if (!rawCacheWriteLimiter.tryAcquire()) {
            pageCacheWritesInFlight.remove(writeId)
            return
        }
        cacheScope.launch {
            try {
                val temporary = File(cacheDir, "$key.$generation.tmp")
                try {
                    temporary.outputStream().buffered().use { output -> output.write(bytes) }
                    val cacheWritten = diskCacheMutex.withLock {
                        if (generation != pageCacheGeneration.get()) return@withLock false
                        if (!temporary.renameTo(file)) temporary.copyTo(file, overwrite = true)
                        true
                    }
                    if (cacheWritten) trimPageCacheIfNeeded()
                } finally {
                    temporary.delete()
                }
            } finally {
                rawCacheWriteLimiter.release()
                pageCacheWritesInFlight.remove(writeId)
            }
        }
    }

    private fun scheduleDecodedPageCacheWrite(
        key: String,
        file: File,
        bitmap: Bitmap,
        webpQuality: Int,
        generation: Int,
    ) {
        val writeId = "$generation:$key:webp"
        if (!pageCacheWritesInFlight.add(writeId)) return
        val bitmapReference = WeakReference(bitmap)
        cacheScope.launch {
            try {
                delay(DECODED_CACHE_WRITE_DELAY_MILLIS)
                awaitVisiblePageIdle()
                rawCacheWriteLimiter.withPermit {
                    // A new visible request can arrive between the idle check
                    // and the permit acquisition. Re-check before spending CPU
                    // on compression so a cache write never wins over reading.
                    if (
                        generation != pageCacheGeneration.get() ||
                        file.isFile ||
                        visiblePageRequestCount.get() > 0
                    ) return@withPermit
                    val cachedBitmap = bitmapReference.get()?.takeUnless(Bitmap::isRecycled) ?: return@withPermit
                    val temporary = File(cacheDir, "$key.$generation.webp.tmp")
                    try {
                        runAtBackgroundPriority {
                            temporary.outputStream().buffered().use { output ->
                                if (!cachedBitmap.compress(Bitmap.CompressFormat.WEBP, webpQuality, output)) {
                                    throw JmSourceException()
                                }
                            }
                        }
                        val cacheWritten = diskCacheMutex.withLock {
                            if (generation != pageCacheGeneration.get()) return@withLock false
                            if (!temporary.renameTo(file)) temporary.copyTo(file, overwrite = true)
                            true
                        }
                        if (cacheWritten) trimPageCacheIfNeeded()
                    } finally {
                        temporary.delete()
                    }
                }
            } catch (_: Exception) {
                // Raw bytes remain a valid fallback when decoded-cache compression fails.
            } finally {
                pageCacheWritesInFlight.remove(writeId)
            }
        }
    }

    private inline fun <T> runAtBackgroundPriority(block: () -> T): T {
        val threadId = Process.myTid()
        val previousPriority = Process.getThreadPriority(threadId)
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        return try {
            block()
        } finally {
            Process.setThreadPriority(previousPriority)
        }
    }

    private fun cacheBitmapIfCurrent(key: String, bitmap: Bitmap, generation: Int) {
        if (generation == pageCacheGeneration.get() && closed.get() == 0) {
            bitmapCache.put(key, bitmap)
        }
    }

    private fun isPageVisible(key: String): Boolean = (visiblePageWaiters[key]?.get() ?: 0) > 0
    private fun isPageHedgeRequested(key: String): Boolean = (hedgePageWaiters[key]?.get() ?: 0) > 0
    private fun isPageTurbo(key: String): Boolean = (turboPageWaiters[key]?.get() ?: 0) > 0

    private suspend fun awaitVisiblePageIdle() {
        while (visiblePageRequestCount.get() > 0 && closed.get() == 0) {
            delay(BACKGROUND_PRIORITY_POLL_MILLIS)
        }
    }

    private suspend fun <T> withImageDecodeTurn(key: String, block: () -> T): T {
        while (true) {
            if (isPageVisible(key)) return imageWorkLimiter.withPermit { block() }
            backgroundImageWorkLimiter.acquire()
            try {
                while (visiblePageRequestCount.get() > 0 && !isPageVisible(key)) {
                    delay(BACKGROUND_PRIORITY_POLL_MILLIS)
                }
                if (isPageVisible(key)) return imageWorkLimiter.withPermit { block() }
                imageWorkLimiter.acquire()
                try {
                    if (visiblePageRequestCount.get() > 0 && !isPageVisible(key)) continue
                    try {
                        return block()
                    } catch (_: BackgroundImageWorkPreempted) {
                        // Region decoding is restartable. Give the slot to the
                        // newly visible page and retry this background page
                        // once foreground work becomes idle again.
                        continue
                    }
                } finally {
                    imageWorkLimiter.release()
                }
            } finally {
                backgroundImageWorkLimiter.release()
            }
        }
    }

    private suspend fun decodeFilePageWithTurn(
        file: File,
        key: String,
        profile: PageDecodeProfile,
        generation: Int,
    ): Bitmap? {
        if (!file.isFile) return null
        return try {
            withImageDecodeTurn(key) { decodeFilePage(file.absolutePath, key, profile, generation) }
                ?: run {
                    // A malformed/partially written decoded cache must not stay
                    // around: its presence otherwise prevents the next request
                    // from rebuilding the cache from the raw/network bytes.
                    file.delete()
                    null
                }
        } catch (error: BackgroundImageWorkPreempted) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    private suspend fun decodeRawPageFileWithTurn(
        file: File,
        page: JmPage,
        key: String,
        profile: PageDecodeProfile,
        generation: Int,
    ): Bitmap? {
        if (!file.isFile) return null
        return withImageDecodeTurn(key) { decodeRawPageFile(file, page, key, profile, generation) }
    }

    private suspend fun fetchImage(
        page: JmPage,
        key: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        hedgeImageHosts: Boolean,
        hedgeDelayMillis: Long,
    ): ByteArray = withPageNetworkTurn(key) {
        var last: Throwable? = null
        val urls = orderedImageUrls(page)
        var remainingUrls = urls
        if ((hedgeImageHosts || isPageHedgeRequested(key)) && urls.size >= 2) {
            try {
                return@withPageNetworkTurn fetchImageHedged(
                    urls[0],
                    urls[1],
                    page,
                    key,
                    onProgress,
                    hedgeDelayMillis,
                ).also { fetched ->
                    recordImageSuccess(fetched.url)
                }.bytes
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                last = error
            }
            remainingUrls = urls.drop(2)
        }
        for (url in remainingUrls) {
            val attempt = fetchImageAttempt(url, page, key, onProgress)
            attempt.getOrNull()?.let { fetched ->
                recordImageSuccess(fetched.url)
                return@withPageNetworkTurn fetched.bytes
            }
            last = attempt.exceptionOrNull() ?: last
        }
        throw last ?: JmSourceException()
    }

    private suspend fun <T> withPageNetworkTurn(key: String, block: suspend () -> T): T {
        while (true) {
            if (isPageVisible(key)) {
                return block()
            }
            while (visiblePageRequestCount.get() > 0 && !isPageVisible(key)) {
                delay(BACKGROUND_PRIORITY_POLL_MILLIS)
            }
            if (isPageVisible(key)) continue
            val limiter = if (isPageTurbo(key) && maxConcurrentImageWork > 1) {
                turboBackgroundNetworkLimiter
            } else {
                backgroundNetworkLimiter
            }
            if (!limiter.tryAcquire()) {
                delay(BACKGROUND_PRIORITY_POLL_MILLIS)
                continue
            }
            try {
                if (visiblePageRequestCount.get() > 0 && !isPageVisible(key)) continue
                if (isPageVisible(key)) continue
                return block()
            } finally {
                limiter.release()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun fetchImageHedged(
        primaryUrl: String,
        secondaryUrl: String,
        page: JmPage,
        key: String,
        onProgress: (Long, Long) -> Unit,
        hedgeDelayMillis: Long,
    ): FetchedImage = supervisorScope {
        val primary = async { openImageAttempt(primaryUrl, page) }
        val earlyPrimary = if (hedgeDelayMillis > 0L) {
            withTimeoutOrNull(hedgeDelayMillis) { primary.await() }
        } else {
            null
        }
        if (earlyPrimary != null) {
            earlyPrimary.getOrNull()?.let { opened ->
                return@supervisorScope readOpenedImage(opened, key, onProgress)
            }
            return@supervisorScope fetchImageAttempt(secondaryUrl, page, key, onProgress).getOrThrow()
        }

        val secondary = async { openImageAttempt(secondaryUrl, page) }
        val (firstResult, other) = select<Pair<Result<OpenedImage>, Deferred<Result<OpenedImage>>>> {
            primary.onAwait { it to secondary }
            secondary.onAwait { it to primary }
        }
        firstResult.getOrNull()?.let { opened ->
            other.cancel()
            other.invokeOnCompletion {
                if (!other.isCancelled) runCatching { other.getCompleted().getOrNull()?.response?.close() }
            }
            return@supervisorScope readOpenedImage(opened, key, onProgress)
        }
        val secondResult = other.await()
        secondResult.getOrNull()?.let { opened ->
            return@supervisorScope readOpenedImage(opened, key, onProgress)
        }
        throw secondResult.exceptionOrNull() ?: firstResult.exceptionOrNull() ?: JmSourceException()
    }

    private suspend fun openImageAttempt(url: String, page: JmPage): Result<OpenedImage> {
        val call = client.newCall(imageRequest(url, page))
        var openedResponse: Response? = null
        return try {
            val response = runInterruptible(Dispatchers.IO) { call.execute() }
            openedResponse = response
        if (!response.isSuccessful) {
            response.close()
            openedResponse = null
            throw JmSourceException()
        }
        val total = response.body.contentLength()
        if (total > MAX_PAGE_BYTES) {
            response.close()
            openedResponse = null
            throw JmSourceException()
        }
        openedResponse = null
        Result.success(OpenedImage(url, response))
        } catch (error: CancellationException) {
            call.cancel()
            openedResponse?.close()
            throw error
        } catch (error: Exception) {
            call.cancel()
            openedResponse?.close()
            url.toHttpUrlOrNull()?.host?.let { failedImageHosts[it] = System.currentTimeMillis() }
            Result.failure(error)
        }
    }

    private suspend fun fetchImageAttempt(
        url: String,
        page: JmPage,
        key: String,
        onProgress: (Long, Long) -> Unit,
    ): Result<FetchedImage> = try {
        Result.success(FetchedImage(url, fetchImageUrl(url, page, key, onProgress)))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        url.toHttpUrlOrNull()?.host?.let { failedImageHosts[it] = System.currentTimeMillis() }
        Result.failure(error)
    }

    private suspend fun fetchImageUrl(
        url: String,
        page: JmPage,
        key: String,
        onProgress: (Long, Long) -> Unit,
    ): ByteArray {
        val call = client.newCall(imageRequest(url, page))
        return try {
            runInterruptible(Dispatchers.IO) { call.execute() }.use { response ->
                if (!response.isSuccessful) throw JmSourceException()
                readImageBody(response.body, key, onProgress)
            }
        } catch (error: CancellationException) {
            call.cancel()
            throw error
        }
    }

    private fun imageRequest(url: String, page: JmPage): Request = Request.Builder()
        .url(url)
        .header("Accept", "image/webp,image/*,*/*;q=0.8")
        .header("X-Requested-With", "com.JMComic3.app")
        .header("Referer", page.referer)
        .header("User-Agent", APP_USER_AGENT)
        .get()
        .build()

    private suspend fun readOpenedImage(
        opened: OpenedImage,
        key: String,
        onProgress: (Long, Long) -> Unit,
    ): FetchedImage = opened.response.use { response ->
        FetchedImage(opened.url, readImageBody(response.body, key, onProgress))
    }

    private suspend fun readImageBody(
        body: ResponseBody,
        key: String,
        onProgress: (Long, Long) -> Unit,
    ): ByteArray {
        val total = body.contentLength()
        if (total > MAX_PAGE_BYTES) throw JmSourceException()
        return withContext(Dispatchers.IO) {
            body.byteStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                val expected = total.toInt().takeIf { total in 1..MAX_PAGE_BYTES.toLong() }
                if (expected != null) {
                    val bytes = ByteArray(expected)
                    var done = 0
                    while (done < expected) {
                        while (visiblePageRequestCount.get() > 0 && !isPageVisible(key)) {
                            delay(BACKGROUND_PRIORITY_POLL_MILLIS)
                        }
                        val read = runInterruptible(Dispatchers.IO) {
                            input.read(bytes, done, expected - done)
                        }
                        val progressed = if (read == 0) {
                            runInterruptible(Dispatchers.IO) { input.read() }
                        } else {
                            read
                        }
                        if (progressed < 0) break
                        if (read == 0) bytes[done] = progressed.toByte()
                        done += if (read == 0) 1 else progressed
                        onProgress(done.toLong(), total)
                    }
                    if (done != expected) throw JmSourceException()
                    return@withContext bytes
                }

                val sink = java.io.ByteArrayOutputStream(16 * 1024)
                var done = 0L
                while (true) {
                    while (visiblePageRequestCount.get() > 0 && !isPageVisible(key)) {
                        delay(BACKGROUND_PRIORITY_POLL_MILLIS)
                    }
                    val read = runInterruptible(Dispatchers.IO) { input.read(buffer) }
                    val progressed = if (read == 0) {
                        runInterruptible(Dispatchers.IO) { input.read() }
                    } else {
                        read
                    }
                    if (progressed < 0) break
                    if (read == 0) {
                        buffer[0] = progressed.toByte()
                    }
                    done += if (read == 0) 1 else progressed
                    if (done > MAX_PAGE_BYTES) throw JmSourceException()
                    sink.write(buffer, 0, if (read == 0) 1 else progressed)
                    onProgress(done, total)
                }
                sink.toByteArray()
            }
        }
    }

    private fun recordImageSuccess(url: String) {
        url.toHttpUrlOrNull()?.host?.let { host ->
            failedImageHosts.remove(host)
            if (host !in imageDomains) return@let
            synchronized(sourceStateLock) {
                if (autoSelectSource && preferredImageHost != host) {
                    preferredImageHost = host
                    sourceSnapshot = sourceSnapshot.copy(selectedImageHost = host)
                    sourcePreferences.edit { putString(PREFERRED_IMAGE_HOST_KEY, host) }
                }
            }
        }
    }

    private fun cachePageAspectRatioIfCurrent(key: String, width: Int, height: Int, generation: Int) {
        if (width <= 0 || height <= 0) return
        if (generation != pageCacheGeneration.get() || closed.get() != 0) return
        val ratio = (width.toFloat() / height.toFloat()).coerceIn(0.05f, 8f)
        pageAspectRatioCache[key] = ratio
    }

    private fun recordPageAspectRatio(key: String, width: Int, height: Int, generation: Int) {
        if (width <= 0 || height <= 0) return
        val ratio = (width.toFloat() / height.toFloat()).coerceIn(0.05f, 8f)
        cachePageAspectRatioIfCurrent(key, width, height, generation)
        pageAspectRatioCallbacks.forEach(key) { callback ->
            postAspectRatioIfSubscribed(key, callback, ratio)
        }
    }

    private fun postAspectRatioIfSubscribed(key: String, callback: (Float) -> Unit, ratio: Float) {
        if (closed.get() != 0 || pageAspectRatioCallbacks.referenceCount(key, callback) <= 0) return
        mainHandler.post {
            if (closed.get() == 0 && pageAspectRatioCallbacks.referenceCount(key, callback) > 0) {
                runCatching { callback(ratio) }
            }
        }
    }

    private fun pageDecodeProfile(
        quality: ReaderImageQuality,
        turboMode: Boolean,
    ): PageDecodeProfile {
        val maxWidth = if (turboMode) {
            TURBO_DECODED_PAGE_WIDTH
        } else {
            when (quality) {
                ReaderImageQuality.Low -> LOW_DECODED_PAGE_WIDTH
                ReaderImageQuality.Medium -> MEDIUM_DECODED_PAGE_WIDTH
                ReaderImageQuality.High -> MAX_DECODED_PAGE_WIDTH
            }
        }
        val requestedPixels = if (turboMode) {
            TURBO_DECODED_PAGE_PIXELS
        } else {
            when (quality) {
                ReaderImageQuality.Low -> LOW_DECODED_PAGE_PIXELS
                ReaderImageQuality.Medium -> MEDIUM_DECODED_PAGE_PIXELS
                ReaderImageQuality.High -> MAX_DECODED_PAGE_PIXELS
            }
        }
        val webpQuality = when {
            turboMode -> 72
            quality == ReaderImageQuality.Low -> 80
            quality == ReaderImageQuality.Medium -> 88
            else -> 92
        }
        val cappedPixels = minOf(maxDecodedPagePixels, requestedPixels)
        return PageDecodeProfile(
            maxWidth = maxWidth,
            maxPixels = cappedPixels,
            cacheWebpQuality = webpQuality,
            hedgeDelayMillis = if (turboMode) TURBO_IMAGE_HEDGE_DELAY_MILLIS else IMAGE_HEDGE_DELAY_MILLIS,
            cacheToken = "${if (turboMode) "turbo" else quality.name.lowercase()}-$maxWidth-$cappedPixels",
            turboMode = turboMode,
        )
    }

    @Suppress("DEPRECATION")
    private fun decodePage(
        bytes: ByteArray,
        page: JmPage,
        key: String,
        profile: PageDecodeProfile,
        generation: Int,
    ): Bitmap {
        if (bytes.isEmpty() || bytes.size > MAX_PAGE_BYTES) throw JmSourceException()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw JmSourceException()
        if (bounds.outWidth.toLong() * bounds.outHeight.toLong() > MAX_PAGE_PIXELS) throw JmSourceException()
        recordPageAspectRatio(key, bounds.outWidth, bounds.outHeight, generation)
        val segments = segmentationCount(page.scrambleId, page.photoId, page.fileName)
        if (segments > bounds.outHeight) throw JmSourceException()
        val decoder = try {
            BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
        } catch (_: Exception) {
            throw JmSourceException()
        }
        val sourceRanges = if (segments == 0) {
            ordinaryPageSourceRanges(bounds.outWidth, bounds.outHeight)
        } else {
            scrambledPageSourceRanges(bounds.outHeight, segments)
        }
        return decodeRegionPage(
            decoder,
            bounds.outWidth,
            bounds.outHeight,
            sourceRanges,
            profile,
            shouldYield = { visiblePageRequestCount.get() > 0 && !isPageVisible(key) },
        )
    }

    /**
     * Decode the source once at a bounded common resolution, reorder it there, and only then scale
     * the complete page. Scaling or sampling every strip independently makes the filter kernel
     * restart at each edge; on some Android/Skia versions that shows up as a one-pixel horizontal
     * tear. The sampled source cap preserves the memory guard used for very long pages.
     */
    private fun decodeRegionPage(
        decoder: BitmapRegionDecoder,
        sourceWidth: Int,
        sourceHeight: Int,
        sourceRanges: List<Pair<Int, Int>>,
        profile: PageDecodeProfile,
        shouldYield: () -> Boolean = { false },
    ): Bitmap {
        if (sourceRanges.isEmpty()) {
            decoder.recycle()
            throw JmSourceException()
        }
        val target = decodedPageSize(sourceWidth, sourceHeight, profile.maxPixels, profile.maxWidth)
        val sampleSize = stitchedRegionSampleSize(sourceWidth, sourceHeight, profile.maxPixels)
        var sampledSource: Bitmap? = null
        var stitched: Bitmap? = null
        var orderedPage: Bitmap? = null
        try {
            if (shouldYield()) throw BackgroundImageWorkPreempted()
            val sampled = decoder.decodeRegion(
                Rect(0, 0, sourceWidth, sourceHeight),
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inScaled = false
                },
            ) ?: throw JmSourceException()
            sampledSource = sampled
            if (shouldYield()) throw BackgroundImageWorkPreempted()

            val ordered = if (sourceRangesAreSequential(sourceRanges, sourceHeight)) {
                sampled.also { sampledSource = null }
            } else {
                val assembled = createBitmap(sampled.width, sampled.height, Bitmap.Config.RGB_565)
                stitched = assembled
                val canvas = Canvas(assembled)
                // Source and destination rectangles have identical sampled dimensions.
                // Keep this as a pixel copy; filtering belongs to the final whole-page scale.
                val copyPaint = Paint(Paint.DITHER_FLAG)
                var destinationTop = 0
                sourceRanges.forEach { sourceRange ->
                    if (shouldYield()) throw BackgroundImageWorkPreempted()
                    val sourceTop = sampledBoundary(sourceRange.first, sourceHeight, sampled.height)
                    val sourceBottom = sampledBoundary(sourceRange.second, sourceHeight, sampled.height)
                    if (sourceBottom > sourceTop) {
                        val rangeHeight = sourceBottom - sourceTop
                        canvas.drawBitmap(
                            sampled,
                            Rect(0, sourceTop, sampled.width, sourceBottom),
                            Rect(0, destinationTop, sampled.width, destinationTop + rangeHeight),
                            copyPaint,
                        )
                        destinationTop += rangeHeight
                    }
                }
                if (destinationTop != sampled.height) throw JmSourceException()
                sampled.recycle()
                sampledSource = null
                assembled.also { stitched = null }
            }
            orderedPage = ordered

            if (ordered.width == target.first && ordered.height == target.second) {
                orderedPage = null
                return ordered
            }
            val scaled = createBitmap(target.first, target.second, Bitmap.Config.RGB_565)
            try {
                Canvas(scaled).drawBitmap(
                    ordered,
                    null,
                    Rect(0, 0, scaled.width, scaled.height),
                    Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
                )
            } catch (error: Throwable) {
                scaled.recycle()
                throw error
            }
            ordered.recycle()
            orderedPage = null
            return scaled
        } catch (error: Throwable) {
            sampledSource?.recycle()
            stitched?.recycle()
            orderedPage?.recycle()
            throw error
        } finally {
            decoder.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun decodeFilePage(path: String, key: String, profile: PageDecodeProfile, generation: Int): Bitmap? {
        val file = File(path)
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_PAGE_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (bounds.outWidth.toLong() * bounds.outHeight.toLong() > MAX_PAGE_PIXELS) return null
        recordPageAspectRatio(key, bounds.outWidth, bounds.outHeight, generation)
        val decoder = try {
            BitmapRegionDecoder.newInstance(path, false)
        } catch (_: Exception) {
            return null
        }
        return decodeRegionPage(
            decoder = decoder,
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
            sourceRanges = ordinaryPageSourceRanges(bounds.outWidth, bounds.outHeight),
            profile = profile,
            shouldYield = { visiblePageRequestCount.get() > 0 && !isPageVisible(key) },
        )
    }

    @Suppress("DEPRECATION")
    private fun decodeRawPageFile(
        file: File,
        page: JmPage,
        key: String,
        profile: PageDecodeProfile,
        generation: Int,
    ): Bitmap? {
        if (!file.isFile || file.length() !in 1..MAX_PAGE_BYTES.toLong()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw JmSourceException()
            if (bounds.outWidth.toLong() * bounds.outHeight.toLong() > MAX_PAGE_PIXELS) throw JmSourceException()
            recordPageAspectRatio(key, bounds.outWidth, bounds.outHeight, generation)
            val segments = segmentationCount(page.scrambleId, page.photoId, page.fileName)
            if (segments > bounds.outHeight) throw JmSourceException()
            val sourceRanges = if (segments == 0) {
                ordinaryPageSourceRanges(bounds.outWidth, bounds.outHeight)
            } else {
                scrambledPageSourceRanges(bounds.outHeight, segments)
            }
            val decoder = try {
                BitmapRegionDecoder.newInstance(file.absolutePath, false)
            } catch (_: Exception) {
                throw JmSourceException()
            }
            decodeRegionPage(
                decoder,
                bounds.outWidth,
                bounds.outHeight,
                sourceRanges,
                profile,
                shouldYield = { visiblePageRequestCount.get() > 0 && !isPageVisible(key) },
            )
        } catch (error: BackgroundImageWorkPreempted) {
            throw error
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    private fun isValidDecodedPageFile(file: File): Boolean {
        if (!file.isFile || file.length() !in 1..MAX_PAGE_BYTES.toLong()) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0 &&
            bounds.outWidth.toLong() * bounds.outHeight.toLong() <= MAX_PAGE_PIXELS
    }

    private suspend fun fetchScramble(photoId: String): String {
        val now = System.currentTimeMillis()
        cachedScrambleId?.takeIf {
            cachedScramblePhotoId == photoId && now - cachedScrambleAt < SCRAMBLE_CACHE_MILLIS
        }?.let { return it }
        return scrambleMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            cachedScrambleId?.takeIf {
                cachedScramblePhotoId == photoId && lockedNow - cachedScrambleAt < SCRAMBLE_CACHE_MILLIS
            }?.let {
                return@withLock it
            }
            fetchScrambleFresh(photoId).also { scramble ->
                cachedScrambleId = scramble
                cachedScramblePhotoId = photoId
                cachedScrambleAt = lockedNow
            }
        }
    }

    private suspend fun fetchScrambleFresh(photoId: String): String = withContext(Dispatchers.IO) {
        val path = "/chapter_view_template?id=$photoId&mode=vertical&page=0&app_img_shunt=1&express=off&v=${epochSeconds()}"
        val attempted = domains.take(MAX_API_CANDIDATES)
        for (domain in attempted) {
            runCatching {
                ensureCookies(domain)
                val timestamp = epochSeconds()
                execute(domain, path, timestamp, APP_CONTENT_PROTOCOL_KEY).use { response ->
                    if (response.isSuccessful) scrambleRegex.find(response.body.readStringLimited(MAX_API_RESPONSE_BYTES))?.groupValues?.getOrNull(1)?.let { return@withContext it }
                }
            }.onFailure { if (it is CancellationException) throw it }
        }
        val refreshed = discoverDomains()
        val attemptedSet = attempted.toHashSet()
        for (domain in refreshed.filterNot(attemptedSet::contains).take(MAX_API_CANDIDATES)) {
            runCatching {
                ensureCookies(domain)
                val timestamp = epochSeconds()
                execute(domain, path, timestamp, APP_CONTENT_PROTOCOL_KEY).use { response ->
                    if (response.isSuccessful) scrambleRegex.find(response.body.readStringLimited(MAX_API_RESPONSE_BYTES))?.groupValues?.getOrNull(1)?.let { return@withContext it }
                }
            }.onFailure { if (it is CancellationException) throw it }
        }
        DEFAULT_SCRAMBLE_ID
    }

    private suspend fun requestJson(path: String): JSONObject = withContext(Dispatchers.IO) {
        if (closed.get() != 0) throw CancellationException("JM 网关已关闭")
        val attempted = domains.take(MAX_API_CANDIDATES)
        requestAcrossDomains(path, attempted)?.let { return@withContext it }
        val refreshed = discoverDomains()
        val attemptedSet = attempted.toHashSet()
        requestAcrossDomains(path, refreshed.filterNot(attemptedSet::contains))?.let { return@withContext it }
        throw JmSourceException()
    }

    /**
     * Reads an endpoint that requires the current AVS session.
     *
     * Anonymous reads deliberately hide per-domain failures so one bad source
     * does not interrupt browsing.  Account reads need to preserve a real
     * 401/403, otherwise an expired session would be reported as a generic
     * source outage and remain persisted forever.
     */
    private suspend fun requestAuthenticatedJson(path: String): JSONObject = withContext(Dispatchers.IO) {
        if (closed.get() != 0) throw CancellationException("JM 网关已关闭")
        var authError: JmAuthException? = null

        suspend fun tryDomains(candidates: List<String>): JSONObject? {
            for (domain in candidates.take(MAX_API_CANDIDATES)) {
                val result = requestDomainJson(path, domain)
                result.getOrNull()?.let { return it }
                result.exceptionOrNull()?.let { error ->
                    if (error is JmAuthException && authError == null) authError = error
                }
            }
            return null
        }

        val attempted = domains.take(MAX_API_CANDIDATES)
        tryDomains(attempted)?.let { return@withContext it }
        val refreshed = runCatching { discoverDomains() }
            .onFailure { error -> if (error is CancellationException) throw error }
            .getOrDefault(emptyList())
        val attemptedSet = attempted.toHashSet()
        tryDomains(refreshed.filterNot(attemptedSet::contains))?.let { return@withContext it }
        throw (authError ?: JmSourceException())
    }

    private suspend fun requestPostJson(
        path: String,
        form: Map<String, String>,
        allowUnauthenticated: Boolean = false,
        retryAcrossDomains: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) {
        if (closed.get() != 0) throw CancellationException("JM 网关已关闭")
        if (!allowUnauthenticated && authenticatedSession == null) {
            throw JmAuthException("请先登录 JM 官方账号")
        }
        var lastError: Throwable? = null
        suspend fun tryDomains(candidates: List<String>): JSONObject? {
            for (domain in candidates) {
                val result = requestDomainJson(path, domain, form)
                result.getOrNull()?.let { return it }
                result.exceptionOrNull()?.let { error ->
                    lastError = error
                    if (error is JmAuthException) throw error
                }
            }
            return null
        }
        val initialCandidates = orderedMutationDomains()

        if (!retryAcrossDomains) {
            // /favorite and /like are server-side toggles rather than
            // idempotent "set" operations. Probe a healthy source first, then
            // submit exactly once; replaying an ambiguous POST could undo the
            // successful toggle on the next domain.
            suspend fun submitOnce(candidates: List<String>): JSONObject? {
                for (domain in candidates.take(MAX_API_CANDIDATES)) {
                    val probe = requestDomainJson("/setting", domain)
                    probe.exceptionOrNull()?.let { error -> lastError = error }
                    if (probe.isFailure) continue
                    val result = requestDomainJson(path, domain, form)
                    result.getOrNull()?.let { return it }
                    result.exceptionOrNull()?.let { error -> throw error }
                }
                return null
            }

            submitOnce(initialCandidates)?.let { return@withContext it }
            runCatching { discoverDomains(force = true) }
                .onFailure { error -> if (error is CancellationException) throw error }
                .getOrNull()
                ?.let { refreshed ->
                    val attempted = initialCandidates.toHashSet()
                    submitOnce(refreshed.filterNot(attempted::contains))?.let { return@withContext it }
                }
            throw (lastError ?: JmSourceException())
        }

        tryDomains(initialCandidates)?.let { return@withContext it }
        // A stale source list should not prevent a valid account operation.
        // Refresh once after the initial candidates fail, while preserving the
        // first meaningful error if discovery itself is unavailable.
        runCatching { discoverDomains(force = true) }
            .onFailure { error -> if (error is CancellationException) throw error }
            .getOrNull()
            ?.let { refreshed ->
                val attempted = initialCandidates.toHashSet()
                tryDomains(refreshed.filterNot(attempted::contains).take(MAX_API_CANDIDATES))
                    ?.let { return@withContext it }
            }
        throw (lastError ?: JmSourceException())
    }

    private fun orderedMutationDomains(): List<String> {
        val candidates = domains.take(MAX_API_CANDIDATES)
        val preferred = synchronized(sourceStateLock) {
            if (!autoSelectSource) preferredSourceHost else null
        }
        return buildList {
            preferred?.takeIf { it in candidates }?.let(::add)
            addAll(candidates.filterNot { it == preferred })
        }.distinct()
    }

    private suspend fun requestAcrossDomains(path: String, candidates: List<String>): JSONObject? {
        val domains = candidates.take(MAX_API_CANDIDATES)
        val (manualSelectionEnabled, manualPreferredHost) = synchronized(sourceStateLock) {
            (!autoSelectSource) to preferredSourceHost
        }
        if (manualSelectionEnabled && manualPreferredHost != null) {
            val manuallyOrdered = buildList {
                if (manualPreferredHost in domains) add(manualPreferredHost)
                addAll(domains.filterNot { it == manualPreferredHost })
            }.distinct().take(MAX_API_CANDIDATES)
            manuallyOrdered.forEach { domain ->
                requestDomainJson(path, domain).getOrNull()?.let { return it }
            }
            return null
        }
        if (domains.size >= 2) {
            raceDomainJson(path, domains[0], domains[1])?.let { return it }
        } else {
            return domains.firstOrNull()?.let { requestDomainJson(path, it).getOrNull() }
        }
        for (domain in domains.drop(2)) {
            requestDomainJson(path, domain).getOrNull()?.let { return it }
        }
        return null
    }

    private suspend fun raceDomainJson(path: String, primaryDomain: String, secondaryDomain: String): JSONObject? =
        supervisorScope {
            val primary = async { requestDomainJson(path, primaryDomain) }
            val earlyPrimary = withTimeoutOrNull(API_HEDGE_DELAY_MILLIS) { primary.await() }
            if (earlyPrimary != null) {
                earlyPrimary.getOrNull()?.let { return@supervisorScope it }
                return@supervisorScope requestDomainJson(path, secondaryDomain).getOrNull()
            }

            val secondary = async { requestDomainJson(path, secondaryDomain) }
            val (firstResult, other) = select<Pair<Result<JSONObject>, Deferred<Result<JSONObject>>>> {
                primary.onAwait { it to secondary }
                secondary.onAwait { it to primary }
            }
            firstResult.getOrNull()?.let { result ->
                other.cancel()
                return@supervisorScope result
            }
            other.await().getOrNull()
        }

    private suspend fun requestDomainJson(
        path: String,
        domain: String,
        form: Map<String, String>? = null,
    ): Result<JSONObject> = try {
        ensureCookies(domain)
        val timestamp = epochSeconds()
        val result = execute(domain, path, timestamp, APP_TOKEN_PROTOCOL_KEY, form).use { response ->
            val envelope = JSONObject(response.body.readStringLimited(MAX_API_RESPONSE_BYTES))
            val responseCode = envelope.int("code") ?: response.code
            if (!response.isSuccessful || responseCode != 200) {
                val message = firstJsonString(envelope, "errorMsg", "msg", "message")
                    .take(MAX_ERROR_MESSAGE_LENGTH)
                    .ifBlank { "JM 官方接口请求失败" }
                if (responseCode == 401 || responseCode == 403 || path == "/login" && responseCode == 422) {
                    throw JmAuthException(message)
                }
                throw JmApiException(message, response.code)
            }
            val encrypted = envelope.string("data")
            if (encrypted.isBlank()) throw JmSourceException()
            JSONObject(protocolDecrypt(encrypted, timestamp, APP_DATA_PROTOCOL_KEY))
        }
        Result.success(result)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun ensureCookies(domain: String) {
        installSessionCookie(domain)
        if (domain in initializedCookieHosts) return
        cookieMutex.withLock {
            installSessionCookie(domain)
            if (domain in initializedCookieHosts) return@withLock
            val initialized = runCatching {
                val timestamp = epochSeconds()
                execute(domain, "/setting", timestamp, APP_TOKEN_PROTOCOL_KEY).use { response ->
                    if (!response.isSuccessful) return@use false
                    response.body.readStringLimited(MAX_API_RESPONSE_BYTES)
                    true
                }
            }.onFailure { if (it is CancellationException) throw it }.getOrDefault(false)
            installSessionCookie(domain)
            if (initialized) initializedCookieHosts += domain
        }
    }

    private fun installSessionCookie(domain: String) {
        val avs = authenticatedSession?.avs?.takeIf { it.isNotBlank() } ?: return
        val url = "https://$domain/".toHttpUrlOrNull() ?: return
        runCatching {
            cookies.saveFromResponse(
                url,
                listOf(
                    Cookie.Builder()
                        .name("AVS")
                        .value(avs)
                        .domain(domain)
                        .path("/")
                        .build(),
                ),
            )
        }
    }

    private fun normalizeDomain(raw: String?): String? {
        return normalizeRemoteDomain(raw)
    }

    private fun sourcePreference(key: String): String =
        sourcePreferences.getString(key, null)?.take(MAX_SOURCE_PREFERENCE_CHARS).orEmpty()

    private fun sourceTimestamp(value: Long): Long {
        val now = System.currentTimeMillis().coerceAtLeast(0L)
        val maximum = if (now > Long.MAX_VALUE - MAX_SOURCE_FUTURE_MILLIS) {
            Long.MAX_VALUE
        } else {
            now + MAX_SOURCE_FUTURE_MILLIS
        }
        return value.coerceIn(0L, maximum)
    }

    private fun loadOfficialDomains(): List<String> = sourcePreference(SOURCE_OFFICIAL_DOMAINS_KEY)
        .lineSequence()
        .take(MAX_SOURCE_PROBE_CANDIDATES)
        .mapNotNull(::normalizeDomain)
        .distinct()
        .take(MAX_SOURCE_PROBE_CANDIDATES)
        .toList()
        .ifEmpty { builtInDomains }

    private fun loadSourceSnapshot(fallbackDomains: List<String>): JmSourceSnapshot {
        val endpoints = sourcePreference(SOURCE_ENDPOINTS_KEY)
            .lineSequence()
            .take(MAX_SOURCE_PROBE_CANDIDATES)
            .mapNotNull { line ->
                val parts = line.split('|', limit = 2)
                val host = normalizeDomain(parts.getOrNull(0)) ?: return@mapNotNull null
                val latency = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it in 0L..MAX_SOURCE_LATENCY_MS }
                JmSourceEndpoint(host, latency)
            }
            .distinctBy(JmSourceEndpoint::host)
            .take(MAX_SOURCE_PROBE_CANDIDATES)
            .toList()
        val allowedHosts = fallbackDomains.toHashSet()
        val resolved = (endpoints.filter { it.host in allowedHosts } + fallbackDomains.map { JmSourceEndpoint(it, null) })
            .distinctBy(JmSourceEndpoint::host)
            .take(MAX_SOURCE_PROBE_CANDIDATES)
        val imageEndpoints = sourcePreference(SOURCE_IMAGE_ENDPOINTS_KEY)
            .lineSequence()
            .take(imageDomains.size)
            .mapNotNull { line ->
                val parts = line.split('|', limit = 2)
                val host = normalizeDomain(parts.getOrNull(0)) ?: return@mapNotNull null
                if (host !in imageDomains) return@mapNotNull null
                val latency = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it in 0L..MAX_SOURCE_LATENCY_MS }
                JmSourceEndpoint(host, latency)
            }
            .distinctBy(JmSourceEndpoint::host)
            .toList()
        val resolvedImages = (imageEndpoints + imageDomains.map { JmSourceEndpoint(it, null) })
            .distinctBy(JmSourceEndpoint::host)
        val savedImageHost = normalizeDomain(sourcePreference(PREFERRED_IMAGE_HOST_KEY))
            ?.takeIf { it in imageDomains }
        return JmSourceSnapshot(
            endpoints = resolved,
            selectedHost = resolved.firstOrNull()?.host,
            updatedAt = sourceTimestamp(sourcePreferences.getLong(SOURCE_UPDATED_AT_KEY, 0L)),
            imageEndpoints = resolvedImages,
            selectedImageHost = savedImageHost ?: resolvedImages.firstOrNull()?.host,
            imageUpdatedAt = sourceTimestamp(sourcePreferences.getLong(SOURCE_IMAGE_UPDATED_AT_KEY, 0L)),
        )
    }

    private fun saveOfficialDomains() {
        sourcePreferences.edit {
            putString(SOURCE_OFFICIAL_DOMAINS_KEY, officialDomains.joinToString("\n").take(MAX_SOURCE_PREFERENCE_CHARS))
        }
    }

    private fun saveSourceState(snapshot: JmSourceSnapshot) {
        sourcePreferences.edit {
            putString(
                SOURCE_ENDPOINTS_KEY,
                snapshot.endpoints.joinToString("\n") { endpoint ->
                    "${endpoint.host}|${endpoint.latencyMs ?: -1L}"
                }.take(MAX_SOURCE_PREFERENCE_CHARS),
            )
            putLong(SOURCE_UPDATED_AT_KEY, sourceTimestamp(snapshot.updatedAt))
            putString(
                SOURCE_IMAGE_ENDPOINTS_KEY,
                snapshot.imageEndpoints.joinToString("\n") { endpoint ->
                    "${endpoint.host}|${endpoint.latencyMs ?: -1L}"
                }.take(MAX_SOURCE_PREFERENCE_CHARS),
            )
            putString(PREFERRED_IMAGE_HOST_KEY, snapshot.selectedImageHost)
            putLong(SOURCE_IMAGE_UPDATED_AT_KEY, sourceTimestamp(snapshot.imageUpdatedAt))
        }
    }

    private fun rotatedImageDomains(seed: String): List<String> {
        val offset = lockStripeIndex(seed, imageDomains.size)
        return imageDomains.drop(offset) + imageDomains.take(offset)
    }

    private fun orderedImageUrls(page: JmPage): List<String> {
        val now = System.currentTimeMillis()
        val preferredHost = sourceSnapshot.selectedImageHost ?: preferredImageHost
        return (listOf(page.url) + page.alternativeUrls)
            .filter(::isAllowedImageUrl)
            .distinct()
            .sortedWith(
                compareBy<String>(
                    { url ->
                        val host = url.toHttpUrlOrNull()?.host
                        if (host != null && now - (failedImageHosts[host] ?: 0L) < IMAGE_HOST_COOLDOWN_MILLIS) 1 else 0
                    },
                    { url -> if (url.toHttpUrlOrNull()?.host == preferredHost) 0 else 1 },
                ),
            )
    }

    private fun preferredSourceOrder(candidates: List<String>): List<String> = buildList {
        if (!autoSelectSource) preferredSourceHost?.let(::add)
        addAll(candidates)
    }.distinct()

    private fun preferredImageSourceOrder(candidates: List<String>): List<String> = buildList {
        if (!autoSelectSource) preferredImageHost?.let(::add)
        sourceSnapshot.selectedImageHost?.let(::add)
        addAll(candidates)
    }.distinct()

    private suspend fun execute(
        domain: String,
        path: String,
        timestamp: String,
        secret: String,
        form: Map<String, String>? = null,
    ): Response {
        val requestBuilder = Request.Builder()
            .url("https://$domain$path")
            .apply { headers(timestamp, secret).forEach { (key, value) -> header(key, value) } }
        if (form == null) {
            requestBuilder.get()
        } else {
            requestBuilder.post(
                FormBody.Builder().apply {
                    form.forEach { (key, value) -> add(key, value) }
                }.build(),
            )
        }
        val call = client.newCall(requestBuilder.build())
        return try {
            runInterruptible(Dispatchers.IO) { call.execute() }
        } catch (error: CancellationException) {
            call.cancel()
            throw error
        }
    }

    private suspend fun probeSourceLatency(domain: String): Long? {
        var call: okhttp3.Call? = null
        return try {
            val startedAt = SystemClock.elapsedRealtime()
            val timestamp = epochSeconds()
            val probeCall = sourceProbeClient.newCall(
                Request.Builder()
                    .url("https://$domain/setting")
                    .apply { headers(timestamp, APP_TOKEN_PROTOCOL_KEY).forEach { (key, value) -> header(key, value) } }
                    .get()
                    .build(),
            )
            call = probeCall
            runInterruptible(Dispatchers.IO) { probeCall.execute() }.use { response ->
                if (!response.isSuccessful) return null
            }
            (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1L)
        } catch (error: CancellationException) {
            call?.cancel()
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun probeImageLatency(domain: String): Long? {
        var call: okhttp3.Call? = null
        return try {
            val startedAt = SystemClock.elapsedRealtime()
            val probeCall = sourceProbeClient.newCall(
                Request.Builder()
                    .url("https://$domain$IMAGE_PROBE_PATH")
                    .header("Referer", "https://${domains.firstOrNull() ?: builtInDomains.first()}/")
                    .header("X-Requested-With", "com.JMComic3.app")
                    .header("User-Agent", APP_USER_AGENT)
                    .head()
                    .build(),
            )
            call = probeCall
            runInterruptible(Dispatchers.IO) { probeCall.execute() }.use { response ->
                if (!isUsableImageProbeStatus(response.code)) return null
            }
            (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1L)
        } catch (error: CancellationException) {
            call?.cancel()
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private fun headers(timestamp: String, secret: String): Map<String, String> = mapOf("Accept" to "application/json", "Accept-Language" to "zh-CN,zh;q=0.9", "User-Agent" to APP_USER_AGENT, "token" to md5(timestamp + secret), "tokenparam" to "$timestamp,$APP_VERSION")

    private fun JSONObject.rankingItems(badge: String, preferResultImage: Boolean = false): List<JmRanking> =
        rankingItemsFrom("content", badge, preferResultImage)

    private fun JSONObject.rankingItemsFrom(
        key: String,
        badge: String,
        preferResultImage: Boolean = false,
    ): List<JmRanking> = array(key).objectsOrValues(MAX_RANKING_ITEMS).mapIndexedNotNull { index, element ->
        val item = element as? JSONObject ?: return@mapIndexedNotNull null
        val id = item.string("id").takeIf { it.matches(Regex("\\d{1,12}")) } ?: return@mapIndexedNotNull null
        val title = item.string("name").take(MAX_TITLE_LENGTH).ifBlank { return@mapIndexedNotNull null }
        val supporting = item.obj("category").string("title").ifBlank { item.string("author") }.take(MAX_FIELD_LENGTH)
        JmRanking(
            id,
            title,
            item.string("image").takeIf { preferResultImage && isAllowedImageUrl(it) } ?: coverUrl(id),
            item.long("total_views")?.coerceAtLeast(0L),
            item.long("likes")?.coerceAtLeast(0L),
            if (badge == "JM 热门") "$badge ${index + 1}" else badge,
            supporting,
        )
    }.distinctBy(JmRanking::id)

    private fun JSONObject.hasMorePage(page: Int, loaded: Int, total: Long?): Boolean {
        if (page >= MAX_OFFICIAL_PAGE || loaded <= 0) return false
        val pageSize = pageSize(OFFICIAL_LIST_PAGE_SIZE)
        return hasMorePagedResults(page, pageSize, loaded, total)
    }

    private fun JSONObject.pageSize(defaultValue: Int): Int =
        long("count")?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt() ?: defaultValue

    private fun String.allowedOrder() = takeIf { it in officialOrders } ?: "mr"
    private fun String.allowedSearchOrder() = takeIf { it in officialSearchOrders } ?: "mr"

    companion object {
        // These values are part of the public upstream client protocol, not project credentials.
        private const val APP_TOKEN_PROTOCOL_KEY = "18comicAPP"
        private const val APP_CONTENT_PROTOCOL_KEY = "18comicAPPContent"
        private const val APP_DATA_PROTOCOL_KEY = "185Hcomic3PAPP7R"
        private const val DOMAIN_SERVER_PROTOCOL_KEY = "diosfjckwpqpdfjkvnqQjsik"
        private const val APP_VERSION = "2.0.30"
        private const val APP_USER_AGENT = "Mozilla/5.0 (Linux; Android 9; JMComic Pure) AppleWebKit/537.36 Chrome/91.0 Mobile Safari/537.36"
        private const val DEFAULT_SCRAMBLE_ID = "220980"
        private const val MAX_API_CANDIDATES = 6
        private const val MAX_SOURCE_PROBE_CANDIDATES = 12
        private const val MAX_SOURCE_PROBE_CONCURRENCY = 3
        private const val SOURCE_PROBE_CONNECT_TIMEOUT_SECONDS = 4L
        private const val SOURCE_PROBE_READ_TIMEOUT_SECONDS = 4L
        private const val SOURCE_PROBE_CALL_TIMEOUT_SECONDS = 6L
        private const val API_HEDGE_DELAY_MILLIS = 450L
        private const val SOURCE_PREFERENCES_NAME = "comicplus_pure_sources"
        private const val SOURCE_OFFICIAL_DOMAINS_KEY = "official_domains"
        private const val SOURCE_ENDPOINTS_KEY = "source_endpoints"
        private const val SOURCE_UPDATED_AT_KEY = "source_updated_at"
        private const val SOURCE_IMAGE_ENDPOINTS_KEY = "source_image_endpoints"
        private const val SOURCE_IMAGE_UPDATED_AT_KEY = "source_image_updated_at"
        private const val PREFERRED_IMAGE_HOST_KEY = "preferred_image_host"
        private const val MAX_SOURCE_PREFERENCE_CHARS = 16 * 1024
        private const val MAX_SOURCE_LATENCY_MS = 120_000L
        private const val MAX_SOURCE_FUTURE_MILLIS = 24L * 60L * 60L * 1_000L
        private const val IMAGE_PROBE_PATH = "/media/albums/220980_3x4.jpg"
        private const val MAX_API_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val MAX_TITLE_LENGTH = 500
        private const val MAX_DESCRIPTION_LENGTH = 50_000
        private const val MAX_FIELD_LENGTH = 512
        private const val MAX_LIST_ITEMS = 200
        private const val MAX_CATALOG_ITEMS = 200
        private const val MAX_TAG_GROUPS = 100
        private const val MAX_RANKING_ITEMS = 200
        private const val MAX_SERIES_ITEMS = 5_000
        private const val MAX_OPTION_ID_LENGTH = 128
        private const val MAX_PAGE_BYTES = 40 * 1024 * 1024
        private const val MAX_PAGE_PIXELS = 80_000_000L
        private const val MAX_DECODED_PAGE_PIXELS = 12_000_000L
        private const val MEDIUM_DECODED_PAGE_PIXELS = 8_000_000L
        private const val LOW_DECODED_PAGE_PIXELS = 4_000_000L
        private const val TURBO_DECODED_PAGE_PIXELS = 2_000_000L
        private const val MAX_DECODED_PAGE_WIDTH = 1_440
        private const val MEDIUM_DECODED_PAGE_WIDTH = 1_080
        private const val LOW_DECODED_PAGE_WIDTH = 720
        private const val TURBO_DECODED_PAGE_WIDTH = 480
        private const val MIN_DECODED_PAGE_WIDTH = 480
        private const val MAX_SOURCE_STRIP_HEIGHT = 2_048
        private const val MAX_SOURCE_STRIP_PIXELS = 4_000_000
        private const val MAX_STITCHED_SOURCE_PIXELS = 16_000_000L
        private const val MAX_REGION_SAMPLE_SIZE = 32
        private const val IMAGE_WARMUP_HOST_COUNT = 2
        private const val MIN_BITMAP_CACHE_KB = 16 * 1024
        private const val MAX_BITMAP_CACHE_KB = 64 * 1024
        private const val CHAPTER_LOCK_STRIPES = 8
        private const val CHAPTER_CACHE_LIMIT = 24
        private const val PAGE_ASPECT_RATIO_CACHE_LIMIT = 256
        private const val PAGE_CACHE_TRIM_INTERVAL = 16
        private const val MAX_DISK_PAGE_CACHE_BYTES = 384L * 1024L * 1024L
        private const val TARGET_DISK_PAGE_CACHE_BYTES = 320L * 1024L * 1024L
        private const val DOMAIN_DISCOVERY_COOLDOWN_MILLIS = 60_000L
        private const val IMAGE_HOST_COOLDOWN_MILLIS = 120_000L
        private const val IMAGE_HEDGE_DELAY_MILLIS = 100L
        private const val TURBO_IMAGE_HEDGE_DELAY_MILLIS = 45L
        private const val BACKGROUND_PRIORITY_POLL_MILLIS = 24L
        private const val DECODED_CACHE_WRITE_DELAY_MILLIS = 4_000L
        private const val UNOBSERVED_PAGE_LOAD_GRACE_MILLIS = 1_500L
        private const val SOURCE_LOAD_REUSE_MILLIS = 400L
        private const val SCRAMBLE_CACHE_MILLIS = 6L * 60L * 60L * 1_000L
        private const val OFFICIAL_SEARCH_PAGE_SIZE = 20
        private const val OFFICIAL_LIST_PAGE_SIZE = 20
        private const val SCRAMBLE_268850 = 268850L
        private const val SCRAMBLE_421926 = 421926L
        private val builtInDomains = listOf("www.cdnhjk.net", "www.cdngwc.cc", "www.cdngwc.net", "www.cdngwc.club", "www.cdnhjk.cc", "www.cdnutc.me")
        private val discoveryUrls = listOf("https://rup4a04-c01.tos-ap-southeast-1.bytepluses.com/newsvr-2025.txt", "https://rup4a04-c02.tos-cn-hongkong.bytepluses.com/newsvr-2025.txt", "https://rup4a04-c03.tos-cn-beijing.bytepluses.com.cn/newsvr-2025.txt")
        private val imageDomains = listOf("cdn-msp.jmapiproxy1.cc", "cdn-msp.jmapiproxy2.cc", "cdn-msp2.jmapiproxy2.cc", "cdn-msp3.jmapiproxy2.cc", "cdn-msp.jmapinodeudzn.net", "cdn-msp3.jmapinodeudzn.net")
        private val officialOrders = setOf("mr", "mv", "mv_m", "mv_w", "mv_t", "mp", "tf")
        private val officialSearchOrders = setOf("mr", "mv", "mp", "tf")
        private val safeImageFile = Regex("^[A-Za-z0-9_-]{1,128}\\.(?:jpg|jpeg|png|webp|gif)$", RegexOption.IGNORE_CASE)
        private val safeNumericId = Regex("^\\d{1,12}$")
        private val safeHost = Regex("^[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?$")
        private val ipLiteral = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$|:")
        private val scrambleRegex = Regex("var\\s+scramble_id\\s*=\\s*(\\d+);")
        private fun epochSeconds() = (System.currentTimeMillis() / 1000L).toString()
        private fun md5(value: String) = MessageDigest.getInstance("MD5").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        @SuppressLint("GetInstance")
        private fun protocolDecrypt(encoded: String, timestamp: String, secret: String): String {
            val encrypted = Base64.getMimeDecoder().decode(encoded)
            val key = md5(timestamp + secret).toByteArray()
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
            return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }
        internal fun decodedPageSize(
            width: Int,
            height: Int,
            maxPixels: Long = MAX_DECODED_PAGE_PIXELS,
            maxWidth: Int = MAX_DECODED_PAGE_WIDTH,
        ): Pair<Int, Int> {
            if (width <= 0 || height <= 0) return 1 to 1
            val safeMaxWidth = maxWidth.coerceIn(MIN_DECODED_PAGE_WIDTH, MAX_DECODED_PAGE_WIDTH)
            val widthScale = safeMaxWidth.toDouble() / width
            val safeMaxPixels = maxPixels.coerceIn(1L, MAX_DECODED_PAGE_PIXELS)
            val pixelScale = sqrt(safeMaxPixels.toDouble() / (width.toLong() * height.toLong()))
            val scale = minOf(1.0, widthScale, pixelScale)
            var targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
            var targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
            while (targetWidth.toLong() * targetHeight > safeMaxPixels) {
                if (targetWidth == 1) {
                    targetHeight = safeMaxPixels.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                } else if (targetHeight == 1) {
                    targetWidth = minOf(safeMaxWidth, safeMaxPixels.toInt())
                } else {
                    val correction = sqrt(
                        safeMaxPixels.toDouble() / (targetWidth.toLong() * targetHeight),
                    )
                    val correctedWidth = (targetWidth * correction).toInt().coerceAtLeast(1)
                    val correctedHeight = (targetHeight * correction).toInt().coerceAtLeast(1)
                    if (correctedWidth == targetWidth && correctedHeight == targetHeight) {
                        if (targetHeight >= targetWidth) targetHeight-- else targetWidth--
                    } else {
                        targetWidth = correctedWidth
                        targetHeight = correctedHeight
                    }
                }
            }
            return targetWidth to targetHeight
        }
        private fun stitchedRegionSampleSize(
            sourceWidth: Int,
            sourceHeight: Int,
            maxPixels: Long,
        ): Int {
            var sample = 1
            // A stitched intermediate is held alongside the final page. Keep it bounded to
            // roughly two final-page budgets, with an absolute cap for high-memory devices.
            val pixelBudget = minOf(MAX_STITCHED_SOURCE_PIXELS, maxPixels.coerceAtLeast(1L) * 2L)
            while (
                sampledDimension(sourceWidth, sample).toLong() * sampledDimension(sourceHeight, sample) > pixelBudget &&
                sample < MAX_REGION_SAMPLE_SIZE
            ) {
                sample *= 2
            }
            return sample
        }

        private fun sampledDimension(value: Int, sample: Int): Int =
            ((value.toLong() + sample - 1L) / sample.toLong()).toInt().coerceAtLeast(1)

        private fun sampledBoundary(value: Int, sourceExtent: Int, sampledExtent: Int): Int =
            (value.toLong() * sampledExtent / sourceExtent.toLong()).toInt()

        private fun sourceRangesAreSequential(sourceRanges: List<Pair<Int, Int>>, sourceHeight: Int): Boolean {
            var expectedTop = 0
            sourceRanges.forEach { range ->
                if (range.first != expectedTop || range.second <= range.first) return false
                expectedTop = range.second
            }
            return expectedTop == sourceHeight
        }

        internal fun scrambledPageSourceRanges(height: Int, segments: Int): List<Pair<Int, Int>> {
            if (height <= 0 || segments <= 0 || segments > height) return emptyList()
            val segmentHeight = height / segments
            val overflow = height % segments
            return List(segments) { index ->
                val sourceTop = height - segmentHeight * (index + 1) - overflow
                val sourceHeight = segmentHeight + if (index == 0) overflow else 0
                sourceTop to sourceTop + sourceHeight
            }
        }
        private fun ordinaryPageSourceRanges(width: Int, height: Int): List<Pair<Int, Int>> {
            if (width <= 0 || height <= 0) return emptyList()
            val stripHeight = minOf(
                MAX_SOURCE_STRIP_HEIGHT,
                (MAX_SOURCE_STRIP_PIXELS / width).coerceAtLeast(1),
            )
            return (0 until height step stripHeight).map { top ->
                top to minOf(height, top + stripHeight)
            }
        }
        private fun bitmapCacheSizeKb(context: Context): Int {
            val memoryMb = (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).memoryClass
            return (memoryMb * 1024 / 8).coerceIn(MIN_BITMAP_CACHE_KB, MAX_BITMAP_CACHE_KB)
        }
        private fun decodedPagePixelLimit(context: Context): Long {
            val memoryMb = (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).memoryClass
            return when {
                memoryMb < 384 -> 8_000_000L
                memoryMb < 512 -> 10_000_000L
                else -> MAX_DECODED_PAGE_PIXELS
            }
        }
        private fun imageWorkPermits(context: Context): Int {
            val memoryMb = (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).memoryClass
            return if (memoryMb < 384) 1 else 2
        }
        internal fun segmentationCount(scrambleId: String, aid: String, fileName: String): Int {
            val scramble = scrambleId.toLongOrNull() ?: return 0
            val numericAid = aid.toLongOrNull() ?: return 0
            if (numericAid < scramble) return 0
            if (numericAid < SCRAMBLE_268850) return 10
            val base = if (numericAid < SCRAMBLE_421926) 10 else 8
            val stem = fileName.substringBeforeLast('.', fileName)
            return (md5("$aid$stem").last().code % base) * 2 + 2
        }
        internal fun lockStripeIndex(key: String, stripeCount: Int): Int {
            require(stripeCount > 0)
            return (key.hashCode() and Int.MAX_VALUE) % stripeCount
        }
        internal fun hasMoreSearchResults(page: Int, pageSize: Int, loaded: Int, total: Long?, redirectAid: String?): Boolean =
            redirectAid == null && hasMorePagedResults(page, pageSize, loaded, total)
        internal fun hasMorePagedResults(page: Int, pageSize: Int, loaded: Int, total: Long?): Boolean {
            if (page !in 1 until MAX_OFFICIAL_PAGE || loaded <= 0) return false
            val safePageSize = pageSize.coerceAtLeast(1)
            return if (total != null) {
                ((page - 1L) * safePageSize + loaded) < total
            } else {
                // Some upstream responses omit total. A full page is the only
                // safe signal that another page may exist; a short page ends it.
                loaded >= safePageSize
            }
        }
        internal fun normalizedPagedTotal(page: Int, pageSize: Int, loaded: Int, reportedTotal: Long?): Long {
            val safePage = page.coerceIn(1, MAX_OFFICIAL_PAGE)
            val safePageSize = pageSize.coerceAtLeast(1)
            val safeLoaded = loaded.coerceAtLeast(0)
            val accumulated = (safePage - 1L) * safePageSize + safeLoaded
            return (reportedTotal ?: accumulated).coerceAtLeast(accumulated)
        }
        internal fun normalizeChapterImageFiles(values: List<String>): List<String> = values
            .asSequence()
            .map(String::trim)
            .filter(safeImageFile::matches)
            .distinctBy(String::lowercase)
            .take(MAX_CHAPTER_PAGE_ITEMS)
            .sortedWith(compareBy({ imageSequence(it) }, { it.lowercase() }))
            .toList()
        internal fun isUsableImageProbeStatus(code: Int): Boolean = code in 200..299
        internal fun normalizeRemoteDomain(raw: String?): String? {
            val url = (raw?.trim()?.takeIf(String::isNotEmpty) ?: return null)
                .let { if (it.contains("://")) it else "https://$it" }
                .toHttpUrlOrNull() ?: return null
            if (url.username.isNotEmpty() || url.password.isNotEmpty() ||
                url.query != null || url.fragment != null || url.encodedPath != "/"
            ) return null
            val host = url.host.lowercase()
            val numericHost = host.isNotEmpty() && host.all { it.isDigit() || it == '.' }
            val localName = host == "localhost" ||
                host.endsWith(".localhost") ||
                host == "local" ||
                host.endsWith(".local") ||
                host == "localdomain" ||
                host.endsWith(".localdomain") ||
                host == "0.0.0.0" ||
                host == "ip6-localhost"
            return host.takeIf {
                url.scheme == "https" &&
                    url.port == 443 &&
                    isValidDnsHost(it) &&
                    it.contains('.') &&
                    !ipLiteral.matches(it) &&
                    !numericHost &&
                    !localName
            }
        }
        private fun isValidDnsHost(host: String): Boolean {
            if (!safeHost.matches(host) || host.length > 253 || host.contains("..")) return false
            return host.split('.').all { label ->
                label.length in 1..63 &&
                    label.first().isLetterOrDigit() &&
                    label.last().isLetterOrDigit() &&
                    label.all { character -> character.isLetterOrDigit() || character == '-' }
            }
        }
        internal fun normalizeRemoteHttpsUrl(raw: String?): String? {
            val url = raw?.trim()?.takeIf(String::isNotBlank)?.toHttpUrlOrNull() ?: return null
            if (url.scheme != "https" || url.port != 443 ||
                url.username.isNotEmpty() || url.password.isNotEmpty()
            ) return null
            if (normalizeRemoteDomain(url.host) == null) return null
            return url.toString().takeIf { it.length <= MAX_FIELD_LENGTH }
        }
        private fun isAllowedImageUrl(raw: String): Boolean {
            val url = raw.trim().toHttpUrlOrNull() ?: return false
            return url.scheme == "https" && url.port == 443 &&
                url.username.isEmpty() && url.password.isEmpty() &&
                url.host in imageDomains && raw.length <= MAX_FIELD_LENGTH * 4
        }
        private fun imageSequence(fileName: String) = Regex("\\d+").find(fileName)?.value?.toLongOrNull() ?: Long.MAX_VALUE
        private fun formatChapterTitle(raw: String, sort: Int): String {
            val name = raw.trim().take(MAX_TITLE_LENGTH)
            if (name.isBlank()) return "第 $sort 话"
            if (name.matches(Regex("^\\d+(?:\\.\\d+)?$"))) return "第 $name 话"
            return (if (name.contains('第') || name.contains('话') || name.contains('話') || name.contains('章')) {
                name
            } else {
                "第 $sort 话 · $name"
            }).take(MAX_TITLE_LENGTH)
        }
        private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
        private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        private fun pageCacheKey(page: JmPage, profile: PageDecodeProfile) =
            sha256("v5|${profile.cacheToken}|${page.url}|${page.scrambleId}")
        private fun pageSourceCacheKey(page: JmPage) = sha256("v3|${page.url}|${page.scrambleId}")
        private val fallbackCategories = listOf(JmCategory("0", "最新A漫", "0"), JmCategory("1", "同人", "doujin"), JmCategory("2", "单本", "single"), JmCategory("3", "短篇", "short"), JmCategory("4", "韩漫", "hanman"))

        fun imageRequestHeaders(referer: String = "https://18comic.vip/"): Map<String, String> = mapOf(
            "Accept" to "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
            "X-Requested-With" to "com.JMComic3.app",
            "Referer" to referer,
            "User-Agent" to APP_USER_AGENT,
            "Accept-Language" to "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7",
        )
    }
}

class JmSourceException : IOException("JM 官方源暂时不可用")

private class BackgroundImageWorkPreempted : RuntimeException()

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

internal class MemoryCookieJar : CookieJar {
    private val values = ConcurrentHashMap<String, List<Cookie>>()

    fun clear() {
        values.clear()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        val boundedIncoming = cookies.filter { cookie ->
            cookie.name.length <= MAX_COOKIE_NAME_LENGTH &&
                cookie.value.length <= MAX_COOKIE_VALUE_LENGTH &&
                cookie.domain.length <= MAX_COOKIE_DOMAIN_LENGTH &&
                cookie.path.length <= MAX_COOKIE_PATH_LENGTH
        }
        values.compute(url.host) { _, existing ->
            val replacements = boundedIncoming.mapTo(HashSet()) { cookie -> cookie.identity() }
            (existing.orEmpty().filterNot { it.identity() in replacements } + boundedIncoming)
                .filter { it.expiresAt > now }
                .takeLast(MAX_COOKIES_PER_HOST)
        }
    }
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val current = values[url.host].orEmpty()
        val valid = current.filter { it.expiresAt > now }
        if (valid.size != current.size) values.replace(url.host, current, valid)
        return valid.filter { it.matches(url) }
    }
}

private const val MAX_COOKIES_PER_HOST = 64
private const val MAX_COOKIE_NAME_LENGTH = 128
private const val MAX_COOKIE_VALUE_LENGTH = 4 * 1024
private const val MAX_COOKIE_DOMAIN_LENGTH = 253
private const val MAX_COOKIE_PATH_LENGTH = 512

private fun Cookie.identity(): String = "$name|$domain|$path"

private fun JSONObject.string(key: String): String = optString(key).takeUnless { it == "null" }.orEmpty()
private fun JSONObject.int(key: String): Int? = parseJsonInt(opt(key))
private fun JSONObject.long(key: String): Long? = when (val value = opt(key)) {
    is Number -> parseCompactLong(value.toString())
    is String -> parseCompactLong(value)
    else -> null
}
private fun JSONObject.array(key: String): JSONArray = optJSONArray(key) ?: JSONArray()
private fun JSONObject.obj(key: String): JSONObject = optJSONObject(key) ?: JSONObject()
private fun JSONObject.stringList(key: String, limit: Int = 200): List<String> =
    array(key).objectsOrValues(limit).mapNotNull { it.primitiveContent() }
private fun JSONArray.objectsOrValues(limit: Int = MAX_JSON_ARRAY_ITEMS): List<Any?> = buildList(minOf(length(), limit.coerceAtLeast(0))) {
    for (index in 0 until minOf(length(), limit.coerceAtLeast(0))) add(opt(index))
}
private fun Any?.primitiveContent(): String? = this?.toString()?.takeUnless { it == "null" }

internal fun parseFavoritePage(
    payload: JSONObject,
    page: Int = 1,
    coverResolver: (String) -> String = { id -> "https://cover.invalid/$id.jpg" },
): JmFavoritePage {
    val root = payload.optJSONObject("data") ?: payload
    val safePage = page.coerceIn(1, MAX_OFFICIAL_PAGE)
    val items = firstJsonArray(root, "list", "content", "data", "albums", "favorites", "photos")
        ?.objectsOrValues(MAX_FAVORITE_SYNC_ITEMS)
        ?.mapNotNull { value ->
            val item = value as? JSONObject ?: return@mapNotNull null
            val id = firstJsonString(item, "id", "aid", "album_id")
                .take(MAX_ACCOUNT_FIELD_LENGTH)
                .takeIf { it.matches(Regex("\\d{1,12}")) }
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
        page = safePage,
        total = JmGateway.normalizedPagedTotal(safePage, OFFICIAL_FAVORITE_PAGE_SIZE, items.size, reportedTotal),
        items = items,
        hasMore = JmGateway.hasMorePagedResults(safePage, OFFICIAL_FAVORITE_PAGE_SIZE, items.size, reportedTotal),
    )
}

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
    return if (value.matches(Regex("^[A-Za-z0-9_-]{1,128}\\.(?:jpg|jpeg|png|webp)$", RegexOption.IGNORE_CASE))) {
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
        spoiler = firstJsonString(value, "spoiler", "is_spoiler") in setOf("1", "true", "TRUE", "yes"),
        replies = replies,
    )
}

private fun JmComment.withAvatarHost(host: String?): JmComment = copy(
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

private fun firstJsonArray(value: JSONObject, vararg keys: String): JSONArray? {
    keys.forEach { key ->
        value.optJSONArray(key)?.let { return it }
        val encoded = value.optString(key).trim()
        if (encoded.startsWith("[") && encoded.length <= MAX_EMBEDDED_JSON_ARRAY_CHARS) {
            runCatching { JSONArray(encoded) }.getOrNull()?.let { return it }
        }
    }
    return null
}

private fun firstJsonString(value: JSONObject, vararg keys: String): String =
    keys.asSequence()
        .map { value.string(it).trim() }
        .firstOrNull(String::isNotBlank)
        .orEmpty()

private fun firstJsonLong(value: JSONObject, vararg keys: String): Long? =
    keys.asSequence()
        .mapNotNull { value.long(it) }
        .firstOrNull()

internal fun parseJsonInt(value: Any?): Int? {
    val parsed = when (value) {
        is Number, is String -> value.toString().trim().toLongOrNull()
        else -> null
    } ?: return null
    return parsed.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
}

internal fun parseCompactLong(raw: String): Long? {
    val normalized = raw.replace(",", "").trim()
    normalized.toLongOrNull()?.let { return it }
    val match = Regex("^([0-9]+(?:\\.[0-9]+)?)([KMB万億亿]?)$", RegexOption.IGNORE_CASE)
        .matchEntire(normalized) ?: return null
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

