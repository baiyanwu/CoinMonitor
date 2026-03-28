package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.network.BinanceAlphaApi
import io.baiyanwu.coinmonitor.data.network.BinanceApi
import io.baiyanwu.coinmonitor.data.network.OkxApi
import io.baiyanwu.coinmonitor.data.network.OkxOnChainApi
import io.baiyanwu.coinmonitor.data.network.OkxOnChainChainRegistry
import io.baiyanwu.coinmonitor.data.network.OkxOnChainRequestSigner
import io.baiyanwu.coinmonitor.data.network.OkxOnChainTokenRow
import io.baiyanwu.coinmonitor.data.network.parseAlphaExchangeInfo
import io.baiyanwu.coinmonitor.data.network.parseAlphaTokenList
import io.baiyanwu.coinmonitor.domain.model.ChainFamily
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OkxApiCredentials
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.MarketSearchRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder

class DefaultMarketSearchRepository(
    private val alphaApi: BinanceAlphaApi,
    private val binanceApi: BinanceApi,
    private val okxApi: OkxApi,
    private val okxOnChainApi: OkxOnChainApi,
    private val okxCredentialsProvider: suspend () -> OkxApiCredentials? = { null }
) : MarketSearchRepository {
    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry>()
    private val ttlMillis = 4 * 60 * 60 * 1000L

    override suspend fun search(keyword: String): List<WatchItem> = coroutineScope {
        val (chainFamilyFilter, normalizedQuery) = parseSearchScope(keyword)
        if (normalizedQuery.isBlank()) return@coroutineScope emptyList()

        val alphaDeferred = async { searchBinanceAlpha(normalizedQuery.uppercase()) }
        val binanceDeferred = async { searchBinance(normalizedQuery.uppercase()) }
        val okxDeferred = async { searchOkx(normalizedQuery.uppercase()) }
        val okxOnChainDeferred = async { searchOkxOnChain(normalizedQuery, chainFamilyFilter) }

        val merged = awaitAll(alphaDeferred, binanceDeferred, okxDeferred, okxOnChainDeferred)
            .flatten()
            .associateBy { it.id }
            .values
            .sortedWith(compareBy({ ExchangeSource.Companion.sortRank(it.exchangeSource) }, { it.symbol }, { it.name }))

        merged
    }

    override suspend fun searchExchange(keyword: String): List<WatchItem> = coroutineScope {
        val normalizedQuery = keyword.trim().uppercase()
        if (normalizedQuery.isBlank()) return@coroutineScope emptyList()

        awaitAll(
            async { searchBinanceAlpha(normalizedQuery) },
            async { searchBinance(normalizedQuery) },
            async { searchOkx(normalizedQuery) }
        )
            .flatten()
            .associateBy { it.id }
            .values
            .sortedWith(compareBy({ ExchangeSource.sortRank(it.exchangeSource) }, { it.symbol }, { it.name }))
    }

    override suspend fun searchOnchain(keyword: String, chainIndex: String): List<WatchItem> {
        val normalizedQuery = keyword.trim()
        if (normalizedQuery.isBlank()) return emptyList()
        if (chainIndex.isBlank()) return emptyList()
        return searchOkxOnChain(
            keyword = normalizedQuery,
            chainFamilyFilter = null,
            selectedChainIndex = chainIndex
        )
    }

    private suspend fun searchBinanceAlpha(keyword: String): List<WatchItem> {
        val tokenList = loadCache("alpha-token-list") { parseAlphaTokenList(alphaApi.getTokenList()) }
        val direct = tokenList.filterByKeyword(keyword)
        if (direct.isNotEmpty()) return direct

        val exchangeInfo = loadCache("alpha-exchange-info") { parseAlphaExchangeInfo(alphaApi.getExchangeInfo()) }
        return exchangeInfo.filterByKeyword(keyword)
    }

    private suspend fun searchBinance(keyword: String): List<WatchItem> {
        val universe = loadCache("binance-spot") {
            binanceApi.getExchangeInfo().symbols
                .filter { it.status == "TRADING" && it.quoteAsset == "USDT" }
                .map { row ->
                    WatchItem(
                        id = "binance:${row.symbol}",
                        symbol = "${row.baseAsset}/${row.quoteAsset}",
                        name = row.baseAsset,
                        exchangeSource = ExchangeSource.BINANCE,
                        addedAt = System.currentTimeMillis()
                    )
                }
        }
        return universe.filterByKeyword(keyword)
    }

    private suspend fun searchOkx(keyword: String): List<WatchItem> {
        val universe = loadCache("okx-spot") {
            okxApi.getSpotInstruments().data
                .filter { it.quoteCcy == "USDT" && it.state == "live" }
                .map { row ->
                    WatchItem(
                        id = "okx:${row.instId}",
                        symbol = "${row.baseCcy}/${row.quoteCcy}",
                        name = row.baseCcy,
                        exchangeSource = ExchangeSource.OKX,
                        addedAt = System.currentTimeMillis()
                    )
                }
        }
        return universe.filterByKeyword(keyword)
    }

    private suspend fun searchOkxOnChain(
        keyword: String,
        chainFamilyFilter: ChainFamily?,
        selectedChainIndex: String? = null
    ): List<WatchItem> {
        val credentials = okxCredentialsProvider()
        if (credentials == null || !credentials.enabled || !credentials.isReady) {
            // 链上接口依赖用户自己填写的凭证，未配置时直接返回空结果，避免影响交易所搜索。
            return emptyList()
        }

        val queryChains = selectedChainIndex
            ?.takeIf { it.isNotBlank() }
            ?.let { listOf(it) }
            ?: OkxOnChainChainRegistry.queryChains(
                requestedFamily = chainFamilyFilter,
                keyword = keyword
            )
        if (queryChains.isEmpty()) return emptyList()

        val chains = queryChains.joinToString(separator = ",")
        val encodedChains = URLEncoder.encode(chains, Charsets.UTF_8.name())
        val encodedKeyword = URLEncoder.encode(keyword, Charsets.UTF_8.name())
        val requestPath = "/api/v6/dex/market/token/search?chains=$encodedChains&search=$encodedKeyword"
        val timestamp = OkxOnChainRequestSigner.buildTimestamp()
        val signature = OkxOnChainRequestSigner.buildSignature(
            timestamp = timestamp,
            method = "GET",
            requestPath = requestPath,
            secret = credentials.secretKey
        )

        val response = runCatching {
            okxOnChainApi.searchTokens(
                accessKey = credentials.apiKey,
                accessSign = signature,
                accessTimestamp = timestamp,
                accessPassphrase = credentials.passphrase,
                chains = chains,
                search = keyword
            )
        }.getOrElse { error ->
            throw IllegalStateException("OKX 链上搜索请求失败，请检查网络或凭证配置。", error)
        }

        if (response.code != "0") {
            throw IllegalStateException(response.msg ?: "OKX 链上搜索失败，请检查凭证是否有效。")
        }
        return response.data
            .mapNotNull { it.toOnChainWatchItem() }
            .filter { item -> selectedChainIndex == null || item.chainIndex == selectedChainIndex }
            .filter { item -> chainFamilyFilter == null || item.chainFamily == chainFamilyFilter }
            .filterByKeyword(keyword)
    }

    private suspend fun loadCache(key: String, block: suspend () -> List<WatchItem>): List<WatchItem> {
        cacheMutex.withLock {
            val cached = cache[key]
            if (cached != null && System.currentTimeMillis() - cached.savedAt < ttlMillis) {
                return cached.items
            }
        }

        val fresh = block()
        cacheMutex.withLock {
            cache[key] = CacheEntry(savedAt = System.currentTimeMillis(), items = fresh)
        }
        return fresh
    }

    private data class CacheEntry(
        val savedAt: Long,
        val items: List<WatchItem>
    )
}

