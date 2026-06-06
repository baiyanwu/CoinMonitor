package io.baiyanwu.coinmonitor.data.ai.market

import io.baiyanwu.coinmonitor.data.network.OkxOnChainApi
import io.baiyanwu.coinmonitor.data.network.OkxOnChainPriceRequest
import io.baiyanwu.coinmonitor.data.network.OkxOnChainRequestSigner
import io.baiyanwu.coinmonitor.data.network.OkxOnChainTokenBasicInfoRow
import io.baiyanwu.coinmonitor.data.network.OkxOnChainTokenPriceInfoRow
import io.baiyanwu.coinmonitor.data.network.OkxOnChainTokenRow
import io.baiyanwu.coinmonitor.domain.model.OkxApiCredentials
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 使用本地资产信息和 OKX Onchain API 生成项目概览型情报。
 */
class ProjectInfoAdapter(
    private val okxOnChainApi: OkxOnChainApi,
    private val okxCredentialsProvider: suspend () -> OkxApiCredentials?
) : MarketSourceAdapter {
    override val spec: MarketSourceSpec = MarketSourceSpec(
        id = SOURCE_ID,
        displayName = "Project Info",
        type = MarketSourceType.CHAIN_ANNOUNCEMENT,
        capabilities = setOf(
            MarketSourceCapability.ASSET_KEYWORD_LOOKUP,
            MarketSourceCapability.CHAIN_ECOSYSTEM_TRACKING
        ),
        authProfile = MarketSourceAuthProfile(
            mode = MarketSourceAuthMode.USER_API_KEY,
            required = false,
            credentialHint = "Optional OKX Web3 API credentials unlock on-chain project enrichment."
        ),
        rateLimitHint = MarketSourceRateLimitHint(
            recommendedRequestsPerMinute = 10,
            supportsBurst = false,
            cacheTtlMillis = ONCHAIN_CACHE_TTL_MILLIS
        )
    )

    private val requestJson = Json { explicitNulls = false }
    private val onchainCache = mutableMapOf<String, TimedCache<List<MarketEvidence>>>()

    /**
     * 生成资产概览型 evidence；链上资产优先尝试 OKX Onchain 增强。
     */
    override suspend fun fetch(query: MarketSourceQuery): List<MarketEvidence> {
        val asset = query.asset
        return if (asset.tokenAddress.isNullOrBlank() || asset.chainId.isNullOrBlank()) {
            listOf(buildLocalOverview(asset))
        } else {
            fetchOnchainOverview(asset)
        }.take(query.limit)
    }

    private suspend fun fetchOnchainOverview(asset: AssetRef): List<MarketEvidence> {
        val cacheKey = "${asset.chainId}:${asset.tokenAddress.orEmpty().lowercase()}"
        val cache = onchainCache.getOrPut(cacheKey) { TimedCache(ONCHAIN_CACHE_TTL_MILLIS) }
        return runCatching {
            cache.getOrLoad {
                buildOnchainOverview(asset)
            }
        }.getOrElse {
            listOf(buildLocalOverview(asset))
        }
    }

    private suspend fun buildOnchainOverview(asset: AssetRef): List<MarketEvidence> {
        val credentials = okxCredentialsProvider()
        if (credentials == null || !credentials.enabled || !credentials.isReady) {
            return listOf(buildLocalOverview(asset))
        }

        val chainIndex = asset.chainId.orEmpty()
        val tokenAddress = asset.tokenAddress.orEmpty()
        val requestBody = listOf(
            OkxOnChainPriceRequest(
                chainIndex = chainIndex,
                tokenContractAddress = tokenAddress
            )
        )
        val searchRow = searchToken(credentials, chainIndex, tokenAddress)
        val basicInfo = lookupBasicInfo(credentials, requestBody)
        val priceInfo = lookupPriceInfo(credentials, requestBody)

        val titleName = basicInfo?.tokenName
            ?: searchRow?.tokenName
            ?: asset.displayName
            ?: asset.baseSymbol
            ?: asset.symbol
        val snippetParts = buildList {
            add("On-chain profile for $titleName.")
            basicInfo?.tokenSymbol?.takeIf { it.isNotBlank() }?.let { add("Symbol $it.") }
            searchRow?.explorerUrl?.takeIf { it.isNotBlank() }?.let { add("Explorer $it.") }
            basicInfo?.officialWebsite?.takeIf { it.isNotBlank() }?.let { add("Website $it.") }
            compactMetric("Price", priceInfo?.price ?: searchRow?.price)?.let { add("$it.") }
            compactMetric("24h change", priceInfo?.change ?: searchRow?.change)?.let { add("$it%.") }
            compactMetric("Market cap", priceInfo?.marketCap ?: searchRow?.marketCap)?.let { add("$it.") }
            compactMetric("Liquidity", priceInfo?.liquidity ?: searchRow?.liquidity)?.let { add("$it.") }
            compactMetric("Holders", priceInfo?.holders ?: searchRow?.holders)?.let { add("$it.") }
            if (basicInfo?.tagList?.communityRecognized == true) {
                add("Community recognized.")
            }
            add("Address ${tokenAddress.lowercase()}.")
        }

        val publishedAtMillis = parseIsoInstantMillis(priceInfo?.time)
            ?: System.currentTimeMillis()
        val confidence = if (parseIsoInstantMillis(priceInfo?.time) != null) {
            SourceTimestampConfidence.EXACT
        } else {
            SourceTimestampConfidence.UNKNOWN
        }

        return listOf(
            MarketEvidence(
                id = "project-info:$chainIndex:${tokenAddress.lowercase()}",
                sourceId = spec.id,
                sourceType = spec.type,
                title = "Project profile for ${asset.symbol}",
                url = searchRow?.explorerUrl ?: basicInfo?.officialWebsite ?: "",
                publishedAtMillis = publishedAtMillis,
                contentSnippet = snippetParts.joinToString(" "),
                eventType = MarketEventType.ECOSYSTEM_UPDATE,
                impactDirection = MarketImpactDirection.NEUTRAL,
                impactStrength = MarketImpactStrength.LOW,
                freshness = if (confidence == SourceTimestampConfidence.EXACT) {
                    resolveFreshness(publishedAtMillis)
                } else {
                    MarketEvidenceFreshness.UNKNOWN
                },
                sourceTimestampConfidence = confidence,
                relatedSymbols = listOfNotNull(asset.baseSymbol, asset.symbol),
                relevanceScore = 0.98,
                credibilityScore = 0.9
            )
        )
    }

    private fun buildLocalOverview(asset: AssetRef): MarketEvidence {
        val nowMillis = System.currentTimeMillis()
        val snippet = buildString {
            append("Local project profile for ")
            append(asset.displayName ?: asset.baseSymbol ?: asset.symbol)
            append(". Symbol ")
            append(asset.symbol)
            append('.')
            asset.exchange?.takeIf { it.isNotBlank() }?.let {
                append(" Exchange ").append(it).append('.')
            }
            asset.marketType?.takeIf { it.isNotBlank() }?.let {
                append(" Market type ").append(it).append('.')
            }
            asset.chainFamily?.takeIf { it.isNotBlank() }?.let {
                append(" Chain ").append(it).append('.')
            }
            asset.tokenAddress?.takeIf { it.isNotBlank() }?.let {
                append(" Address ").append(it.lowercase()).append('.')
            }
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
            credibilityScore = 0.85
        )
    }

    private suspend fun searchToken(
        credentials: OkxApiCredentials,
        chainIndex: String,
        tokenAddress: String
    ): OkxOnChainTokenRow? = withContext(Dispatchers.IO) {
        val requestPath = "/api/v6/dex/market/token/search?chains=$chainIndex&search=$tokenAddress"
        val timestamp = OkxOnChainRequestSigner.buildTimestamp()
        val signature = OkxOnChainRequestSigner.buildSignature(
            timestamp = timestamp,
            method = "GET",
            requestPath = requestPath,
            secret = credentials.secretKey
        )
        val response = okxOnChainApi.searchTokens(
            accessKey = credentials.apiKey,
            accessSign = signature,
            accessTimestamp = timestamp,
            accessPassphrase = credentials.passphrase,
            chains = chainIndex,
            search = tokenAddress
        )
        if (response.code != "0") return@withContext null
        response.data.firstOrNull {
            it.tokenContractAddress.equals(tokenAddress, ignoreCase = true)
        }
    }

    private suspend fun lookupBasicInfo(
        credentials: OkxApiCredentials,
        requestBody: List<OkxOnChainPriceRequest>
    ): OkxOnChainTokenBasicInfoRow? = withContext(Dispatchers.IO) {
        val body = requestJson.encodeToString(requestBody)
        val timestamp = OkxOnChainRequestSigner.buildTimestamp()
        val signature = OkxOnChainRequestSigner.buildSignature(
            timestamp = timestamp,
            method = "POST",
            requestPath = "/api/v6/dex/market/token/basic-info",
            secret = credentials.secretKey,
            body = body
        )
        val response = okxOnChainApi.getTokenBasicInfo(
            accessKey = credentials.apiKey,
            accessSign = signature,
            accessTimestamp = timestamp,
            accessPassphrase = credentials.passphrase,
            requestBody = requestBody
        )
        if (response.code != "0") return@withContext null
        response.data.firstOrNull()
    }

    private suspend fun lookupPriceInfo(
        credentials: OkxApiCredentials,
        requestBody: List<OkxOnChainPriceRequest>
    ): OkxOnChainTokenPriceInfoRow? = withContext(Dispatchers.IO) {
        val body = requestJson.encodeToString(requestBody)
        val timestamp = OkxOnChainRequestSigner.buildTimestamp()
        val signature = OkxOnChainRequestSigner.buildSignature(
            timestamp = timestamp,
            method = "POST",
            requestPath = "/api/v6/dex/market/price-info",
            secret = credentials.secretKey,
            body = body
        )
        val response = okxOnChainApi.getTokenPriceInfo(
            accessKey = credentials.apiKey,
            accessSign = signature,
            accessTimestamp = timestamp,
            accessPassphrase = credentials.passphrase,
            requestBody = requestBody
        )
        if (response.code != "0") return@withContext null
        response.data.firstOrNull()
    }

    private companion object {
        private const val SOURCE_ID = "project-info"
        private const val ONCHAIN_CACHE_TTL_MILLIS = 30 * 60 * 1000L
    }
}
