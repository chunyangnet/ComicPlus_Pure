package com.comicplus.pure

import org.junit.Assert.assertEquals
import org.json.JSONArray
import org.junit.Test
import org.json.JSONObject
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

class JmGatewayProtocolTest {
    @Test
    fun segmentationCount_doesNotDecodeContentBeforeScrambleThreshold() {
        assertEquals(0, JmGateway.segmentationCount("220980", "200000", "00001.webp"))
    }

    @Test
    fun segmentationCount_usesTenFixedSegmentsForJm255468() {
        assertEquals(10, JmGateway.segmentationCount("220980", "255468", "00001.webp"))
    }

    @Test
    fun segmentationCount_usesHashedRulesForNewerContent() {
        assertEquals(6, JmGateway.segmentationCount("220980", "268850", "00001.webp"))
        assertEquals(8, JmGateway.segmentationCount("220980", "421927", "00001.webp"))
    }

    @Test
    fun scrambledSourceRanges_coverEveryPixelExactlyOnceInReverseOrder() {
        val ranges = JmGateway.scrambledPageSourceRanges(height = 1_003, segments = 10)

        assertEquals(900 to 1_003, ranges.first())
        assertEquals(0 to 100, ranges.last())
        assertEquals(1_003, ranges.sumOf { it.second - it.first })
        assertEquals((0 until 1_003).toList(), ranges.flatMap { (top, bottom) -> (top until bottom).toList() }.sorted())
    }

    @Test
    fun decodedPageSize_usesExactScalingInsteadOfPowerOfTwoCliff() {
        assertEquals(1_440 to 1_999, JmGateway.decodedPageSize(width = 1_441, height = 2_000))
        assertEquals(
            1_080 to 1_499,
            JmGateway.decodedPageSize(width = 1_441, height = 2_000, maxWidth = 1_080),
        )
        val longPage = JmGateway.decodedPageSize(width = 1_440, height = 10_000)
        assertEquals(true, longPage.first.toLong() * longPage.second <= 12_000_000L)
        val lowMemoryLongPage = JmGateway.decodedPageSize(width = 1_440, height = 10_000, maxPixels = 8_000_000L)
        assertEquals(true, lowMemoryLongPage.first.toLong() * lowMemoryLongPage.second <= 8_000_000L)
        val extremeStrip = JmGateway.decodedPageSize(width = 1, height = 80_000_000)
        assertEquals(true, extremeStrip.first.toLong() * extremeStrip.second <= 12_000_000L)
    }

    @Test
    fun legacyDownloadsAreInvalidatedAfterSeamlessStitchingUpgrade() {
        assertEquals(false, isCompatibleDownload("255468", null))
        assertEquals(false, isCompatibleDownload("255468", "decode-v2"))
        assertEquals(false, isCompatibleDownload("421927", "decode-v3"))
        assertEquals(false, isCompatibleDownload("200000", null))
        assertEquals(true, isCompatibleDownload("421927", "decode-v4"))
    }

    @Test
    fun searchPaginationUsesLoadedOffsetAndStopsOnRedirect() {
        assertEquals(true, JmGateway.hasMoreSearchResults(2, 20, 20, 45, null))
        assertEquals(false, JmGateway.hasMoreSearchResults(3, 20, 5, 45, null))
        assertEquals(false, JmGateway.hasMoreSearchResults(1, 20, 20, 100, "255468"))
    }

