package io.coinbar.tokenmonitor.data.network

import io.coinbar.tokenmonitor.domain.model.ExchangeSource
import io.coinbar.tokenmonitor.domain.model.MarketQuote
import io.coinbar.tokenmonitor.domain.model.WatchItem
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal fun JsonElement?.stringValue(): String? {
    return (this as? JsonPrimitive)?.contentOrNull
}

internal fun JsonElement?.doubleValue(): Double? {
    return stringValue()?.toDoubleOrNull()
}

internal fun parseAlphaTokenList(body: JsonObject): List<WatchItem> {
    if (!body.isAlphaSuccess()) return emptyList()

    val data = body["data"]?.jsonArray ?: return emptyList()
    return buildList {
        data.forEach { element ->
            val row = element.jsonObject
            val alphaId = row["alphaId"].stringValue()?.uppercase() ?: return@forEach
            val symbolValue = row["symbol"].stringValue()?.trim().orEmpty()
            val tokenName = row["name"].stringValue()?.trim().orEmpty()
            val displaySymbol = symbolValue.ifEmpty { tokenName }.ifEmpty { alphaId }
            val displayName = tokenName.ifEmpty { displaySymbol }
            add(
                WatchItem(
                    id = "binance-alpha:${alphaId}USDT",
                    symbol = "$displaySymbol/USDT",
                    name = displayName,
                    exchangeSource = ExchangeSource.BINANCE_ALPHA,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }
}

internal fun parseAlphaExchangeInfo(body: JsonObject): List<WatchItem> {
    if (!body.isAlphaSuccess()) return emptyList()

    val symbols = body["data"]?.jsonObject?.get("symbols")?.jsonArray ?: return emptyList()
    return buildList {
        symbols.forEach { element ->
            val row = element.jsonObject
            val symbol = row["symbol"].stringValue()?.uppercase() ?: return@forEach
            val quoteAsset = row["quoteAsset"].stringValue()?.uppercase() ?: alphaQuoteAsset(symbol)
            if (quoteAsset != "USDT") return@forEach

            val status = row["status"].stringValue()?.uppercase() ?: "TRADING"
            if (status != "TRADING" && status != "ENABLED") return@forEach

            val baseAsset = row["baseAsset"].stringValue()?.uppercase() ?: alphaBaseAsset(symbol, quoteAsset)
            add(
                WatchItem(
                    id = "binance-alpha:$symbol",
                    symbol = "$baseAsset/$quoteAsset",
                    name = baseAsset,
                    exchangeSource = ExchangeSource.BINANCE_ALPHA,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }
}

internal fun parseAlphaTicker(body: JsonObject, item: WatchItem): MarketQuote? {
    if (!body.isAlphaSuccess()) return null
    val row = body["data"]?.jsonObject ?: return null
    val lastPrice = row["lastPrice"].doubleValue() ?: return null
    val priceChangePercent = row["priceChangePercent"].doubleValue() ?: return null

    return MarketQuote(
        id = item.id,
        symbol = item.symbol,
        name = item.name,
        priceUsd = lastPrice,
        change24hPercent = priceChangePercent
    )
}

private fun alphaBaseAsset(symbol: String, quoteAsset: String): String {
    return if (symbol.endsWith(quoteAsset) && symbol.length > quoteAsset.length) {
        symbol.dropLast(quoteAsset.length)
    } else {
        symbol
    }
}

private fun alphaQuoteAsset(symbol: String): String {
    return when {
        symbol.endsWith("USDT") -> "USDT"
        symbol.endsWith("USDC") -> "USDC"
        symbol.endsWith("BTC") -> "BTC"
        else -> "USDT"
    }
}

