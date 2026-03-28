package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.network.BinanceAlphaApi
import io.baiyanwu.coinmonitor.data.network.BinanceApi
import io.baiyanwu.coinmonitor.data.network.OkxApi
import io.baiyanwu.coinmonitor.data.network.OkxOnChainApi
import io.baiyanwu.coinmonitor.data.network.OkxOnChainPriceRequest
import io.baiyanwu.coinmonitor.data.network.OkxOnChainRequestSigner
import io.baiyanwu.coinmonitor.data.network.parseAlphaTicker
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OkxApiCredentials
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.MarketQuoteRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DefaultMarketQuoteRepository(
    private val alphaApi: BinanceAlphaApi,
    private val binanceApi: BinanceApi,
    private val okxApi: OkxApi,
    private val okxOnChainApi: OkxOnChainApi,
    private val okxCredentialsProvider: suspend () -> OkxApiCredentials? = { null }
) : MarketQuoteRepository {
    private val requestJson = Json {
        explicitNulls = false
    }

    override suspend fun fetchQuotes(items: List<WatchItem>): List<MarketQuote> {
        if (items.isEmpty()) return emptyList()

        val alphaItems = items.filter {
            it.marketType == MarketType.CEX_SPOT && it.exchangeSource == ExchangeSource.BINANCE_ALPHA
        }
        val binanceItems = items.filter {
            it.marketType == MarketType.CEX_SPOT && it.exchangeSource == ExchangeSource.BINANCE
        }
        val okxItems = items.filter {
            it.marketType == MarketType.CEX_SPOT && it.exchangeSource == ExchangeSource.OKX
        }
        val onChainItems = items.filter { it.marketType == MarketType.ONCHAIN_TOKEN }

        val alphaQuotes = runCatching { fetchAlphaQuotes(alphaItems) }.getOrDefault(emptyList())
        val binanceQuotes = runCatching { fetchBinanceQuotes(binanceItems) }.getOrDefault(emptyList())
        val okxQuotes = runCatching { fetchOkxQuotes(okxItems) }.getOrDefault(emptyList())
        val onChainQuotes = runCatching { fetchOkxOnChainQuotes(onChainItems) }.getOrDefault(emptyList())

        val quotes = (alphaQuotes + binanceQuotes + okxQuotes + onChainQuotes).associateBy { it.id }
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

    private suspend fun fetchOkxOnChainQuotes(items: List<WatchItem>): List<MarketQuote> = coroutineScope {
        if (items.isEmpty()) return@coroutineScope emptyList()
        val credentials = okxCredentialsProvider()
        if (credentials == null || !credentials.enabled || !credentials.isReady) {
            return@coroutineScope emptyList()
        }

        val requestBody = items.mapNotNull { item ->
            val chainIndex = item.chainIndex?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val tokenAddress = item.tokenAddress?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            OkxOnChainPriceRequest(
                chainIndex = chainIndex,
                tokenContractAddress = tokenAddress
            )
        }
        if (requestBody.isEmpty()) return@coroutineScope emptyList()

        val requestBodyJson = requestJson.encodeToString(requestBody)
        val requestPath = "/api/v6/dex/market/price"
        val timestamp = OkxOnChainRequestSigner.buildTimestamp()
        val signature = OkxOnChainRequestSigner.buildSignature(
            timestamp = timestamp,
            method = "POST",
            requestPath = requestPath,
            secret = credentials.secretKey,
            body = requestBodyJson
        )

        val response = okxOnChainApi.getTokenPrices(
            accessKey = credentials.apiKey,
            accessSign = signature,
            accessTimestamp = timestamp,
            accessPassphrase = credentials.passphrase,
            requestBody = requestBody
        )
        if (response.code != "0") return@coroutineScope emptyList()

        val quoteRows = response.data.associateBy { row ->
            "${row.chainIndex.orEmpty()}:${row.tokenContractAddress.orEmpty().lowercase()}"
        }
        items.mapNotNull { item ->
            val chainIndex = item.chainIndex?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val tokenAddress = item.tokenAddress?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val row = quoteRows["$chainIndex:${tokenAddress.lowercase()}"] ?: return@mapNotNull null
            val price = row.price?.toDoubleOrNull() ?: return@mapNotNull null
            MarketQuote(
                id = item.id,
                symbol = item.symbol,
                name = item.name,
                priceUsd = price,
                change24hPercent = null
            )
        }
    }
}
