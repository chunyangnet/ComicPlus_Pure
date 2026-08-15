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

/** JM 官方只读适配器。 */
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
    private val pageLoadsInFlight = ConcurrentHashMap<String, Deferred<Bitmap>>()
    private val sourceLoadsInFlight = ConcurrentHashMap<String, Deferred<ByteArray>>()
    private val pageLoadWaiters = ConcurrentHashMap<String, AtomicInteger>()
    private val sourceLoadWaiters = ConcurrentHashMap<String, AtomicInteger>()
    private val pageProgressCallbacks = ConcurrentHashMap<String, (Long, Long) -> Unit>()
    private val pageAspectRatioCallbacks = ConcurrentHashMap<String, (Float) -> Unit>()
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
    private val scrambleMutex = Mutex()
    private val initializedCookieHosts = ConcurrentHashMap.newKeySet<String>()
    private val failedImageHosts = ConcurrentHashMap<String, Long>()
    private val warmedImageHosts = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var officialDomains: List<String> = initialOfficialDomains
    @Volatile private var sourceSnapshot: JmSourceSnapshot = initialSourceSnapshot
    @Volatile private var domains: List<String> = initialSourceSnapshot.endpoints.map(JmSourceEndpoint::host)
        .ifEmpty { initialOfficialDomains }
    @Volatile private var lastDomainDiscoveryAt = 0L
    @Volatile private var cachedScrambleId: String? = null
    @Volatile private var cachedScrambleAt = 0L
    @Volatile private var preferredImageHost: String? = initialSourceSnapshot.selectedImageHost
        ?.takeIf(safeHost::matches)
    @Volatile private var autoSelectSource = true
    @Volatile private var preferredSourceHost: String? = null

    fun setSourcePreferences(
        autoSelect: Boolean,
        preferredHost: String? = null,
        preferredImageHost: String? = null,
    ): JmSourceSnapshot {
        val previousImageHost = sourceSnapshot.selectedImageHost
        autoSelectSource = autoSelect
        preferredSourceHost = preferredHost?.takeIf(safeHost::matches)
        this.preferredImageHost = preferredImageHost?.takeIf { host -> imageDomains.any { it == host } }
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
        return sourceSnapshot
    }

    fun cachedSourceSnapshot(): JmSourceSnapshot = sourceSnapshot

    fun warmImageConnections(comicId: String, chapterId: String) {
        if (!comicId.matches(safeNumericId) || !chapterId.matches(safeNumericId)) return
        buildList {
            sourceSnapshot.selectedImageHost?.let(::add)
            preferredImageHost?.let(::add)
            addAll(rotatedImageDomains(chapterId))
        }.distinct().take(IMAGE_WARMUP_HOST_COUNT).forEach { host ->
            if (!warmedImageHosts.add(host)) return@forEach
            cacheScope.launch {
                try {
                    val request = Request.Builder()
                        .url("https://$host/media/albums/${comicId}_3x4.jpg")
                        .header("Referer", "https://${domains.firstOrNull() ?: builtInDomains.first()}/")
                        .header("X-Requested-With", "com.JMComic3.app")
                        .header("User-Agent", APP_USER_AGENT)
                        .head()
                        .build()
                    runInterruptible(Dispatchers.IO) {
                        imageWarmupClient.newCall(request).execute().close()
                    }
                } catch (_: Exception) {
                    warmedImageHosts.remove(host)
                }
            }
        }
    }

    suspend fun refreshSourceList(
        force: Boolean = false,
        updateOfficialList: Boolean = true,
    ): JmSourceSnapshot {
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
        val ordered = orderSourceEndpoints(probed, autoSelectSource, preferredSourceOrder(candidates))
        val imageProbed = coroutineScope {
            val limiter = Semaphore(MAX_SOURCE_PROBE_CONCURRENCY)
            imageDomains.map { domain ->
                async {
                    limiter.withPermit { JmSourceEndpoint(domain, probeImageLatency(domain)) }
                }
            }.awaitAll()
        }
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
        val snapshot = JmSourceSnapshot(
            endpoints = ordered,
            selectedHost = domains.firstOrNull(),
            updatedAt = now,
            imageEndpoints = orderedImages,
            selectedImageHost = selectedImage,
            imageUpdatedAt = now,
        )
        sourceSnapshot = snapshot
        saveSourceState(snapshot)
        return snapshot
    }

    suspend fun home(): List<JmRanking> = withContext(Dispatchers.IO) {
        (category("0", "mv_t") + category("0", "mr")).distinctBy(JmRanking::id).take(40)
    }

    suspend fun category(slug: String, order: String = "mr", page: Int = 1): List<JmRanking> =
        requestJson("/categories/filter?page=${page.coerceIn(1, 200)}&order=&c=${encode(slug.ifBlank { "0" })}&o=${order.allowedOrder()}")
            .rankingItems("JM 分类")

    suspend fun categories(): List<JmCategory> = try {
        requestJson("/categories").array("categories").objectsOrValues().mapNotNull { item ->
            val obj = item as? JSONObject ?: return@mapNotNull null
            val name = obj.string("name").trim()
            val slug = obj.string("slug").trim().ifBlank { "0" }
            if (name.isBlank()) null else JmCategory(obj.string("id").ifBlank { slug }, name, slug, obj.long("total_albums"))
        }.distinctBy(JmCategory::slug).ifEmpty { fallbackCategories }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        fallbackCategories
    }

    suspend fun discoverDomains(force: Boolean = false): List<String> {
        val now = System.currentTimeMillis()
        if (!force && now - lastDomainDiscoveryAt < DOMAIN_DISCOVERY_COOLDOWN_MILLIS) return officialDomains
        return domainDiscoveryMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (!force && lockedNow - lastDomainDiscoveryAt < DOMAIN_DISCOVERY_COOLDOWN_MILLIS) return@withLock officialDomains
            try {
                for (url in discoveryUrls.shuffled()) {
                    try {
                        val encoded = runInterruptible(Dispatchers.IO) {
                            discoveryClient.newCall(Request.Builder().url(url).get().build()).execute()
                        }.use { response ->
                        if (!response.isSuccessful) return@use null
                        response.body?.readStringLimited(MAX_API_RESPONSE_BYTES)?.dropWhile { it.code > 127 }?.trim()
                    } ?: continue
                        val decoded = protocolDecrypt(encoded, "", DOMAIN_SERVER_PROTOCOL_KEY)
                        val discovered = JSONObject(decoded).array("Server").objectsOrValues()
                            .mapNotNull { normalizeDomain(it.primitiveContent()) }
                            .distinct()
                        if (discovered.isNotEmpty()) {
                            officialDomains = (discovered + builtInDomains)
                                .distinct()
                                .take(MAX_SOURCE_PROBE_CANDIDATES)
                            domains = preferredSourceOrder(
                                (domains + officialDomains).distinct().take(MAX_SOURCE_PROBE_CANDIDATES),
                            )
                            saveOfficialDomains()
                            return@withLock officialDomains
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
        val payload = requestJson("/search?main_tag=${mainTag.coerceIn(0, 4)}&search_query=${encode(normalized)}&page=${page.coerceIn(1, 200)}&o=${order.allowedSearchOrder()}&t=a")
        val items = payload.rankingItems("JM 搜索", preferResultImage = true)
        val total = payload.long("total") ?: items.size.toLong()
        val size = payload.long("count")?.toInt()?.takeIf { it > 0 }
            ?: items.size.takeIf { it > 0 }
            ?: OFFICIAL_SEARCH_PAGE_SIZE
        val redirectAid = payload.string("redirect_aid").takeIf { it.matches(Regex("\\d{1,12}")) }
        return JmSearchPage(
            query = payload.string("search_query").ifBlank { normalized },
            page = page,
            total = total.coerceAtLeast(items.size.toLong()),
            redirectAid = redirectAid,
            items = items,
            hasMore = hasMoreSearchResults(page, size, items.size, total, redirectAid),
        )
    }

    suspend fun comic(id: String): JmComic {
        require(id.matches(Regex("\\d{1,12}"))) { "JM 编号无效" }
        val data = requestJson("/album?id=$id")
        val actualId = data.string("id").takeIf(safeNumericId::matches) ?: id
        val series = data.array("series").objectsOrValues().mapIndexedNotNull { index, element ->
            val item = element as? JSONObject ?: return@mapIndexedNotNull null
            val chapterId = item.string("id").takeIf { it.matches(Regex("\\d{1,12}")) } ?: return@mapIndexedNotNull null
            val sort = item.int("sort")?.takeIf { it > 0 } ?: index + 1
            JmChapter(chapterId, sort, formatChapterTitle(item.string("name"), sort))
        }.sortedBy(JmChapter::index).distinctBy(JmChapter::index).ifEmpty { listOf(JmChapter(actualId, 1, "第 1 话")) }
        return JmComic(actualId, data.string("name").ifBlank { "JM$actualId" }, data.string("description"), coverUrl(actualId), data.stringList("author"), data.stringList("tags"), series, data.long("total_views"), data.long("likes"))
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
                    val title = data.string("name").ifBlank { "第 1 话" }
                    val serverScramble = data.string("scramble_id").takeIf(safeNumericId::matches)
                    val scramble = when {
                        serverScramble != null -> serverScramble.also { speculativeScramble?.cancel() }
                        photoId.toLongOrNull()?.let { it < DEFAULT_SCRAMBLE_ID.toLong() } == true ->
                            DEFAULT_SCRAMBLE_ID.also { speculativeScramble?.cancel() }
                        photoId == id -> speculativeScramble?.await() ?: fetchScramble(photoId)
                        else -> fetchScramble(photoId).also { speculativeScramble?.cancel() }
                    }
                    val hosts = rotatedImageDomains(photoId)
                    val files = data.array("images").objectsOrValues().mapNotNull { it.primitiveContent()?.trim()?.takeIf(safeImageFile::matches) }.distinctBy(String::lowercase).sortedWith(compareBy({ imageSequence(it) }, { it.lowercase() })).distinctBy(::imageSequence)
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
    ): Bitmap {
        val key = pageCacheKey(page, profile)
        val sourceKey = pageSourceCacheKey(page)
        if (onProgress != null) pageProgressCallbacks[key] = onProgress
        if (onAspectRatio != null) {
            pageAspectRatioCallbacks[key] = onAspectRatio
            pageAspectRatioCache[key]?.let { ratio ->
                mainHandler.post { onAspectRatio(ratio) }
            }
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
                    mainHandler.post { callback(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)) }
                }
                return bitmap
            }
            val created = cacheScope.async(start = CoroutineStart.LAZY) {
                performPageLoad(page, key, sourceKey, profile, hedgeImageHosts)
            }
            val existing = pageLoadsInFlight.putIfAbsent(key, created)
            val selected = existing ?: created.also { deferred ->
                deferred.invokeOnCompletion { pageLoadsInFlight.remove(key, deferred) }
                deferred.start()
            }
            if (existing != null) created.cancel()
            return selected.await()
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
    ): Bitmap =
        withContext(Dispatchers.IO) {
            bitmapCache.get(key)?.takeUnless(Bitmap::isRecycled)?.let { return@withContext it }
            val decodedCacheFile = File(cacheDir, "$key.webp")
            val rawCacheFile = File(cacheDir, "$sourceKey.source")

            page.localPath?.let { localPath ->
                return@withContext withImageDecodeTurn(key) {
                    decodeFilePage(localPath, key, profile) ?: throw JmSourceException()
                }.also { bitmapCache.put(key, it) }
            }
            decodeFilePageWithTurn(decodedCacheFile, key, profile)?.let { bitmap ->
                decodedCacheFile.setLastModified(System.currentTimeMillis())
                bitmapCache.put(key, bitmap)
                return@withContext bitmap
            }
            decodeRawPageFileWithTurn(rawCacheFile, page, key, profile)?.let { bitmap ->
                rawCacheFile.setLastModified(System.currentTimeMillis())
                bitmapCache.put(key, bitmap)
                if (!profile.turboMode) {
                    scheduleDecodedPageCacheWrite(key, decodedCacheFile, bitmap, profile.cacheWebpQuality)
                }
                return@withContext bitmap
            }

            val bytes = requestSourceBytes(
                page = page,
                sourceKey = sourceKey,
                onProgress = { done, total -> pageProgressCallbacks[key]?.invoke(done, total) },
                hedgeImageHosts = hedgeImageHosts,
                hedgeDelayMillis = profile.hedgeDelayMillis,
            )
            val decoded = withImageDecodeTurn(key) { decodePage(bytes, page, key, profile) }
            bitmapCache.put(key, decoded)
            if (!profile.turboMode) {
                scheduleDecodedPageCacheWrite(
                    key,
                    decodedCacheFile,
                    decoded,
                    profile.cacheWebpQuality,
                )
            }
            decoded
        }

    private suspend fun requestSourceBytes(
        page: JmPage,
        sourceKey: String,
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
                ).also { bytes ->
                    scheduleRawPageCacheWrite(sourceKey, File(cacheDir, "$sourceKey.source"), bytes)
                }
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
            return selected.await()
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
        if (isValidDecodedPageFile(target)) return@withContext
        target.delete()
        val profile = pageDecodeProfile(ReaderImageQuality.High, turboMode = false)
        val decodedKey = pageCacheKey(page, profile)
        val sourceKey = pageSourceCacheKey(page)
        val decodedCacheFile = File(cacheDir, "$decodedKey.webp")
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
        )
        val generation = pageCacheGeneration.get()
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
        val selected = sourceSnapshot.selectedImageHost
            ?.takeIf { host -> imageDomains.any { it == host } }
        val host = selected ?: imageDomains[lockStripeIndex(id, imageDomains.size)]
        return "https://$host/media/albums/${id}_3x4.jpg"
    }

    suspend fun clearPageCache(): Long = withContext(Dispatchers.IO) {
        pageCacheGeneration.incrementAndGet()
        bitmapCache.evictAll()
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

    private fun scheduleRawPageCacheWrite(key: String, file: File, bytes: ByteArray) {
        val generation = pageCacheGeneration.get()
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
    ) {
        val generation = pageCacheGeneration.get()
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

    private fun isPageVisible(key: String): Boolean = (visiblePageWaiters[key]?.get() ?: 0) > 0
    private fun isPageHedgeRequested(key: String): Boolean = (hedgePageWaiters[key]?.get() ?: 0) > 0
    private fun isPageTurbo(key: String): Boolean = (turboPageWaiters[key]?.get() ?: 0) > 0

    private suspend fun awaitVisiblePageIdle() {
        while (visiblePageRequestCount.get() > 0) delay(BACKGROUND_PRIORITY_POLL_MILLIS)
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
    ): Bitmap? {
        if (!file.isFile) return null
        return withImageDecodeTurn(key) { decodeFilePage(file.absolutePath, key, profile) }
    }

    private suspend fun decodeRawPageFileWithTurn(
        file: File,
        page: JmPage,
        key: String,
        profile: PageDecodeProfile,
    ): Bitmap? {
        if (!file.isFile) return null
        return withImageDecodeTurn(key) { decodeRawPageFile(file, page, key, profile) }
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

    private suspend fun openImageAttempt(url: String, page: JmPage): Result<OpenedImage> = try {
        val request = imageRequest(url, page)
        val response = runInterruptible(Dispatchers.IO) { client.newCall(request).execute() }
        if (!response.isSuccessful) {
            response.close()
            throw JmSourceException()
        }
        val total = response.body?.contentLength() ?: throw JmSourceException().also { response.close() }
        if (total > MAX_PAGE_BYTES) {
            response.close()
            throw JmSourceException()
        }
        Result.success(OpenedImage(url, response))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        url.toHttpUrlOrNull()?.host?.let { failedImageHosts[it] = System.currentTimeMillis() }
        Result.failure(error)
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
        return runInterruptible(Dispatchers.IO) { client.newCall(imageRequest(url, page)).execute() }.use { response ->
            if (!response.isSuccessful) throw JmSourceException()
            readImageBody(response.body ?: throw JmSourceException(), key, onProgress)
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
        FetchedImage(opened.url, readImageBody(response.body ?: throw JmSourceException(), key, onProgress))
    }

    private suspend fun readImageBody(
        body: ResponseBody,
        key: String,
        onProgress: (Long, Long) -> Unit,
    ): ByteArray {
        val total = body.contentLength()
        if (total > MAX_PAGE_BYTES) throw JmSourceException()
        return withContext(Dispatchers.IO) {
            val sink = java.io.ByteArrayOutputStream(if (total > 0) total.toInt().coerceAtMost(MAX_PAGE_BYTES) else 16 * 1024)
            body.byteStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                var done = 0L
                while (true) {
                    while (visiblePageRequestCount.get() > 0 && !isPageVisible(key)) {
                        delay(BACKGROUND_PRIORITY_POLL_MILLIS)
                    }
                    val read = runInterruptible(Dispatchers.IO) { input.read(buffer) }
                    if (read < 0) break
                    done += read
                    if (done > MAX_PAGE_BYTES) throw JmSourceException()
                    sink.write(buffer, 0, read)
                    onProgress(done, total)
                }
            }
            sink.toByteArray()
        }
    }

    private fun recordImageSuccess(url: String) {
        url.toHttpUrlOrNull()?.host?.let { host ->
            failedImageHosts.remove(host)
            if (autoSelectSource && preferredImageHost != host) {
                preferredImageHost = host
                sourceSnapshot = sourceSnapshot.copy(selectedImageHost = host)
                sourcePreferences.edit { putString(PREFERRED_IMAGE_HOST_KEY, host) }
            }
        }
    }

    private fun recordPageAspectRatio(key: String, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val ratio = (width.toFloat() / height.toFloat()).coerceIn(0.05f, 8f)
        pageAspectRatioCache[key] = ratio
        pageAspectRatioCallbacks[key]?.let { callback ->
            mainHandler.post { callback(ratio) }
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
    ): Bitmap {
        if (bytes.isEmpty() || bytes.size > MAX_PAGE_BYTES) throw JmSourceException()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw JmSourceException()
        if (bounds.outWidth.toLong() * bounds.outHeight.toLong() > MAX_PAGE_PIXELS) throw JmSourceException()
        recordPageAspectRatio(key, bounds.outWidth, bounds.outHeight)
        val segments = segmentationCount(page.scrambleId, page.photoId, page.fileName)
        if (segments > bounds.outHeight) throw JmSourceException()
        val decoder = BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false) ?: throw JmSourceException()
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
        val regionSampleSize = regionSampleSize(sourceWidth, target.first)
        val decoded = createBitmap(target.first, target.second, Bitmap.Config.RGB_565)
        try {
            val canvas = Canvas(decoded)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            var destinationSourceTop = 0
            sourceRanges.forEach { sourceRange ->
                if (shouldYield()) throw BackgroundImageWorkPreempted()
                val rangeHeight = sourceRange.second - sourceRange.first
                val strip = decoder.decodeRegion(
                    Rect(0, sourceRange.first, sourceWidth, sourceRange.second),
                    BitmapFactory.Options().apply {
                        inSampleSize = regionSampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565
                    },
                ) ?: throw JmSourceException()
                try {
                    val destinationTop = (destinationSourceTop.toLong() * decoded.height / sourceHeight).toInt()
                    val destinationBottom = minOf(
                        decoded.height,
                        ((destinationSourceTop + rangeHeight).toLong() * decoded.height / sourceHeight)
                            .toInt().coerceAtLeast(destinationTop + 1),
                    )
                    canvas.drawBitmap(
                        strip,
                        Rect(0, 0, strip.width, strip.height),
                        Rect(0, destinationTop, decoded.width, destinationBottom),
                        paint,
                    )
                    destinationSourceTop += rangeHeight
                } finally {
                    strip.recycle()
                }
                if (shouldYield()) throw BackgroundImageWorkPreempted()
            }
        } catch (error: Throwable) {
            decoded.recycle()
            throw error
        } finally {
            decoder.recycle()
        }
        return decoded
    }

    @Suppress("DEPRECATION")
    private fun decodeFilePage(path: String, key: String, profile: PageDecodeProfile): Bitmap? {
        val file = File(path)
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_PAGE_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (bounds.outWidth.toLong() * bounds.outHeight.toLong() > MAX_PAGE_PIXELS) return null
        recordPageAspectRatio(key, bounds.outWidth, bounds.outHeight)
        val decoder = BitmapRegionDecoder.newInstance(path, false) ?: return null
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
    ): Bitmap? {
        if (!file.isFile || file.length() !in 1..MAX_PAGE_BYTES.toLong()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw JmSourceException()
            if (bounds.outWidth.toLong() * bounds.outHeight.toLong() > MAX_PAGE_PIXELS) throw JmSourceException()
            recordPageAspectRatio(key, bounds.outWidth, bounds.outHeight)
            val segments = segmentationCount(page.scrambleId, page.photoId, page.fileName)
            if (segments > bounds.outHeight) throw JmSourceException()
            val sourceRanges = if (segments == 0) {
                ordinaryPageSourceRanges(bounds.outWidth, bounds.outHeight)
            } else {
                scrambledPageSourceRanges(bounds.outHeight, segments)
            }
            val decoder = BitmapRegionDecoder.newInstance(file.absolutePath, false) ?: throw JmSourceException()
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
        cachedScrambleId?.takeIf { now - cachedScrambleAt < SCRAMBLE_CACHE_MILLIS }?.let { return it }
        return scrambleMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            cachedScrambleId?.takeIf { lockedNow - cachedScrambleAt < SCRAMBLE_CACHE_MILLIS }?.let {
                return@withLock it
            }
            fetchScrambleFresh(photoId).also { scramble ->
                cachedScrambleId = scramble
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
                    if (response.isSuccessful) scrambleRegex.find(response.body?.readStringLimited(MAX_API_RESPONSE_BYTES).orEmpty())?.groupValues?.getOrNull(1)?.let { return@withContext it }
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
                    if (response.isSuccessful) scrambleRegex.find(response.body?.readStringLimited(MAX_API_RESPONSE_BYTES).orEmpty())?.groupValues?.getOrNull(1)?.let { return@withContext it }
                }
            }.onFailure { if (it is CancellationException) throw it }
        }
        DEFAULT_SCRAMBLE_ID
    }

    private suspend fun requestJson(path: String): JSONObject = withContext(Dispatchers.IO) {
        val attempted = domains.take(MAX_API_CANDIDATES)
        requestAcrossDomains(path, attempted)?.let { return@withContext it }
        val refreshed = discoverDomains()
        val attemptedSet = attempted.toHashSet()
        requestAcrossDomains(path, refreshed.filterNot(attemptedSet::contains))?.let { return@withContext it }
        throw JmSourceException()
    }

    private suspend fun requestAcrossDomains(path: String, candidates: List<String>): JSONObject? {
        val domains = candidates.take(MAX_API_CANDIDATES)
        if (!autoSelectSource && preferredSourceHost != null) {
            val manuallyOrdered = buildList {
                if (preferredSourceHost in domains) add(preferredSourceHost!!)
                addAll(domains.filterNot { it == preferredSourceHost })
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

    private suspend fun requestDomainJson(path: String, domain: String): Result<JSONObject> = try {
        ensureCookies(domain)
        val timestamp = epochSeconds()
        val result = execute(domain, path, timestamp, APP_TOKEN_PROTOCOL_KEY).use { response ->
            if (!response.isSuccessful) throw JmSourceException()
            val envelope = JSONObject(response.body?.readStringLimited(MAX_API_RESPONSE_BYTES).orEmpty())
            if (envelope.int("code") != 200) throw JmSourceException()
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
        if (domain in initializedCookieHosts) return
        cookieMutex.withLock {
            if (domain in initializedCookieHosts) return@withLock
            val initialized = runCatching {
                val timestamp = epochSeconds()
                execute(domain, "/setting", timestamp, APP_TOKEN_PROTOCOL_KEY).use { response ->
                    if (!response.isSuccessful) return@use false
                    response.body?.readStringLimited(MAX_API_RESPONSE_BYTES)
                    true
                }
            }.onFailure { if (it is CancellationException) throw it }.getOrDefault(false)
            if (initialized) initializedCookieHosts += domain
        }
    }

    private fun normalizeDomain(raw: String?): String? {
        val url = (raw?.trim()?.takeIf(String::isNotEmpty) ?: return null).let { if (it.contains("://")) it else "https://$it" }.toHttpUrlOrNull() ?: return null
        val host = url.host.lowercase()
        return host.takeIf {
            url.scheme == "https" &&
                url.port == 443 &&
                safeHost.matches(it) &&
                !ipLiteral.matches(it) &&
                !it.endsWith(".localhost")
        }
    }

    private fun loadOfficialDomains(): List<String> = sourcePreferences
        .getString(SOURCE_OFFICIAL_DOMAINS_KEY, null)
        .orEmpty()
        .lineSequence()
        .mapNotNull(::normalizeDomain)
        .distinct()
        .take(MAX_SOURCE_PROBE_CANDIDATES)
        .toList()
        .ifEmpty { builtInDomains }

    private fun loadSourceSnapshot(fallbackDomains: List<String>): JmSourceSnapshot {
        val endpoints = sourcePreferences.getString(SOURCE_ENDPOINTS_KEY, null)
            .orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split('|', limit = 2)
                val host = normalizeDomain(parts.getOrNull(0)) ?: return@mapNotNull null
                val latency = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it >= 0L }
                JmSourceEndpoint(host, latency)
            }
            .distinctBy(JmSourceEndpoint::host)
            .take(MAX_SOURCE_PROBE_CANDIDATES)
            .toList()
        val allowedHosts = fallbackDomains.toHashSet()
        val resolved = (endpoints.filter { it.host in allowedHosts } + fallbackDomains.map { JmSourceEndpoint(it, null) })
            .distinctBy(JmSourceEndpoint::host)
            .take(MAX_SOURCE_PROBE_CANDIDATES)
        val imageEndpoints = sourcePreferences.getString(SOURCE_IMAGE_ENDPOINTS_KEY, null)
            .orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split('|', limit = 2)
                val host = normalizeDomain(parts.getOrNull(0)) ?: return@mapNotNull null
                if (host !in imageDomains) return@mapNotNull null
                val latency = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it >= 0L }
                JmSourceEndpoint(host, latency)
            }
            .distinctBy(JmSourceEndpoint::host)
            .toList()
        val resolvedImages = (imageEndpoints + imageDomains.map { JmSourceEndpoint(it, null) })
            .distinctBy(JmSourceEndpoint::host)
        val savedImageHost = sourcePreferences.getString(PREFERRED_IMAGE_HOST_KEY, null)
            ?.takeIf { it in imageDomains }
        return JmSourceSnapshot(
            endpoints = resolved,
            selectedHost = resolved.firstOrNull()?.host,
            updatedAt = sourcePreferences.getLong(SOURCE_UPDATED_AT_KEY, 0L).coerceAtLeast(0L),
            imageEndpoints = resolvedImages,
            selectedImageHost = savedImageHost ?: resolvedImages.firstOrNull()?.host,
            imageUpdatedAt = sourcePreferences.getLong(SOURCE_IMAGE_UPDATED_AT_KEY, 0L).coerceAtLeast(0L),
        )
    }

    private fun saveOfficialDomains() {
        sourcePreferences.edit { putString(SOURCE_OFFICIAL_DOMAINS_KEY, officialDomains.joinToString("\n")) }
    }

    private fun saveSourceState(snapshot: JmSourceSnapshot) {
        sourcePreferences.edit {
            putString(
                SOURCE_ENDPOINTS_KEY,
                snapshot.endpoints.joinToString("\n") { endpoint ->
                    "${endpoint.host}|${endpoint.latencyMs ?: -1L}"
                },
            )
            putLong(SOURCE_UPDATED_AT_KEY, snapshot.updatedAt)
            putString(
                SOURCE_IMAGE_ENDPOINTS_KEY,
                snapshot.imageEndpoints.joinToString("\n") { endpoint ->
                    "${endpoint.host}|${endpoint.latencyMs ?: -1L}"
                },
            )
            putString(PREFERRED_IMAGE_HOST_KEY, snapshot.selectedImageHost)
            putLong(SOURCE_IMAGE_UPDATED_AT_KEY, snapshot.imageUpdatedAt)
        }
    }

    private fun rotatedImageDomains(seed: String): List<String> {
        val offset = lockStripeIndex(seed, imageDomains.size)
        return imageDomains.drop(offset) + imageDomains.take(offset)
    }

    private fun orderedImageUrls(page: JmPage): List<String> {
        val now = System.currentTimeMillis()
        val preferredHost = sourceSnapshot.selectedImageHost ?: preferredImageHost
        return (listOf(page.url) + page.alternativeUrls).distinct().sortedWith(
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

    private suspend fun execute(domain: String, path: String, timestamp: String, secret: String) =
        runInterruptible(Dispatchers.IO) {
            client.newCall(Request.Builder().url("https://$domain$path").apply { headers(timestamp, secret).forEach { (k, v) -> header(k, v) } }.get().build()).execute()
        }

    private suspend fun probeSourceLatency(domain: String): Long? = try {
        val startedAt = SystemClock.elapsedRealtime()
        val timestamp = epochSeconds()
        runInterruptible(Dispatchers.IO) {
            sourceProbeClient.newCall(
                Request.Builder()
                    .url("https://$domain/setting")
                    .apply { headers(timestamp, APP_TOKEN_PROTOCOL_KEY).forEach { (key, value) -> header(key, value) } }
                    .get()
                    .build(),
            ).execute()
        }.use { response ->
            if (!response.isSuccessful) return null
        }
        (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1L)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun probeImageLatency(domain: String): Long? = try {
        val startedAt = SystemClock.elapsedRealtime()
        runInterruptible(Dispatchers.IO) {
            sourceProbeClient.newCall(
                Request.Builder()
                    .url("https://$domain$IMAGE_PROBE_PATH")
                    .header("Referer", "https://${domains.firstOrNull() ?: builtInDomains.first()}/")
                    .header("X-Requested-With", "com.JMComic3.app")
                    .header("User-Agent", APP_USER_AGENT)
                    .head()
                    .build(),
            ).execute()
        }.use { response ->
            if (response.code >= 500) return null
        }
        (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1L)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun headers(timestamp: String, secret: String): Map<String, String> = mapOf("Accept" to "application/json", "Accept-Language" to "zh-CN,zh;q=0.9", "User-Agent" to APP_USER_AGENT, "token" to md5(timestamp + secret), "tokenparam" to "$timestamp,$APP_VERSION")

    private fun JSONObject.rankingItems(badge: String, preferResultImage: Boolean = false): List<JmRanking> = array("content").objectsOrValues().mapIndexedNotNull { index, element ->
        val item = element as? JSONObject ?: return@mapIndexedNotNull null
        val id = item.string("id").takeIf { it.matches(Regex("\\d{1,12}")) } ?: return@mapIndexedNotNull null
        val title = item.string("name").ifBlank { return@mapIndexedNotNull null }
        JmRanking(id, title, item.string("image").takeIf { preferResultImage && it.startsWith("https://") } ?: coverUrl(id), item.long("total_views"), item.long("likes"), if (badge == "JM 热门") "$badge ${index + 1}" else badge, item.obj("category").string("title"))
    }

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
        private const val IMAGE_PROBE_PATH = "/media/albums/220980_3x4.jpg"
        private const val MAX_API_RESPONSE_BYTES = 2 * 1024 * 1024
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
        private const val SCRAMBLE_268850 = 268850L
        private const val SCRAMBLE_421926 = 421926L
        private val builtInDomains = listOf("www.cdnhjk.net", "www.cdngwc.cc", "www.cdngwc.net", "www.cdngwc.club", "www.cdnhjk.cc", "www.cdnutc.me")
        private val discoveryUrls = listOf("https://rup4a04-c01.tos-ap-southeast-1.bytepluses.com/newsvr-2025.txt", "https://rup4a04-c02.tos-cn-hongkong.bytepluses.com/newsvr-2025.txt", "https://rup4a04-c03.tos-cn-beijing.bytepluses.com.cn/newsvr-2025.txt")
        private val imageDomains = listOf("cdn-msp.jmapiproxy1.cc", "cdn-msp.jmapiproxy2.cc", "cdn-msp2.jmapiproxy2.cc", "cdn-msp3.jmapiproxy2.cc", "cdn-msp.jmapinodeudzn.net", "cdn-msp3.jmapinodeudzn.net")
        private val officialOrders = setOf("mr", "mv", "mv_m", "mv_w", "mv_t", "mp", "tf")
        private val officialSearchOrders = setOf("mr", "mv", "mp", "tf")
        private val safeImageFile = Regex("^[A-Za-z0-9_-]+\\.(?:jpg|jpeg|png|webp|gif)$", RegexOption.IGNORE_CASE)
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
        private fun regionSampleSize(sourceWidth: Int, targetWidth: Int): Int {
            var sample = 1
            while (sourceWidth / (sample * 2) >= targetWidth) {
                sample *= 2
            }
            return sample
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
        internal fun hasMoreSearchResults(page: Int, pageSize: Int, loaded: Int, total: Long, redirectAid: String?): Boolean =
            redirectAid == null && loaded > 0 && ((page.coerceAtLeast(1) - 1L) * pageSize.coerceAtLeast(1) + loaded) < total
        private fun imageSequence(fileName: String) = Regex("\\d+").find(fileName)?.value?.toLongOrNull() ?: Long.MAX_VALUE
        private fun formatChapterTitle(raw: String, sort: Int): String { val name = raw.trim(); if (name.isBlank()) return "第 $sort 话"; if (name.matches(Regex("^\\d+(?:\\.\\d+)?$"))) return "第 $name 话"; return if (name.contains('第') || name.contains('话') || name.contains('話') || name.contains('章')) name else "第 $sort 话 · $name" }
        private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
        private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        private fun pageCacheKey(page: JmPage, profile: PageDecodeProfile) =
            sha256("v4|${profile.cacheToken}|${page.url}|${page.scrambleId}")
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

private class MemoryCookieJar : CookieJar {
    private val values = ConcurrentHashMap<String, List<Cookie>>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        values.compute(url.host) { _, existing ->
            val replacements = cookies.mapTo(HashSet()) { cookie -> cookie.identity() }
            (existing.orEmpty().filterNot { it.identity() in replacements } + cookies)
                .filter { it.expiresAt > System.currentTimeMillis() }
        }
    }
    override fun loadForRequest(url: HttpUrl): List<Cookie> = values[url.host].orEmpty().filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
}

private fun Cookie.identity(): String = "$name|$domain|$path"

private suspend fun ResponseBody.readStringLimited(maxBytes: Int): String = runInterruptible(Dispatchers.IO) {
    val declared = contentLength()
    if (declared > maxBytes) throw JmSourceException()
    val output = java.io.ByteArrayOutputStream(if (declared > 0) declared.toInt() else 8 * 1024)
    byteStream().use { input ->
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw JmSourceException()
            output.write(buffer, 0, read)
        }
    }
    output.toString(Charsets.UTF_8.name())
}

private fun JSONObject.string(key: String): String = optString(key).takeUnless { it == "null" }.orEmpty()
private fun JSONObject.int(key: String): Int? = string(key).toIntOrNull() ?: optLong(key).takeIf { has(key) }?.toInt()
private fun JSONObject.long(key: String): Long? = when (val value = opt(key)) {
    is Number -> value.toLong()
    is String -> parseCompactLong(value)
    else -> null
}
private fun JSONObject.array(key: String): JSONArray = optJSONArray(key) ?: JSONArray()
private fun JSONObject.obj(key: String): JSONObject = optJSONObject(key) ?: JSONObject()
private fun JSONObject.stringList(key: String): List<String> = array(key).objectsOrValues().mapNotNull { it.primitiveContent() }
private fun JSONArray.objectsOrValues(): List<Any?> = buildList(length()) {
    for (index in 0 until length()) add(opt(index))
}
private fun Any?.primitiveContent(): String? = this?.toString()?.takeUnless { it == "null" }

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