private fun List<WatchItem>.filterByKeyword(keyword: String): List<WatchItem> {
    val normalizedKeyword = keyword.uppercase()
    return filter { item ->
        val base = item.symbol.substringBefore("/").uppercase()
        val raw = item.id.substringAfter(":").uppercase()
        val displayName = item.name.uppercase()
        val tokenAddress = item.tokenAddress?.uppercase().orEmpty()
        base.contains(normalizedKeyword) ||
            raw.contains(normalizedKeyword) ||
            displayName.contains(normalizedKeyword) ||
            tokenAddress.contains(normalizedKeyword)
    }.take(80)
}

private fun parseSearchScope(keyword: String): Pair<ChainFamily?, String> {
    val raw = keyword.trim()
    if (raw.isBlank()) return null to ""
    return when {
        raw.startsWith("EVM:", ignoreCase = true) -> ChainFamily.EVM to raw.substringAfter(':').trim()
        raw.startsWith("SOL:", ignoreCase = true) -> ChainFamily.SOL to raw.substringAfter(':').trim()
        else -> null to raw
    }
}

private fun OkxOnChainTokenRow.toOnChainWatchItem(): WatchItem? {
    val address = tokenContractAddress?.takeIf { it.isNotBlank() } ?: return null
    val chain = chainIndex?.takeIf { it.isNotBlank() } ?: return null
    val symbolText = tokenSymbol?.takeIf { it.isNotBlank() } ?: address.take(6)
    val nameText = tokenName?.takeIf { it.isNotBlank() } ?: symbolText
    val family = OkxOnChainChainRegistry.resolveChainFamily(
        chainIndex = chain,
        tokenAddress = address
    ) ?: return null
    return WatchItem(
        id = "okx-onchain:${chain}:${address.lowercase()}",
        symbol = symbolText.uppercase(),
        name = nameText,
        exchangeSource = ExchangeSource.OKX,
        marketType = MarketType.ONCHAIN_TOKEN,
        chainFamily = family,
        chainIndex = chain,
        tokenAddress = address,
        iconUrl = tokenLogoUrl,
        addedAt = System.currentTimeMillis()
    )
}
