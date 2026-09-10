package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.network.BinanceAlphaApi
import io.baiyanwu.coinmonitor.data.network.BinanceApi
import io.baiyanwu.coinmonitor.data.network.BinanceFuturesApi
import io.baiyanwu.coinmonitor.data.network.BinanceTickerRow
import io.baiyanwu.coinmonitor.data.network.DexScreenerClient
import io.baiyanwu.coinmonitor.data.network.DexScreenerPairSelector
import io.baiyanwu.coinmonitor.data.network.OkxApi
import io.baiyanwu.coinmonitor.data.network.PinnedPoolSelectionPolicy
import io.baiyanwu.coinmonitor.data.network.parseAlphaTicker
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OnchainChainRegistry
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.model.inferOnchainChainFamily
import io.baiyanwu.coinmonitor.domain.model.normalizeOnchainAddress
import io.baiyanwu.coinmonitor.domain.model.onchainAddressesEqual
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
    private val dexScreenerClient: DexScreenerClient
) : MarketQuoteRepository {
    private val pinnedPoolSelectionPolicy = PinnedPoolSelectionPolicy()

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
        val onChainQuotes = fetchDexScreenerQuotes(onChainItems)

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

    private suspend fun fetchDexScreenerQuotes(items: List<WatchItem>): List<MarketQuote> = coroutineScope {
        if (items.isEmpty()) return@coroutineScope emptyList()
        items.groupBy { item ->
            OnchainChainRegistry.resolve(
                chainIndexOrDexScreenerId = item.chainIndex,
                family = item.chainFamily ?: inferOnchainChainFamily(
                    dexScreenerId = item.chainIndex,
                    addresses = listOfNotNull(item.tokenAddress)
                )
            )
        }
            .filterKeys { it != null }
            .map { (chainOrNull, chainItems) ->
                async {
                    val chain = chainOrNull ?: return@async emptyList()
                    chainItems.chunked(MAX_TOKEN_ADDRESSES_PER_REQUEST).flatMap { chunk ->
                        try {
                            val addresses = chunk.mapNotNull { item ->
                                item.tokenAddress?.takeIf(String::isNotBlank)
                                    ?.let { normalizeOnchainAddress(chain.family, it) }
                            }.distinct()
                            if (addresses.isEmpty()) return@flatMap emptyList()
                            val pairs = dexScreenerClient.getTokenPairsBatch(
                                chainId = chain.dexScreenerId,
                                tokenAddresses = addresses
                            ).filter { pair ->
                                pair.chainId.equals(chain.dexScreenerId, ignoreCase = true)
                            }
                            chunk.mapNotNull { item ->
                                val tokenAddress = item.tokenAddress ?: return@mapNotNull null
                                val selected = pinnedPoolSelectionPolicy.select(
                                    itemId = item.id,
                                    pairs = pairs,
                                    tokenAddress = tokenAddress,
                                    family = chain.family,
                                    preferredPoolAddress = item.poolAddress
                                ) ?: return@mapNotNull null
                                val selectedPoolAddress = normalizeOnchainAddress(
                                    chain.family,
                                    selected.pair.pairAddress
                                )
                                val bindingChanged = item.poolAddress == null ||
                                    !onchainAddressesEqual(
                                        chain.family,
                                        item.poolAddress,
                                        selectedPoolAddress
                                    ) ||
                                    item.poolTokenSide != selected.tokenSide
                                MarketQuote(
                                    id = item.id,
                                    symbol = selected.pairLabel,
                                    name = item.name,
                                    priceUsd = selected.priceUsd,
                                    change24hPercent = selected.change24hPercent,
                                    marketCap = selected.marketCap,
                                    poolAddress = selectedPoolAddress,
                                    poolTokenSide = selected.tokenSide,
                                    requestedPoolAddress = item.poolAddress,
                                    requestedPoolTokenSide = item.poolTokenSide,
                                    resetTrend = bindingChanged
                                )
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
            }
            .awaitAll()
            .flatten()
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

    private companion object {
        const val MAX_TOKEN_ADDRESSES_PER_REQUEST = 30
    }
}