    @Test
    fun officialListPaginationUsesServerPageSize() {
        assertEquals(true, JmGateway.hasMorePagedResults(1, 20, 20, 45))
        assertEquals(true, JmGateway.hasMorePagedResults(2, 20, 20, 45))
        assertEquals(false, JmGateway.hasMorePagedResults(3, 20, 5, 45))
        assertEquals(false, JmGateway.hasMorePagedResults(1, 20, 0, 45))
        assertEquals(false, JmGateway.hasMorePagedResults(200, 20, 20, Long.MAX_VALUE))
        assertEquals(false, JmGateway.hasMoreSearchResults(200, 20, 20, Long.MAX_VALUE, null))
        assertEquals(true, JmGateway.hasMorePagedResults(1, 20, 20, null))
        assertEquals(false, JmGateway.hasMorePagedResults(1, 20, 19, null))
        assertEquals(true, JmGateway.hasMoreSearchResults(2, 20, 20, null, null))
        assertEquals(40L, JmGateway.normalizedPagedTotal(2, 20, 20, null))
        assertEquals(45L, JmGateway.normalizedPagedTotal(3, 20, 5, 45L))
        assertEquals(45L, JmGateway.normalizedPagedTotal(3, 20, 5, 1L))
    }

    @Test
    fun lockStripeIndexIsStableAndAlwaysInBounds() {
        val first = JmGateway.lockStripeIndex("page-key", 32)
        assertEquals(first, JmGateway.lockStripeIndex("page-key", 32))
        assertEquals(true, first in 0 until 32)
        assertEquals(true, JmGateway.lockStripeIndex("polygenelubricants", 32) in 0 until 32)
    }

    @Test
    fun lowerHexEncodingHandlesSignedBytesWithoutFormattingAllocations() {
        assertEquals("000f10ff", encodeLowerHex(byteArrayOf(0x00, 0x0f, 0x10, 0xff.toByte())))
    }

    @Test
    fun metadataTextCannotCreateExtraFields() {
        assertEquals("标题 副标题", sanitizeMetadataText("标题\r\n副标题"))
    }

    @Test
    fun compactServerNumbersRemainUsableForPagination() {
        assertEquals(1_200L, parseCompactLong("1.2K"))
        assertEquals(35_000L, parseCompactLong("3.5万"))
        assertEquals(1_234_567L, parseCompactLong("1,234,567"))
        assertEquals(null, parseCompactLong("not-a-number"))
    }

    @Test
    fun jsonIntegersRejectOverflowInsteadOfWrapping() {
        assertEquals(Int.MAX_VALUE, parseJsonInt(Int.MAX_VALUE.toLong()))
        assertEquals(Int.MIN_VALUE, parseJsonInt(Int.MIN_VALUE.toString()))
        assertEquals(null, parseJsonInt(Int.MAX_VALUE.toLong() + 1L))
        assertEquals(null, parseJsonInt(Long.MAX_VALUE))
        assertEquals(null, parseJsonInt("1.5"))
    }

    @Test
    fun jsonAliasHelpersSkipBlankValuesAndBoundEmbeddedArrays() {
        val payload = JSONObject()
            .put("blank", "   ")
            .put("fallback", "  usable  ")
            .put("embedded", "[1,2,3]")

        assertEquals("usable", firstJsonString(payload, "blank", "fallback"))
        assertEquals(
            listOf("1", "2"),
            firstJsonArray(payload, "embedded")
                ?.objectsOrValues(limit = 2)
                ?.mapNotNull(Any?::primitiveContent),
        )
    }

    @Test
    fun sourceOrderingPrefersReachableLowLatencyHostOnlyWhenEnabled() {
        val endpoints = listOf(
            JmSourceEndpoint("slow.example", 240),
            JmSourceEndpoint("unreachable.example", null),
            JmSourceEndpoint("fast.example", 35),
        )
        assertEquals(
            listOf("fast.example", "slow.example", "unreachable.example"),
            orderSourceEndpoints(endpoints, autoSelect = true).map(JmSourceEndpoint::host),
        )
        assertEquals(
            listOf("slow.example", "unreachable.example", "fast.example"),
            orderSourceEndpoints(endpoints, autoSelect = false).map(JmSourceEndpoint::host),
        )
    }

    @Test
    fun sourceOrderingRetainsOfficialOrderAsTieBreaker() {
        val endpoints = listOf(
            JmSourceEndpoint("b.example", 40),
            JmSourceEndpoint("a.example", 40),
        )
        assertEquals(
            listOf("b.example", "a.example"),
            orderSourceEndpoints(endpoints, autoSelect = true, preferredHosts = listOf("b.example", "a.example"))
                .map(JmSourceEndpoint::host),
        )
    }

