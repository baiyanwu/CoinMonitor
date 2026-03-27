package io.coinbar.tokenmonitor.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

interface BinanceApi {
    @GET("api/v3/exchangeInfo")
    suspend fun getExchangeInfo(): BinanceExchangeInfoResponse

    @GET("api/v3/ticker/24hr")
    suspend fun getTickers(@Query("symbols") symbols: String): List<BinanceTickerRow>
}

interface OkxApi {
    @GET("api/v5/public/instruments")
    suspend fun getSpotInstruments(@Query("instType") instType: String = "SPOT"): OkxInstrumentsResponse

    @GET("api/v5/market/ticker")
    suspend fun getTicker(@Query("instId") instId: String): OkxTickerResponse
}

interface BinanceAlphaApi {
    @GET("bapi/defi/v1/public/alpha-trade/get-exchange-info")
    suspend fun getExchangeInfo(): JsonObject

    @GET("bapi/defi/v1/public/wallet-direct/buw/wallet/cex/alpha/all/token/list")
    suspend fun getTokenList(): JsonObject

    @GET("bapi/defi/v1/public/alpha-trade/ticker")
    suspend fun getTicker(@Query("symbol") symbol: String): JsonObject
}

@Serializable
data class BinanceExchangeInfoResponse(
    val symbols: List<BinanceSymbolRow>
)

@Serializable
data class BinanceSymbolRow(
    val symbol: String,
    val status: String,
    val baseAsset: String,
    val quoteAsset: String
)

@Serializable
data class BinanceTickerRow(
    val symbol: String,
    val lastPrice: String,
    val priceChangePercent: String
)

@Serializable
data class OkxInstrumentsResponse(
    val code: String,
    val data: List<OkxInstrumentRow>
)

@Serializable
data class OkxInstrumentRow(
    @SerialName("instId") val instId: String,
    val baseCcy: String,
    val quoteCcy: String,
    val state: String
)

@Serializable
data class OkxTickerResponse(
    val code: String,
    val data: List<OkxTickerRow>
)

@Serializable
data class OkxTickerRow(
    val last: String,
    val open24h: String
)

internal fun JsonObject.isAlphaSuccess(): Boolean {
    val success = get("success")?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull()
    if (success == false) return false

    val code = get("code")?.toString()?.removeSurrounding("\"")
    return code == null || code == "0" || code == "000000"
}

internal fun JsonObject.jsonArray(key: String): JsonArray? = this[key] as? JsonArray

