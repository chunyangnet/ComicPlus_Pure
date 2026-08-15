package com.comicplus.pure

import org.junit.Assert.assertEquals
import org.junit.Test
import org.json.JSONObject

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
    fun legacyDownloadsAreInvalidatedAfterExactScalingUpgrade() {
        assertEquals(false, isCompatibleDownload("255468", null))
        assertEquals(false, isCompatibleDownload("255468", "decode-v2"))
        assertEquals(false, isCompatibleDownload("200000", null))
        assertEquals(true, isCompatibleDownload("421927", "decode-v3"))
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
    }

    @Test
    fun lockStripeIndexIsStableAndAlwaysInBounds() {
        val first = JmGateway.lockStripeIndex("page-key", 32)
        assertEquals(first, JmGateway.lockStripeIndex("page-key", 32))
        assertEquals(true, first in 0 until 32)
        assertEquals(true, JmGateway.lockStripeIndex("polygenelubricants", 32) in 0 until 32)
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
}
