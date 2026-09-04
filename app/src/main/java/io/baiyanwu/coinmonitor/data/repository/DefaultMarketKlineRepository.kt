package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.network.BinanceAlphaApi
import io.baiyanwu.coinmonitor.data.network.BinanceApi
import io.baiyanwu.coinmonitor.data.network.BinanceFuturesApi
import io.baiyanwu.coinmonitor.data.network.DexScreenerClient
import io.baiyanwu.coinmonitor.data.network.DexScreenerPairSelector
import io.baiyanwu.coinmonitor.data.network.GeckoTerminalClient
import io.baiyanwu.coinmonitor.data.network.OkxApi
import io.baiyanwu.coinmonitor.data.network.isAlphaSuccess
import io.baiyanwu.coinmonitor.domain.model.CandleEntry
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.KlineInterval
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OnchainChainRegistry
import io.baiyanwu.coinmonitor.domain.model.PoolTokenSide
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.model.inferOnchainChainFamily
import io.baiyanwu.coinmonitor.domain.model.normalizeOnchainAddress
import io.baiyanwu.coinmonitor.domain.model.toGeckoTerminalInterval
import io.baiyanwu.coinmonitor.domain.repository.MarketKlineRepository
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.ConcurrentHashMap

class DefaultMarketKlineRepository(
    private val alphaApi: BinanceAlphaApi,
    private val binanceApi: BinanceApi,
    private val binanceFuturesApi: BinanceFuturesApi,
    private val okxApi: OkxApi,
    private val dexScreenerClient: DexScreenerClient,
    private val geckoTerminalClient: GeckoTerminalClient,
    private val watchlistRepository: WatchlistRepository
) : MarketKlineRepository {
    private val onchainCache = ConcurrentHashMap<OnchainCacheKey, OnchainCacheEntry>()

    override suspend fun fetchCandles(
        item: WatchItem,
        interval: KlineInterval,
        limit: Int
    ): List<CandleEntry> {
        return when {
            item.marketType == MarketType.ONCHAIN_TOKEN -> fetchOnchainCandles(item, interval, limit)
            item.marketType == MarketType.CEX_USDT_FUTURES &&
                item.exchangeSource == ExchangeSource.BINANCE -> fetchBinanceFuturesCandles(item, interval, limit)
            item.exchangeSource == ExchangeSource.BINANCE -> fetchBinanceCandles(item, interval, limit)
            item.exchangeSource == ExchangeSource.BINANCE_ALPHA -> fetchAlphaCandles(item, interval, limit)
            else -> fetchOkxCandles(item, interval, limit)
        }.sortedBy { it.openTimeMillis }
    }

    private suspend fun fetchBinanceFuturesCandles(
        item: WatchItem,
        interval: KlineInterval,
        limit: Int
    ): List<CandleEntry> {
        val symbol = item.id.substringAfter("binance-futures:").uppercase()
        return binanceFuturesApi.getKlines(symbol, interval.binanceValue, limit)
            .mapNotNull { element ->
                parseBinanceRow(element as? JsonArray ?: return@mapNotNull null)
            }
    }

    private suspend fun fetchBinanceCandles(
        item: WatchItem,
        interval: KlineInterval,
        limit: Int
    ): List<CandleEntry> {
        val symbol = item.id.substringAfter("binance:").uppercase()
        return binanceApi.getKlines(symbol, interval.binanceValue, limit)
            .mapNotNull { element ->
                parseBinanceRow(element as? JsonArray ?: return@mapNotNull null)
            }
    }

    private suspend fun fetchAlphaCandles(
        item: WatchItem,
        interval: KlineInterval,
        limit: Int
    ): List<CandleEntry> {
        val symbol = item.id.substringAfter("binance-alpha:").uppercase()
        val response = alphaApi.getKlines(symbol, interval.binanceValue, limit)
        if (!response.isAlphaSuccess()) return emptyList()
        val rows = response.findFirstArray("data") ?: return emptyList()
        return rows.mapNotNull { element ->
            parseBinanceRow(element as? JsonArray ?: return@mapNotNull null)
        }
    }

    private suspend fun fetchOkxCandles(
        item: WatchItem,
        interval: KlineInterval,
        limit: Int
    ): List<CandleEntry> {
        val instId = when (item.marketType) {
            MarketType.CEX_USDT_FUTURES -> item.id.substringAfter("okx-futures:")
            else -> item.id.substringAfter("okx:")
        }.uppercase()
        val response = okxApi.getCandles(instId, interval.okxValue, limit)
        if (response.code != "0") return emptyList()
        return response.data.mapNotNull(::parseOkxRow)
    }

    private suspend fun fetchOnchainCandles(
        item: WatchItem,
        interval: KlineInterval,
        limit: Int
    ): List<CandleEntry> {
        val chain = OnchainChainRegistry.resolve(
            chainIndexOrDexScreenerId = item.chainIndex,
            family = item.chainFamily ?: inferOnchainChainFamily(
                dexScreenerId = item.chainIndex,
                addresses = listOfNotNull(item.tokenAddress)
            )
        ) ?: throw IllegalArgumentException("链上标的缺少网络标识")
        val tokenAddress = item.tokenAddress?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("链上标的缺少合约地址")
        val binding = if (!item.poolAddress.isNullOrBlank() && item.poolTokenSide != null) {
            PoolBinding(item.poolAddress, item.poolTokenSide)
        } else {
            val pairs = dexScreenerClient.getTokenPairs(chain.dexScreenerId, tokenAddress)
            val selected = DexScreenerPairSelector.select(pairs, tokenAddress, chain.family)
                ?: throw IllegalStateException("DexScreener 未找到可用流动池")
            val selectedPoolAddress = normalizeOnchainAddress(chain.family, selected.pair.pairAddress)
            watchlistRepository.updateOnchainPoolBinding(
                id = item.id,
                poolAddress = selectedPoolAddress,
                side = selected.tokenSide
            )
            PoolBinding(selectedPoolAddress, selected.tokenSide)
        }
        val mapping = interval.toGeckoTerminalInterval()
        val outputLimit = limit.coerceAtLeast(1)
        val cacheKey = OnchainCacheKey(
            network = chain.geckoTerminalId,
            poolAddress = binding.poolAddress,
            tokenSide = binding.tokenSide,
            interval = interval,
            limit = outputLimit
        )
        onchainCache[cacheKey]
            ?.takeIf { System.currentTimeMillis() - it.savedAtMillis < ONCHAIN_CACHE_TTL_MILLIS }
            ?.let { return it.candles }

        val sourceCandles = fetchGeckoSourceCandles(
            network = chain.geckoTerminalId,
            poolAddress = binding.poolAddress,
            timeframe = mapping.timeframe,
            aggregate = mapping.aggregate,
            tokenSide = binding.tokenSide,
            targetCount = resolveGeckoSourceCount(outputLimit, mapping.localAggregationDays)
        )
        val candles = if (mapping.localAggregationDays > 1) {
            aggregateDailyCandles(sourceCandles, mapping.localAggregationDays)
        } else {
            sourceCandles
        }.takeLast(outputLimit)
        if (candles.isEmpty()) throw IllegalStateException("GeckoTerminal 暂无该池子的 K 线数据")
        onchainCache[cacheKey] = OnchainCacheEntry(System.currentTimeMillis(), candles)
        return candles
    }

    private suspend fun fetchGeckoSourceCandles(
        network: String,
        poolAddress: String,
        timeframe: String,
        aggregate: Int,
        tokenSide: PoolTokenSide,
        targetCount: Int
    ): List<CandleEntry> {
        val candlesByOpenTime = linkedMapOf<Long, CandleEntry>()
        var beforeTimestamp: Long? = null
        while (candlesByOpenTime.size < targetCount) {
            val pageLimit = (targetCount - candlesByOpenTime.size)
                .coerceAtMost(MAX_GECKO_CANDLES_PER_REQUEST)
            val response = geckoTerminalClient.getPoolOhlcv(
                network = network,
                poolAddress = poolAddress,
                timeframe = timeframe,
                aggregate = aggregate,
                limit = pageLimit,
                tokenSide = tokenSide,
                beforeTimestamp = beforeTimestamp
            )
            val page = response.data.attributes.ohlcvList
                .mapNotNull(::parseGeckoTerminalRow)
            if (page.isEmpty()) break

            val previousSize = candlesByOpenTime.size
            page.forEach { candle -> candlesByOpenTime[candle.openTimeMillis] = candle }
            val earliestTimestamp = page.minOf(CandleEntry::openTimeMillis) / 1_000L
            if (candlesByOpenTime.size == previousSize ||
                page.size < pageLimit ||
                earliestTimestamp == beforeTimestamp
            ) {
                break
            }
            beforeTimestamp = earliestTimestamp
        }
        return candlesByOpenTime.values.sortedBy(CandleEntry::openTimeMillis)
    }

    private fun parseBinanceRow(row: List<String>): CandleEntry? {
        if (row.size < 6) return null
        return CandleEntry(
            openTimeMillis = row[0].toLongOrNull() ?: return null,
            open = row[1].toDoubleOrNull() ?: return null,
            high = row[2].toDoubleOrNull() ?: return null,
            low = row[3].toDoubleOrNull() ?: return null,
            close = row[4].toDoubleOrNull() ?: return null,
            volume = row[5].toDoubleOrNull() ?: return null
        )
    }

    private fun parseBinanceRow(row: JsonArray): CandleEntry? {
        return parseBinanceRow(
            row.mapNotNull { element ->
                when (element) {
                    is JsonPrimitive -> element.content
                    else -> null
                }
            }
        )
    }

    private fun parseOkxRow(row: List<String>): CandleEntry? {
        if (row.size < 6) return null
        return CandleEntry(
            openTimeMillis = row[0].toLongOrNull() ?: return null,
            open = row[1].toDoubleOrNull() ?: return null,
            high = row[2].toDoubleOrNull() ?: return null,
            low = row[3].toDoubleOrNull() ?: return null,
            close = row[4].toDoubleOrNull() ?: return null,
            volume = row[5].toDoubleOrNull() ?: 0.0,
            isConfirmed = row.getOrNull(8) != "0"
        )
    }

    private fun parseGeckoTerminalRow(row: List<Double>): CandleEntry? {
        if (row.size < 6) return null
        return CandleEntry(
            openTimeMillis = row[0].toLong() * 1_000L,
            open = row[1],
            high = row[2],
            low = row[3],
            close = row[4],
            volume = row[5]
        ).takeIf {
            it.open > 0.0 && it.high > 0.0 && it.low > 0.0 && it.close > 0.0
        }
    }

    private fun aggregateDailyCandles(
        source: List<CandleEntry>,
        periodDays: Int
    ): List<CandleEntry> {
        require(periodDays > 1)
        return source
            .groupBy { candle -> resolveLongPeriodStart(candle.openTimeMillis, periodDays) }
            .toSortedMap()
            .map { (periodStart, rows) ->
                CandleEntry(
                    openTimeMillis = periodStart,
                    open = rows.first().open,
                    high = rows.maxOf(CandleEntry::high),
                    low = rows.minOf(CandleEntry::low),
                    close = rows.last().close,
                    volume = rows.sumOf(CandleEntry::volume),
                    isConfirmed = rows.all(CandleEntry::isConfirmed)
                )
            }
    }

    private fun resolveLongPeriodStart(openTimeMillis: Long, periodDays: Int): Long {
        val periodMillis = periodDays * MILLIS_PER_DAY
        val anchorOffsetMillis = if (periodDays == DAYS_PER_WEEK) {
            MONDAY_ANCHOR_OFFSET_MILLIS
        } else {
            0L
        }
        return Math.floorDiv(openTimeMillis - anchorOffsetMillis, periodMillis) * periodMillis +
            anchorOffsetMillis
    }

    private fun resolveGeckoSourceCount(outputLimit: Int, localAggregationDays: Int): Int {
        return (outputLimit.toLong() * localAggregationDays)
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()
    }

    private data class PoolBinding(val poolAddress: String, val tokenSide: PoolTokenSide)
    private data class OnchainCacheKey(
        val network: String,
        val poolAddress: String,
        val tokenSide: PoolTokenSide,
        val interval: KlineInterval,
        val limit: Int
    )
    private data class OnchainCacheEntry(val savedAtMillis: Long, val candles: List<CandleEntry>)

    private companion object {
        const val ONCHAIN_CACHE_TTL_MILLIS = 30_000L
        const val MAX_GECKO_CANDLES_PER_REQUEST = 1_000
        const val MILLIS_PER_DAY = 86_400_000L
        const val DAYS_PER_WEEK = 7
        const val MONDAY_ANCHOR_OFFSET_MILLIS = -3L * MILLIS_PER_DAY
    }
}

private fun JsonObject.findFirstArray(key: String): JsonArray? {
    val element = this[key] ?: return null
    return when (element) {
        is JsonArray -> {
            val first = element.firstOrNull()
            if (first is JsonArray) {
                element
            } else {
                first as? JsonArray
            }
        }

        is JsonObject -> element[key] as? JsonArray
        else -> null
    }
}
