package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.network.BinanceAlphaApi
import io.baiyanwu.coinmonitor.data.network.BinanceApi
import io.baiyanwu.coinmonitor.data.network.OkxApi
import io.baiyanwu.coinmonitor.data.network.parseAlphaExchangeInfo
import io.baiyanwu.coinmonitor.data.network.parseAlphaTokenList
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.MarketSearchRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultMarketSearchRepository(
    private val alphaApi: BinanceAlphaApi,
    private val binanceApi: BinanceApi,
    private val okxApi: OkxApi
) : MarketSearchRepository {
    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry>()
    private val ttlMillis = 4 * 60 * 60 * 1000L

    override suspend fun search(keyword: String): List<WatchItem> = coroutineScope {
        val query = keyword.trim().uppercase()
        if (query.isBlank()) return@coroutineScope emptyList()

        val alphaDeferred = async { searchBinanceAlpha(query) }
        val binanceDeferred = async { searchBinance(query) }
        val okxDeferred = async { searchOkx(query) }

        val merged = awaitAll(alphaDeferred, binanceDeferred, okxDeferred)
            .flatten()
            .associateBy { it.id }
            .values
            .sortedWith(compareBy({ ExchangeSource.Companion.sortRank(it.exchangeSource) }, { it.symbol }, { it.name }))

        merged
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
    return filter { item ->
        val base = item.symbol.substringBefore("/").uppercase()
        val raw = item.id.substringAfter(":").uppercase()
        val displayName = item.name.uppercase()
        base.contains(keyword) || raw.contains(keyword) || displayName.contains(keyword)
    }.take(80)
}
