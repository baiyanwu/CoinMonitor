package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.network.BinanceAlphaApi
import io.baiyanwu.coinmonitor.data.network.BinanceApi
import io.baiyanwu.coinmonitor.data.network.OkxApi
import io.baiyanwu.coinmonitor.data.network.OkxOnChainApi
import io.baiyanwu.coinmonitor.data.network.OkxOnChainRequestSigner
import io.baiyanwu.coinmonitor.data.network.isAlphaSuccess
import io.baiyanwu.coinmonitor.domain.model.CandleEntry
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.KlineInterval
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OkxApiCredentials
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.MarketKlineRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class DefaultMarketKlineRepository(
    private val alphaApi: BinanceAlphaApi,
    private val binanceApi: BinanceApi,
    private val okxApi: OkxApi,
    private val okxOnChainApi: OkxOnChainApi,
    private val okxCredentialsProvider: suspend () -> OkxApiCredentials? = { null }
) : MarketKlineRepository {
    override suspend fun fetchCandles(
        item: WatchItem,
        interval: KlineInterval,
        limit: Int
    ): List<CandleEntry> {
        return when {
            item.marketType == MarketType.ONCHAIN_TOKEN -> fetchOnchainCandles(item, interval, limit)
            item.exchangeSource == ExchangeSource.BINANCE -> fetchBinanceCandles(item, interval, limit)
            item.exchangeSource == ExchangeSource.BINANCE_ALPHA -> fetchAlphaCandles(item, interval, limit)
            else -> fetchOkxCandles(item, interval, limit)
        }.sortedBy { it.openTimeMillis }
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
        val instId = item.id.substringAfter("okx:").uppercase()
        val response = okxApi.getCandles(instId, interval.okxValue, limit)
        if (response.code != "0") return emptyList()
        return response.data.mapNotNull(::parseOkxRow)
    }

    private suspend fun fetchOnchainCandles(
        item: WatchItem,
        interval: KlineInterval,
        limit: Int
    ): List<CandleEntry> {
        val credentials = okxCredentialsProvider()
        if (credentials == null || !credentials.enabled || !credentials.isReady) return emptyList()
        val chainIndex = item.chainIndex?.takeIf { it.isNotBlank() } ?: return emptyList()
        val tokenAddress = item.tokenAddress?.takeIf { it.isNotBlank() } ?: return emptyList()
        val requestPath = buildString {
            append("/api/v6/dex/market/candles")
            append("?chainIndex=").append(chainIndex)
            append("&tokenContractAddress=").append(normalizeTokenAddress(item))
            append("&bar=").append(interval.okxValue)
            append("&limit=").append(limit)
        }
        val timestamp = OkxOnChainRequestSigner.buildTimestamp()
        val signature = OkxOnChainRequestSigner.buildSignature(
            timestamp = timestamp,
            method = "GET",
            requestPath = requestPath,
            secret = credentials.secretKey
        )
        val response = okxOnChainApi.getCandles(
            accessKey = credentials.apiKey,
            accessSign = signature,
            accessTimestamp = timestamp,
            accessPassphrase = credentials.passphrase,
            chainIndex = chainIndex,
            tokenContractAddress = normalizeTokenAddress(item),
            bar = interval.okxValue,
            limit = limit
        )
        if (response.code != "0") return emptyList()
        return response.data.mapNotNull(::parseOkxRow)
    }

    private fun normalizeTokenAddress(item: WatchItem): String {
        val address = item.tokenAddress.orEmpty()
        return if (item.chainFamily?.name == "SOL") address else address.lowercase()
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
