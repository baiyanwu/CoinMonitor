package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.network.BinanceAlphaApi
import io.baiyanwu.coinmonitor.data.network.BinanceApi
import io.baiyanwu.coinmonitor.data.network.BinanceFuturesApi
import io.baiyanwu.coinmonitor.data.network.DexScreenerClient
import io.baiyanwu.coinmonitor.data.network.DexScreenerPair
import io.baiyanwu.coinmonitor.data.network.DexScreenerPairSelector
import io.baiyanwu.coinmonitor.data.network.OkxApi
import io.baiyanwu.coinmonitor.data.network.parseAlphaExchangeInfo
import io.baiyanwu.coinmonitor.data.network.parseAlphaTokenList
import io.baiyanwu.coinmonitor.domain.model.ChainFamily
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OnchainChain
import io.baiyanwu.coinmonitor.domain.model.OnchainChainRegistry
import io.baiyanwu.coinmonitor.domain.model.OnchainPoolOption
import io.baiyanwu.coinmonitor.domain.model.PoolTokenSide
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.model.looksLikeOnchainAddress
import io.baiyanwu.coinmonitor.domain.model.normalizeOnchainAddress
import io.baiyanwu.coinmonitor.domain.repository.MarketSearchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PartialExchangeSearchException(
    val failedSourceCount: Int
) : IllegalStateException()