    @Test
    fun manuallySelectedImageLineRemainsFirst() {
        val endpoints = listOf(
            JmSourceEndpoint("fast.example", 20),
            JmSourceEndpoint("chosen.example", 180),
            JmSourceEndpoint("fallback.example", null),
        )
        assertEquals(
            listOf("chosen.example", "fast.example", "fallback.example"),
            orderSourceEndpoints(
                endpoints = endpoints,
                autoSelect = false,
                preferredHosts = listOf("chosen.example", "fast.example", "fallback.example"),
            ).map(JmSourceEndpoint::host),
        )
    }

    @Test
    fun officialForumPayloadParsesAliasesAndNestedReplies() {
        val page = parseJmCommentPage(
            JSONObject(
                """
                {
                  "total": "21",
                  "list": [
                    {
                      "CID": "7",
                      "UID": "42",
                      "AID": "123",
                      "username": "pilot",
                      "nickname": "Captain",
                      "content": "<p>Hello<br/>JM</p>",
                      "photo": "https://cdn.example/avatar.jpg",
                      "addtime": "2026-08-15 12:00:00",
                      "likes": "3",
                      "spoiler": "1",
                      "replys": [
                        {"cid": "8", "username": "reply", "comment": "Thanks", "parent_cid": "7"}
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            ),
            page = 1,
        )

        assertEquals(21L, page.total)
        assertEquals(true, page.hasMore)
        assertEquals("7", page.comments.single().id)
        assertEquals("42", page.comments.single().userId)
        assertEquals("Captain", page.comments.single().nickname)
        assertEquals(3L, page.comments.single().likes)
        assertEquals(true, page.comments.single().spoiler)
        assertEquals("8", page.comments.single().replies.single().id)
        assertEquals("7", page.comments.single().replies.single().parentId)
    }

    @Test
    fun officialForumPayloadUsesStableFallbackIdsAndStopsAtReplyDepthLimit() {
        val page = parseJmCommentPage(
            JSONObject("""{"list":[{"content":"root","replies":[{"content":"reply","replies":[{"content":"deep","replies":[{"content":"too deep"}]}]}]}]}"""),
            page = 3,
        )

        val root = page.comments.single()
        assertEquals("3-1", root.id)
        assertEquals("3-1-r1", root.replies.single().id)
        assertEquals("3-1-r1-r1", root.replies.single().replies.single().id)
        assertEquals(0, root.replies.single().replies.single().replies.size)
    }

    @Test
    fun officialFavoritePayloadParsesAliasesAndBoundsItems() {
        val page = parseFavoritePage(
            JSONObject(
                """
                {
                  "total": "21",
                  "content": [
                    {"aid":"123", "title":"Saved", "image":"cover.jpg", "author":"a,b"},
                    {"id":"123", "name":"duplicate"},
                    {"id":"bad", "name":"ignored"}
                  ]
                }
                """.trimIndent(),
            ),
            page = 1,
        )

        assertEquals(21L, page.total)
        assertEquals(true, page.hasMore)
        assertEquals(true, page.totalKnown)
        assertEquals(1, page.items.size)
        assertEquals("123", page.items.single().id)
        assertEquals("Saved", page.items.single().title)
        assertEquals(listOf("a", "b"), page.items.single().authors)
        assertEquals("https://cover.invalid/media/albums/cover.jpg", page.items.single().coverUrl)
    }

    @Test
    fun favoritePageMarksAComputedTotalAsUnknown() {
        val page = parseFavoritePage(
            JSONObject("""{"list":[{"id":"123","name":"Saved"}]}"""),
        )

        assertEquals(1L, page.total)
        assertEquals(false, page.totalKnown)
    }

    @Test
    fun officialFavoritePayloadParsesChildFoldersAndSelectedFolder() {
        val page = parseFavoritePage(
            JSONObject(
                """
                {
                  "total": "1",
                  "list": [{"id":"123", "name":"Saved"}],
                  "folder_list": [
                    {"FID":"7", "name":"追更"},
                    {"fid":"8", "folder_name":"已读"},
                    {"FID":"7", "name":"duplicate"},
                    {"FID":"bad", "name":"ignored"}
                  ]
                }
                """.trimIndent(),
            ),
            page = 1,
            folderId = "7",
        )

        assertEquals("7", page.folderId)
        assertEquals(listOf("0", "7", "8"), page.folders.map(JmFavoriteFolder::id))
        assertEquals(listOf("全部", "追更", "已读"), page.folders.map(JmFavoriteFolder::name))
    }

    @Test
    fun officialFavoriteFolderMutationsUseTheVerifiedMobileFields() {
        assertEquals(
            mapOf("type" to "add", "folder_id" to "0", "folder_name" to "追更"),
            JmGateway.favoriteFolderMutationForm(
                type = "add",
                folderId = "0",
                folderName = "追更",
            ),
        )
        assertEquals(
            mapOf("type" to "move", "folder_id" to "7", "aid" to "123"),
            JmGateway.favoriteFolderMutationForm(
                type = "move",
                folderId = "7",
                albumId = "123",
            ),
        )
    }

    @Test
    fun sourceSnapshotRefreshesOnlyWhenMeasurementsAreMissingOrStale() {
        val snapshot = JmSourceSnapshot(
            endpoints = listOf(JmSourceEndpoint("api.example", 42L)),
            selectedHost = "api.example",
            updatedAt = 900L,
            imageEndpoints = listOf(JmSourceEndpoint("cdn.example", 55L)),
            selectedImageHost = "cdn.example",
            imageUpdatedAt = 900L,
        )

        assertEquals(false, sourceSnapshotNeedsRefresh(snapshot, nowMillis = 1_000L, maxAgeMillis = 500L))
        assertEquals(true, sourceSnapshotNeedsRefresh(snapshot, nowMillis = 1_401L, maxAgeMillis = 500L))
        assertEquals(
            true,
            sourceSnapshotNeedsRefresh(
                snapshot.copy(imageEndpoints = listOf(JmSourceEndpoint("cdn.example", null))),
                nowMillis = 1_000L,
                maxAgeMillis = 500L,
            ),
        )
        assertEquals(
            true,
            sourceSnapshotNeedsRefresh(snapshot, nowMillis = 800L, maxAgeMillis = 500L),
        )
    }

    @Test
    fun officialForumPaginationUsesTheTenItemServerPage() {
        val tenComments = (1..10).joinToString(",") { index -> "{\"CID\":\"$index\"}" }

        assertEquals(
            true,
            parseJmCommentPage(JSONObject("""{"total":"23","list":[$tenComments]}"""), page = 2).hasMore,
        )
        assertEquals(
            false,
            parseJmCommentPage(JSONObject("""{"total":"23","list":[{},{},{}]}"""), page = 3).hasMore,
        )
    }

    @Test
    fun officialForumPayloadCapsUntrustedItemsAndText() {
        val hugeText = "x".repeat(25_000)
        val comments = JSONArray().apply {
            repeat(60) { index ->
                put(JSONObject().put("CID", index + 1).put("content", hugeText))
            }
        }
        val page = parseJmCommentPage(JSONObject().put("total", 60).put("list", comments))

        assertEquals(50, page.comments.size)
        assertEquals(20_000, page.comments.first().content.length)
    }

    @Test
    fun sourceValidationRejectsLocalHostsAndNonSuccessImageProbes() {
        assertEquals("example.com", JmGateway.normalizeRemoteDomain("example.com"))
        assertEquals(null, JmGateway.normalizeRemoteDomain("localhost"))
        assertEquals(null, JmGateway.normalizeRemoteDomain("reader.localhost"))
        assertEquals(null, JmGateway.normalizeRemoteDomain("127.0.0.1"))
        assertEquals(null, JmGateway.normalizeRemoteDomain("https://example.com:8443"))
        assertEquals(null, JmGateway.normalizeRemoteDomain("intranet"))
        assertEquals(null, JmGateway.normalizeRemoteDomain("https://example.com/setting"))
        assertEquals(null, JmGateway.normalizeRemoteDomain("https://example.com?next=localhost"))
        assertEquals(null, JmGateway.normalizeRemoteDomain("https://example.com/#fragment"))
        assertEquals(null, JmGateway.normalizeRemoteDomain("https://user:secret@example.com"))
        assertEquals(null, JmGateway.normalizeRemoteDomain("bad..example.com"))
        assertEquals(null, JmGateway.normalizeRemoteDomain("-bad.example.com"))
        assertEquals("https://cdn.example/avatar.jpg", JmGateway.normalizeRemoteHttpsUrl("https://cdn.example/avatar.jpg"))
        assertEquals(null, JmGateway.normalizeRemoteHttpsUrl("https://localhost/avatar.jpg"))
        assertEquals(null, JmGateway.normalizeRemoteHttpsUrl("https://user:secret@cdn.example/avatar.jpg"))
        assertEquals(null, JmGateway.normalizeRemoteHttpsUrl("https://cdn.example:8443/avatar.jpg"))
        assertEquals(true, JmGateway.isUsableImageProbeStatus(200))
        assertEquals(true, JmGateway.isUsableImageProbeStatus(204))
        assertEquals(false, JmGateway.isUsableImageProbeStatus(404))
        assertEquals(false, JmGateway.isUsableImageProbeStatus(500))
    }

    @Test
    fun cookieJarBoundsUntrustedCookieCountAndValueSize() {
        val jar = MemoryCookieJar()
        val url = "https://example.com/setting".toHttpUrl()
        val cookies = (1..100).map { index ->
            Cookie.Builder()
                .name("c$index")
                .value("ok")
                .domain("example.com")
                .build()
        } + Cookie.Builder()
            .name("oversized")
            .value("x".repeat(5 * 1024))
            .domain("example.com")
            .build()

        jar.saveFromResponse(url, cookies)

        assertEquals(64, jar.loadForRequest(url).size)
        assertEquals(false, jar.loadForRequest(url).any { it.name == "oversized" })
    }

    @Test
    fun cookieJarReplacesTheSameCookieIdentityWithoutDuplicates() {
        val jar = MemoryCookieJar()
        val url = "https://example.com/setting".toHttpUrl()
        fun cookie(value: String) = Cookie.Builder()
            .name("AVS")
            .value(value)
            .domain("example.com")
            .path("/")
            .build()

        jar.saveFromResponse(url, listOf(cookie("old")))
        jar.saveFromResponse(url, listOf(cookie("new")))

        assertEquals(listOf("new"), jar.loadForRequest(url).map { it.value })
    }

    @Test
    fun cookieJarPublishesRotatedSessionCookies() {
        val updates = mutableListOf<String>()
        val jar = MemoryCookieJar { cookie ->
            if (cookie.name == "AVS") updates += cookie.value
        }
        val url = "https://example.com/setting".toHttpUrl()

        jar.saveFromResponse(
            url,
            listOf(
                Cookie.Builder()
                    .name("AVS")
                    .value("rotated-session")
                    .domain("example.com")
                    .path("/")
                    .build(),
            ),
        )

        assertEquals(listOf("rotated-session"), updates)
    }

    @Test
    fun manuallyInstalledSessionCookieDoesNotLookLikeServerRotation() {
        val updates = mutableListOf<String>()
        val jar = MemoryCookieJar { cookie -> updates += cookie.value }
        val url = "https://example.com/setting".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("AVS")
            .value("restored-session")
            .domain("example.com")
            .path("/")
            .build()

        jar.install(url, listOf(cookie))

        assertEquals(emptyList<String>(), updates)
        assertEquals(listOf("restored-session"), jar.loadForRequest(url).map { it.value })
    }

    @Test
    fun chapterImageOrderingDoesNotDropFilesWithSameOrMissingSequence() {
        assertEquals(
            listOf("1.jpg", "1.png", "cover.webp", "extra.jpeg"),
            JmGateway.normalizeChapterImageFiles(listOf("extra.jpeg", "1.png", "cover.webp", "1.jpg", "1.JPG")),
        )
    }

    @Test
    fun mirroredComicPagesShareOneLogicalCacheIdentity() {
        val firstMirror = JmPage(
            index = 1,
            photoId = "123",
            fileName = "00001.jpg",
            scrambleId = "220980",
            url = "https://cdn-msp.jmapiproxy1.cc/media/photos/123/00001.jpg",
            referer = "https://example.com/",
        )
        val secondMirror = firstMirror.copy(
            url = "https://cdn-msp.jmapiproxy2.cc/media/photos/123/00001.jpg",
        )

        assertEquals(pageContentIdentity(firstMirror), pageContentIdentity(secondMirror))
        assertEquals(
            false,
            pageContentIdentity(firstMirror) == pageContentIdentity(firstMirror.copy(fileName = "00002.jpg")),
        )
    }

    @Test
    fun commentPaginationInfersMoreOnlyFromFullPageWhenTotalIsMissing() {
        val tenComments = (1..10).joinToString(",") { index -> "{\"CID\":\"$index\"}" }

        val secondPage = parseJmCommentPage(JSONObject("""{"list":[$tenComments]}"""), page = 2)
        assertEquals(true, secondPage.hasMore)
        assertEquals(20L, secondPage.total)
        assertEquals(
            false,
            parseJmCommentPage(JSONObject("""{"list":[{"CID":"1"}]}"""), page = 1).hasMore,
        )
    }

    @Test
    fun imageConnectionWarmupExpiresBeforeIdleConnectionsAreReaped() {
        assertEquals(false, JmGateway.isImageWarmupFresh(null, now = 100L))
        assertEquals(true, JmGateway.isImageWarmupFresh(lastWarmedAt = 100L, now = 239_999L))
        assertEquals(false, JmGateway.isImageWarmupFresh(lastWarmedAt = 100L, now = 240_100L))
        assertEquals(false, JmGateway.isImageWarmupFresh(lastWarmedAt = 200L, now = 100L))
    }

    @Test
    fun restoredSessionIsOnlyInvalidatedWhenEveryReachableResultRejectsAuthentication() {
        assertEquals(true, JmGateway.shouldInvalidateRestoredSession(authFailures = 2, otherFailures = 0))
        assertEquals(false, JmGateway.shouldInvalidateRestoredSession(authFailures = 1, otherFailures = 1))
        assertEquals(false, JmGateway.shouldInvalidateRestoredSession(authFailures = 0, otherFailures = 3))
    }

    @Test
    fun loginPersistsTheResponseCookieInsteadOfAStalePayloadToken() {
        assertEquals("cookie-session", JmGateway.selectLoginAvs("payload-session", "cookie-session"))
        assertEquals("payload-session", JmGateway.selectLoginAvs("payload-session", null))
        assertEquals("payload-session", JmGateway.selectLoginAvs("payload-session", ""))
    }

    @Test
    fun sessionCookieAcceptsExactAndParentApiDomainsOnly() {
        val hosts = listOf("www.cdngwc.cc", "api.example.com")
        assertEquals(true, JmGateway.isCookieDomainForHosts("www.cdngwc.cc", hosts))
        assertEquals(true, JmGateway.isCookieDomainForHosts("cdngwc.cc", hosts))
        assertEquals(false, JmGateway.isCookieDomainForHosts("evil-cdngwc.cc", hosts))
        assertEquals(false, JmGateway.isCookieDomainForHosts("www.api.example.com", hosts))
    }

    @Test
    fun authenticatedHostCooldownSkipsRecentFailuresButAlwaysKeepsOneFallback() {
        assertEquals(
            listOf("expired.example", "healthy.example"),
            JmGateway.prioritizeHostsByFailureCooldown(
                candidates = listOf("expired.example", "cooling.example", "healthy.example"),
                failedAt = mapOf("expired.example" to 700L, "cooling.example" to 950L),
                now = 1_000L,
                cooldownMillis = 200L,
            ),
        )
        assertEquals(
            listOf("oldest.example"),
            JmGateway.prioritizeHostsByFailureCooldown(
                candidates = listOf("oldest.example", "newest.example"),
                failedAt = mapOf("oldest.example" to 800L, "newest.example" to 950L),
                now = 1_000L,
                cooldownMillis = 500L,
            ),
        )
    }

    @Test
    fun favoriteSyncUsesKnownTotalToBoundParallelPagePlan() {
        assertEquals(1, JmGateway.favoritePageCount(total = 0L, maxPages = 10))
        assertEquals(2, JmGateway.favoritePageCount(total = 21L, maxPages = 10))
        assertEquals(10, JmGateway.favoritePageCount(total = 500L, maxPages = 10))
    }

    @Test
    fun coverMirrorsPreserveTheCanonicalPathAndQuery() {
        val mirrors = JmGateway.coverMirrorUrls(
            "https://origin.example/media/albums/123_3x4.jpg?token=public",
        )

        assertEquals(4, mirrors.size)
        assertEquals(true, mirrors.all { it.contains("/media/albums/123_3x4.jpg?token=public") })
        assertEquals(mirrors.size, mirrors.distinct().size)
        assertEquals(
            listOf("https://origin.example/custom/cover.jpg"),
            JmGateway.coverMirrorUrls("https://origin.example/custom/cover.jpg"),
        )
    }

    @Test
    fun imageDecodeConcurrencyProtectsLowMemoryDevices() {
        assertEquals(1, JmGateway.imageWorkPermits(memoryMb = 384, isLowRamDevice = false))
        assertEquals(1, JmGateway.imageWorkPermits(memoryMb = 512, isLowRamDevice = true))
        assertEquals(2, JmGateway.imageWorkPermits(memoryMb = 512, isLowRamDevice = false))
    }

    @Test
    fun homeFeedMergeKeepsPriorityOrderAndRemovesDuplicates() {
        val first = listOf(ranking("1"), ranking("2"))
        val second = listOf(ranking("2"), ranking("3"))

        assertEquals(listOf("1", "2", "3"), mergeHomeRankings(first, second).map(JmRanking::id))
    }

    @Test
    fun rankingCapabilityRequiresAtLeastOneUsableItem() {
        assertEquals(false, JSONObject("""{"content":[]}""").hasUsableRankingPayload("content"))
        assertEquals(
            false,
            JSONObject("""{"content":[{"id":"bad","name":"Broken"}]}""")
                .hasUsableRankingPayload("content"),
        )
        assertEquals(
            true,
            JSONObject("""{"content":[{"id":"123","name":"Supported"}]}""")
                .hasUsableRankingPayload("content"),
        )
    }

    @Test
    fun weeklyCapabilityAcceptsTypeAndTypesPayloadVariants() {
        assertEquals(
            true,
            JSONObject("""{"categories":[{"id":"1"}],"type":[{"id":"2"}]}""")
                .hasUsableWeekCatalogPayload(),
        )
        assertEquals(
            true,
            JSONObject("""{"data":{"categories":[{"id":"1"}],"types":[{"id":"2"}]}}""")
                .hasUsableWeekCatalogPayload(),
        )
        assertEquals(
            false,
            JSONObject("""{"categories":[],"types":[]}""").hasUsableWeekCatalogPayload(),
        )
    }

    private fun ranking(id: String) = JmRanking(id = id, title = "Comic $id", coverUrl = null)
}
