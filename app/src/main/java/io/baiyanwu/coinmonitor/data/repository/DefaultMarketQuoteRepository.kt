package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.network.BinanceAlphaApi
import io.baiyanwu.coinmonitor.data.network.BinanceApi
import io.baiyanwu.coinmonitor.data.network.BinanceFuturesApi
import io.baiyanwu.coinmonitor.data.network.BinanceTickerRow
import io.baiyanwu.coinmonitor.data.network.DexScreenerClient
import io.baiyanwu.coinmonitor.data.network.OkxApi
import io.baiyanwu.coinmonitor.data.network.parseAlphaTicker
import io.baiyanwu.coinmonitor.domain.model.AppPreferences
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OnchainDataProvider
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.MarketQuoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class DefaultMarketQuoteRepository(
    private val alphaApi: BinanceAlphaApi,
    private val binanceApi: BinanceApi,
    private val binanceFuturesApi: BinanceFuturesApi,
    private val okxApi: OkxApi,
    private val onchainRouter: OnchainProviderRouter
) : MarketQuoteRepository {
    constructor(
        alphaApi: BinanceAlphaApi,
        binanceApi: BinanceApi,
        binanceFuturesApi: BinanceFuturesApi,
        okxApi: OkxApi,
        onchainProviders: List<OnchainMarketProvider>,
        onchainProviderOrder: () -> List<OnchainDataProvider> = {
            AppPreferences.DEFAULT_ONCHAIN_PROVIDER_ORDER
        }
    ) : this(
        alphaApi,
        binanceApi,
        binanceFuturesApi,
        okxApi,
        OnchainProviderRouter(onchainProviders, onchainProviderOrder)
    )

    constructor(
        alphaApi: BinanceAlphaApi,
        binanceApi: BinanceApi,
        binanceFuturesApi: BinanceFuturesApi,
        okxApi: OkxApi,
        dexScreenerClient: DexScreenerClient
    ) : this(
        alphaApi,
        binanceApi,
        binanceFuturesApi,
        okxApi,
        OnchainProviderRouter(listOf(DexScreenerOnchainMarketProvider(dexScreenerClient)))
    )

    override suspend fun fetchQuotes(items: List<WatchItem>): List<MarketQuote> {
        if (items.isEmpty()) return emptyList()

        val alphaItems = items.filter {
            it.marketType == MarketType.CEX_SPOT && it.exchangeSource == ExchangeSource.BINANCE_ALPHA
        }
        val binanceItems = items.filter {
            it.marketType == MarketType.CEX_SPOT && it.exchangeSource == ExchangeSource.BINANCE
        }
        val binanceFuturesItems = items.filter {
            it.marketType == MarketType.CEX_USDT_FUTURES && it.exchangeSource == ExchangeSource.BINANCE
        }
        val okxItems = items.filter {
            it.marketType == MarketType.CEX_SPOT && it.exchangeSource == ExchangeSource.OKX
        }
        val okxFuturesItems = items.filter {
            it.marketType == MarketType.CEX_USDT_FUTURES && it.exchangeSource == ExchangeSource.OKX
        }
        val onChainItems = items.filter { it.marketType == MarketType.ONCHAIN_TOKEN }

        val alphaQuotes = fetchOrEmpty { fetchAlphaQuotes(alphaItems) }
        val binanceQuotes = fetchOrEmpty { fetchBinanceQuotes(binanceItems) }
        val binanceFuturesQuotes = fetchOrEmpty {
            fetchBinanceFuturesQuotes(binanceFuturesItems)
        }
        val okxQuotes = fetchOrEmpty { fetchOkxQuotes(okxItems) }
        val okxFuturesQuotes = fetchOrEmpty { fetchOkxQuotes(okxFuturesItems) }
        val onChainQuotes = onchainRouter.fetchQuotes(onChainItems)

        val quotes = (
            alphaQuotes +
                binanceQuotes +
                binanceFuturesQuotes +
                okxQuotes +
                okxFuturesQuotes +
                onChainQuotes
            ).associateBy { it.id }
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

    private suspend fun fetchBinanceFuturesQuotes(items: List<WatchItem>): List<MarketQuote> = coroutineScope {
        items.map { item ->
            async {
                val symbol = item.id.substringAfter("binance-futures:").uppercase()
                binanceFuturesApi.getTicker(symbol).toMarketQuote(item)
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun fetchOkxQuotes(items: List<WatchItem>): List<MarketQuote> = coroutineScope {
        items.map { item ->
            async {
                val instId = when (item.marketType) {
                    MarketType.CEX_USDT_FUTURES -> item.id.substringAfter("okx-futures:").uppercase()
                    else -> item.id.substringAfter("okx:").uppercase()
                }
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

    private fun BinanceTickerRow.toMarketQuote(item: WatchItem): MarketQuote? {
        val lastPrice = lastPrice.toDoubleOrNull() ?: return null
        val change = priceChangePercent.toDoubleOrNull() ?: return null
        return MarketQuote(
            id = item.id,
            symbol = item.symbol,
            name = item.name,
            priceUsd = lastPrice,
            change24hPercent = change
        )
    }

    private suspend fun <T> fetchOrEmpty(block: suspend () -> List<T>): List<T> {
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }

}