class DefaultMarketSearchRepository(
    private val alphaApi: BinanceAlphaApi,
    private val binanceApi: BinanceApi,
    private val binanceFuturesApi: BinanceFuturesApi,
    private val okxApi: OkxApi,
    private val dexScreenerClient: DexScreenerClient
) : MarketSearchRepository {
    private val supportedBinanceFuturesPerpetualContractTypes = setOf("PERPETUAL", "TRADIFI_PERPETUAL")
    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry>()
    private val ttlMillis = 4 * 60 * 60 * 1000L

    override suspend fun search(keyword: String): List<WatchItem> = coroutineScope {
        val (chainFamilyFilter, normalizedQuery) = parseSearchScope(keyword)
        if (normalizedQuery.isBlank()) return@coroutineScope emptyList()

        val alphaDeferred = async { searchBinanceAlpha(normalizedQuery.uppercase()) }
        val binanceDeferred = async { searchBinance(normalizedQuery.uppercase()) }
        val binanceFuturesDeferred = async { searchBinanceUsdtFutures(normalizedQuery.uppercase()) }
        val okxDeferred = async { searchOkx(normalizedQuery.uppercase()) }
        val okxFuturesDeferred = async { searchOkxUsdtFutures(normalizedQuery.uppercase()) }
        val onchainDeferred = async { searchPublicOnchain(normalizedQuery, chainFamilyFilter) }

        val merged = awaitAll(
            alphaDeferred,
            binanceDeferred,
            binanceFuturesDeferred,
            okxDeferred,
            okxFuturesDeferred,
            onchainDeferred
        )
            .flatten()
            .associateBy { it.id }
            .values
            .sortedWith(compareBy({ ExchangeSource.Companion.sortRank(it.exchangeSource) }, { it.symbol }, { it.name }))

        merged
    }

    override fun searchExchange(keyword: String): Flow<List<WatchItem>> = flow {
        val normalizedQuery = keyword.trim().uppercase()
        if (normalizedQuery.isBlank()) {
            emit(emptyList())
            return@flow
        }

        val attempts = supervisorScope {
            listOf<suspend () -> List<WatchItem>>(
                { searchBinanceAlpha(normalizedQuery) },
                { searchBinance(normalizedQuery) },
                { searchBinanceUsdtFutures(normalizedQuery) },
                { searchOkx(normalizedQuery) },
                { searchOkxUsdtFutures(normalizedQuery) }
            )
                .map { search -> async { captureSearchAttempt(search) } }
                .awaitAll()
        }

        val successfulBatches = attempts.mapNotNull { it.getOrNull() }
        val failures = attempts.mapNotNull { it.exceptionOrNull() }
        if (successfulBatches.isEmpty()) {
            throw failures.firstOrNull() ?: IllegalStateException("Exchange search failed")
        }

        val mergedResults = successfulBatches
            .flatten()
            .associateBy(WatchItem::id)
            .values
            .sortedWith(
                compareBy(
                    { ExchangeSource.sortRank(it.exchangeSource) },
                    { it.symbol },
                    { it.name }
                )
            )
        emit(mergedResults)

        if (failures.isNotEmpty()) {
            throw PartialExchangeSearchException(failures.size)
        }
    }

    override suspend fun searchOnchain(keyword: String): List<WatchItem> {
        val normalizedQuery = keyword.trim()
        if (normalizedQuery.isBlank()) return emptyList()

        val addressFamily = when {
            looksLikeOnchainAddress(ChainFamily.EVM, normalizedQuery) -> ChainFamily.EVM
            looksLikeOnchainAddress(ChainFamily.SOL, normalizedQuery) -> ChainFamily.SOL
            else -> null
        }
        return searchPublicOnchain(
            keyword = normalizedQuery,
            chainFamilyFilter = addressFamily
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

    private suspend fun searchBinanceUsdtFutures(keyword: String): List<WatchItem> {
        val universe = loadCache("binance-usdt-futures") {
            binanceFuturesApi.getExchangeInfo().symbols
                .filter {
                    it.status == "TRADING" &&
                        it.quoteAsset == "USDT" &&
                        it.marginAsset == "USDT" &&
                        it.contractType in supportedBinanceFuturesPerpetualContractTypes
                }
                .map { row ->
                    WatchItem(
                        id = "binance-futures:${row.symbol}",
                        symbol = row.symbol.uppercase(),
                        name = row.baseAsset,
                        exchangeSource = ExchangeSource.BINANCE,
                        marketType = MarketType.CEX_USDT_FUTURES,
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

    private suspend fun searchOkxUsdtFutures(keyword: String): List<WatchItem> {
        val universe = loadCache("okx-usdt-futures") {
            okxApi.getInstruments(instType = "SWAP").data
                .filter {
                    it.state == "live" &&
                        it.settleCcy == "USDT" &&
                        it.instId.endsWith("-USDT-SWAP")
                }
                .map { row ->
                    val baseAsset = row.instId.substringBefore("-").uppercase()
                    WatchItem(
                        id = "okx-futures:${row.instId}",
                        symbol = row.instId.removeSuffix("-SWAP").replace("-", "").uppercase(),
                        name = baseAsset,
                        exchangeSource = ExchangeSource.OKX,
                        marketType = MarketType.CEX_USDT_FUTURES,
                        addedAt = System.currentTimeMillis()
                    )
                }
        }
        return universe.filterByKeyword(keyword)
    }

    private suspend fun captureSearchAttempt(
        block: suspend () -> List<WatchItem>
    ): Result<List<WatchItem>> {
        var lastError: Exception? = null
        repeat(EXCHANGE_SEARCH_MAX_ATTEMPTS) { attempt ->
            try {
                return Result.success(block())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                lastError = error
                if (attempt < EXCHANGE_SEARCH_MAX_ATTEMPTS - 1) {
                    delay(EXCHANGE_SEARCH_RETRY_DELAY_MILLIS)
                }
            }
        }
        return Result.failure(lastError ?: IllegalStateException("Exchange search failed"))
    }

    private suspend fun searchPublicOnchain(
        keyword: String,
        chainFamilyFilter: ChainFamily? = null
    ): List<WatchItem> {
        val pairs = dexScreenerClient.searchPairs(keyword)
        val chains = OnchainChainRegistry.entries.filter { chain ->
            chainFamilyFilter == null || chain.family == chainFamilyFilter
        }
        return chains.flatMap { chain ->
            val chainPairs = pairs.filter { it.chainId.equals(chain.dexScreenerId, ignoreCase = true) }
            val addresses = chainPairs.flatMap { pair ->
                DexScreenerPairSelector.matchingTokenAddresses(pair, keyword, chain.family)
            }
            addresses
                .distinctBy { normalizeOnchainAddress(chain.family, it) }
                .mapNotNull { address ->
                    val candidates = DexScreenerPairSelector.candidates(
                        pairs = chainPairs,
                        tokenAddress = address,
                        family = chain.family
                    )
                    candidates.firstOrNull()?.toOnchainWatchItem(
                        chain = chain,
                        candidates = candidates
                    )
                }
        }
            .distinctBy(WatchItem::semanticKey)
            .sortedWith(compareBy({ it.symbol }, { it.name }))
            .take(80)
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

    private companion object {
        const val EXCHANGE_SEARCH_MAX_ATTEMPTS = 2
        const val EXCHANGE_SEARCH_RETRY_DELAY_MILLIS = 150L
    }
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

private fun io.baiyanwu.coinmonitor.data.network.SelectedDexPair.toOnchainWatchItem(
    chain: OnchainChain,
    candidates: List<io.baiyanwu.coinmonitor.data.network.SelectedDexPair>
): WatchItem {
    val normalizedAddress = normalizeOnchainAddress(chain.family, tokenAddress)
    val poolOptions = candidates.map { candidate -> candidate.toPoolOption(chain) }
    val selectedPool = poolOptions.firstOrNull()
    return WatchItem(
        id = "onchain:${chain.chainIndex}:$normalizedAddress",
        symbol = tokenSymbol.uppercase(),
        name = tokenName,
        exchangeSource = ExchangeSource.ONCHAIN,
        marketType = MarketType.ONCHAIN_TOKEN,
        chainFamily = chain.family,
        chainIndex = chain.chainIndex,
        tokenAddress = normalizedAddress,
        poolAddress = normalizeOnchainAddress(chain.family, pair.pairAddress),
        poolTokenSide = tokenSide,
        iconUrl = pair.info?.imageUrl.takeIf { tokenSide == io.baiyanwu.coinmonitor.domain.model.PoolTokenSide.BASE },
        lastPrice = priceUsd,
        change24hPercent = change24hPercent,
        lastUpdatedAt = System.currentTimeMillis(),
        addedAt = System.currentTimeMillis(),
        selectedPool = selectedPool,
        poolOptions = poolOptions
    )
}

private fun io.baiyanwu.coinmonitor.data.network.SelectedDexPair.toPoolOption(
    chain: OnchainChain
): OnchainPoolOption {
    val counterToken = if (tokenSide == PoolTokenSide.BASE) pair.quoteToken else pair.baseToken
    val targetLabel = tokenSymbol.ifBlank { tokenAddress.take(8) }.uppercase()
    val counterLabel = counterToken.symbol.ifBlank { counterToken.address.take(8) }.uppercase()
    return OnchainPoolOption(
        poolAddress = normalizeOnchainAddress(chain.family, pair.pairAddress),
        tokenSide = tokenSide,
        pairLabel = "$targetLabel / $counterLabel",
        dexId = pair.dexId,
        labels = pair.labels.orEmpty(),
        liquidityUsd = pair.liquidity?.usd ?: 0.0,
        volume24hUsd = pair.volume["h24"],
        priceUsd = priceUsd,
        change24hPercent = change24hPercent,
        poolUrl = pair.url
    )
}
