package io.baiyanwu.coinmonitor.data.ai.market

import io.baiyanwu.coinmonitor.data.network.DexScreenerClient
import io.baiyanwu.coinmonitor.data.network.DexScreenerPairSelector
import io.baiyanwu.coinmonitor.domain.model.OnchainChainRegistry
import io.baiyanwu.coinmonitor.lib.agents.AssetRef
import io.baiyanwu.coinmonitor.lib.agents.MarketEvidence
import io.baiyanwu.coinmonitor.lib.agents.MarketEventType
import io.baiyanwu.coinmonitor.lib.agents.MarketEvidenceFreshness
import io.baiyanwu.coinmonitor.lib.agents.MarketImpactDirection
import io.baiyanwu.coinmonitor.lib.agents.MarketImpactStrength
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceAdapter
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceAuthMode
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceAuthProfile
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceCapability
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceQuery
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceRateLimitHint
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceSpec
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceType
import io.baiyanwu.coinmonitor.lib.agents.SourceTimestampConfidence

class ProjectInfoAdapter(
    private val dexScreenerClient: DexScreenerClient
) : MarketSourceAdapter {
    override val spec: MarketSourceSpec = MarketSourceSpec(
        id = SOURCE_ID,
        displayName = "DexScreener Project Info",
        type = MarketSourceType.CHAIN_ANNOUNCEMENT,
        capabilities = setOf(
            MarketSourceCapability.ASSET_KEYWORD_LOOKUP,
            MarketSourceCapability.CHAIN_ECOSYSTEM_TRACKING
        ),
        authProfile = MarketSourceAuthProfile(
            mode = MarketSourceAuthMode.NONE,
            required = false,
            credentialHint = null
        ),
        rateLimitHint = MarketSourceRateLimitHint(
            recommendedRequestsPerMinute = 10,
            supportsBurst = false,
            cacheTtlMillis = ONCHAIN_CACHE_TTL_MILLIS
        )
    )

    private val onchainCache = mutableMapOf<String, TimedCache<List<MarketEvidence>>>()

    override suspend fun fetch(query: MarketSourceQuery): List<MarketEvidence> {
        val asset = query.asset
        val result = if (asset.tokenAddress.isNullOrBlank() || asset.chainId.isNullOrBlank()) {
            listOf(buildLocalOverview(asset))
        } else {
            fetchOnchainOverview(asset)
        }
        return result.take(query.limit)
    }

    private suspend fun fetchOnchainOverview(asset: AssetRef): List<MarketEvidence> {
        val cacheKey = "${asset.chainId}:${asset.tokenAddress}"
        val cache = onchainCache.getOrPut(cacheKey) { TimedCache(ONCHAIN_CACHE_TTL_MILLIS) }
        return runCatching {
            cache.getOrLoad { buildDexScreenerOverview(asset) }
        }.getOrElse {
            listOf(buildLocalOverview(asset))
        }
    }

    private suspend fun buildDexScreenerOverview(asset: AssetRef): List<MarketEvidence> {
        val chain = OnchainChainRegistry.find(asset.chainId)
            ?: return listOf(buildLocalOverview(asset))
        val tokenAddress = asset.tokenAddress.orEmpty()
        val pairs = dexScreenerClient.getTokenPairs(chain.dexScreenerId, tokenAddress)
        val selected = DexScreenerPairSelector.select(pairs, tokenAddress, chain.family)
            ?: return listOf(buildLocalOverview(asset))
        val pair = selected.pair
        val name = selected.tokenName.ifBlank {
            asset.displayName ?: asset.baseSymbol ?: asset.symbol
        }
        val snippet = buildList {
            add("On-chain profile for $name.")
            add("Symbol ${selected.tokenSymbol}.")
            compactMetric("Price", selected.priceUsd.toString())?.let { add("$it.") }
            selected.change24hPercent?.let { add("24h change $it%.") }
            pair.liquidity?.usd?.let { add("Liquidity $it.") }
            pair.volume["h24"]?.let { add("24h volume $it.") }
            pair.marketCap?.let { add("Market cap $it.") }
            pair.fdv?.let { add("FDV $it.") }
            pair.dexId.takeIf(String::isNotBlank)?.let { add("DEX $it.") }
            pair.info?.websites.orEmpty().firstOrNull()?.url
                ?.takeIf(String::isNotBlank)
                ?.let { add("Website $it.") }
            pair.info?.socials.orEmpty()
                .mapNotNull { social ->
                    social.handle?.takeIf(String::isNotBlank)?.let { handle ->
                        val label = social.type ?: social.platform ?: "social"
                        "$label $handle"
                    }
                }
                .take(3)
                .forEach { add("Social $it.") }
            add("Pool ${pair.pairAddress}.")
            pair.url?.takeIf(String::isNotBlank)?.let { add("Pool link $it.") }
            add("Address $tokenAddress.")
        }.joinToString(" ")
        val nowMillis = System.currentTimeMillis()
        return listOf(
            MarketEvidence(
                id = "project-info:${chain.chainIndex}:$tokenAddress",
                sourceId = spec.id,
                sourceType = spec.type,
                title = "Project profile for ${asset.symbol}",
                url = pair.url ?: pair.info?.websites.orEmpty().firstOrNull()?.url.orEmpty(),
                publishedAtMillis = nowMillis,
                contentSnippet = snippet,
                eventType = MarketEventType.ECOSYSTEM_UPDATE,
                impactDirection = MarketImpactDirection.NEUTRAL,
                impactStrength = MarketImpactStrength.LOW,
                freshness = MarketEvidenceFreshness.UNKNOWN,
                sourceTimestampConfidence = SourceTimestampConfidence.UNKNOWN,
                relatedSymbols = listOfNotNull(asset.baseSymbol, asset.symbol),
                relevanceScore = 0.98,
                credibilityScore = 0.86
            )
        )
    }

    private fun buildLocalOverview(asset: AssetRef): MarketEvidence {
        val nowMillis = System.currentTimeMillis()
        val snippet = buildString {
            append("Local project profile for ")
            append(asset.displayName ?: asset.baseSymbol ?: asset.symbol)
            append(". Symbol ").append(asset.symbol).append('.')
            asset.exchange?.takeIf(String::isNotBlank)?.let { append(" Exchange ").append(it).append('.') }
            asset.marketType?.takeIf(String::isNotBlank)?.let { append(" Market type ").append(it).append('.') }
            asset.chainFamily?.takeIf(String::isNotBlank)?.let { append(" Chain ").append(it).append('.') }
            asset.tokenAddress?.takeIf(String::isNotBlank)?.let { append(" Address ").append(it).append('.') }
        }
        return MarketEvidence(
            id = "project-info:local:${asset.symbol.lowercase()}",
            sourceId = spec.id,
            sourceType = spec.type,
            title = "Project profile for ${asset.symbol}",
            url = "",
            publishedAtMillis = nowMillis,
            contentSnippet = snippet,
            eventType = MarketEventType.ECOSYSTEM_UPDATE,
            impactDirection = MarketImpactDirection.NEUTRAL,
            impactStrength = MarketImpactStrength.LOW,
            freshness = MarketEvidenceFreshness.UNKNOWN,
            sourceTimestampConfidence = SourceTimestampConfidence.UNKNOWN,
            relatedSymbols = listOfNotNull(asset.baseSymbol, asset.symbol),
            relevanceScore = 0.9,
            credibilityScore = 0.75
        )
    }

    private companion object {
        const val SOURCE_ID = "project-info"
        const val ONCHAIN_CACHE_TTL_MILLIS = 30 * 60 * 1_000L
    }
}
