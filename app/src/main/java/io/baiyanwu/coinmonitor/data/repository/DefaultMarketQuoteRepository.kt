package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.network.BinanceAlphaApi
import io.baiyanwu.coinmonitor.data.network.BinanceApi
import io.baiyanwu.coinmonitor.data.network.OkxApi
import io.baiyanwu.coinmonitor.data.network.parseAlphaTicker
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.MarketQuoteRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.collections.plus

class DefaultMarketQuoteRepository(
    private val alphaApi: BinanceAlphaApi,
    private val binanceApi: BinanceApi,
    private val okxApi: OkxApi
) : MarketQuoteRepository {
    override suspend fun fetchQuotes(items: List<WatchItem>): List<MarketQuote> {
        if (items.isEmpty()) return emptyList()

        val alphaItems = items.filter { it.exchangeSource == ExchangeSource.BINANCE_ALPHA }
        val binanceItems = items.filter { it.exchangeSource == ExchangeSource.BINANCE }
        val okxItems = items.filter { it.exchangeSource == ExchangeSource.OKX }

        val alphaQuotes = runCatching { fetchAlphaQuotes(alphaItems) }.getOrDefault(emptyList())
        val binanceQuotes = runCatching { fetchBinanceQuotes(binanceItems) }.getOrDefault(emptyList())
        val okxQuotes = runCatching { fetchOkxQuotes(okxItems) }.getOrDefault(emptyList())

        val quotes = (alphaQuotes + binanceQuotes + okxQuotes).associateBy { it.id }
        return items.mapNotNull { quotes[it.id] }
    }

    private suspend fun fetchAlphaQuotes(items: List<WatchItem>): List<MarketQuote> = coroutineScope {
        items.map { item ->
            async {
                val symbol = item.id.substringAfter("binance-alpha:").uppercase()
                parseAlphaTicker(alphaApi.getTicker(symbol), item)
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun fetchBinanceQuotes(items: List<WatchItem>): List<MarketQuote> {
        if (items.isEmpty()) return emptyList()

        val symbolMap = items.associateBy { it.id.substringAfter("binance:").uppercase() }
        val symbolJson = symbolMap.keys.sorted().joinToString(
            prefix = "[",
            postfix = "]",
            separator = ","
        ) { "\"$it\"" }

        return binanceApi.getTickers(symbolJson).mapNotNull { row ->
            val item = symbolMap[row.symbol] ?: return@mapNotNull null
            val lastPrice = row.lastPrice.toDoubleOrNull() ?: return@mapNotNull null
            val change = row.priceChangePercent.toDoubleOrNull() ?: return@mapNotNull null
            MarketQuote(
                id = item.id,
                symbol = item.symbol,
                name = item.name,
                priceUsd = lastPrice,
                change24hPercent = change
            )
        }
    }

    private suspend fun fetchOkxQuotes(items: List<WatchItem>): List<MarketQuote> = coroutineScope {
        items.map { item ->
            async {
                val instId = item.id.substringAfter("okx:").uppercase()
                val response = okxApi.getTicker(instId)
                if (response.code != "0") return@async null
                val row = response.data.firstOrNull() ?: return@async null
                val last = row.last.toDoubleOrNull() ?: return@async null
                val open24h = row.open24h.toDoubleOrNull() ?: return@async null
                if (open24h <= 0) return@async null
                val changePercent = ((last - open24h) / open24h) * 100
                MarketQuote(
                    id = item.id,
                    symbol = item.symbol,
                    name = item.name,
                    priceUsd = last,
                    change24hPercent = changePercent
                )
            }
        }.awaitAll().filterNotNull()
    }
}
