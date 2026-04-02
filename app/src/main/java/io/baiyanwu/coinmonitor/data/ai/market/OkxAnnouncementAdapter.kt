package io.baiyanwu.coinmonitor.data.ai.market

import io.baiyanwu.coinmonitor.lib.agents.AssetRef
import io.baiyanwu.coinmonitor.lib.agents.MarketEvidence
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceAdapter
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceAuthMode
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceAuthProfile
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceCapability
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceQuery
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceRateLimitHint
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceSpec
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 基于 OKX Help Center 公告页的低噪音公告适配器。
 */
class OkxAnnouncementAdapter(
    private val okHttpClient: OkHttpClient
) : MarketSourceAdapter {
    override val spec: MarketSourceSpec = MarketSourceSpec(
        id = SOURCE_ID,
        displayName = "OKX Announcements",
        type = MarketSourceType.EXCHANGE_ANNOUNCEMENT,
        capabilities = setOf(MarketSourceCapability.OFFICIAL_ANNOUNCEMENT_TRACKING),
        authProfile = MarketSourceAuthProfile(mode = MarketSourceAuthMode.NONE),
        rateLimitHint = MarketSourceRateLimitHint(
            recommendedRequestsPerMinute = 6,
            supportsBurst = false,
            cacheTtlMillis = CACHE_TTL_MILLIS
        )
    )

    private val cache = TimedCache<List<AnnouncementRecord>>(CACHE_TTL_MILLIS)

    /**
     * 拉取 OKX 最新公告页，并按当前资产过滤。
     */
    override suspend fun fetch(query: MarketSourceQuery): List<MarketEvidence> {
        val records = runCatching { cache.getOrLoad(::loadAnnouncements) }.getOrDefault(emptyList())
        val publishedAfterMillis = query.publishedAfterMillis
        return records
            .asSequence()
            .filter { record ->
                publishedAfterMillis == null || record.publishedAtMillis >= publishedAfterMillis
            }
            .filter { record -> query.asset.isRelevant("${record.title} ${record.snippet}") }
            .map { record -> record.toEvidence(query.asset) }
            .sortedByDescending { it.relevanceScore ?: 0.0 }
            .take(query.limit)
            .toList()
    }

    private suspend fun loadAnnouncements(): List<AnnouncementRecord> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_ANNOUNCEMENTS_URL)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("User-Agent", BROWSER_USER_AGENT)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@withContext emptyList()
            parseOkxAnnouncementRecords(body)
        }
    }

    private fun AnnouncementRecord.toEvidence(asset: AssetRef): MarketEvidence {
        val relevance = asset.relevanceScore("$title $snippet")
        return MarketEvidence(
            id = id,
            sourceId = spec.id,
            sourceType = spec.type,
            title = title,
            url = url,
            publishedAtMillis = publishedAtMillis,
            contentSnippet = snippet.ifBlank { title },
            eventType = classifyEventType(title),
            impactDirection = classifyImpactDirection(title),
            impactStrength = classifyImpactStrength(title),
            freshness = resolveFreshness(publishedAtMillis),
            sourceTimestampConfidence = sourceTimestampConfidence,
            relatedSymbols = listOfNotNull(asset.baseSymbol, asset.symbol),
            relevanceScore = relevance,
            credibilityScore = 0.95
        )
    }

    private companion object {
        private const val SOURCE_ID = "okx-announcements"
        private const val CACHE_TTL_MILLIS = 10 * 60 * 1000L
        private const val LATEST_ANNOUNCEMENTS_URL =
            "https://www.okx.com/help/section/announcements-latest-announcements"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
