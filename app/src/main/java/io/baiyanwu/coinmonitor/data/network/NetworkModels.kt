package io.baiyanwu.coinmonitor.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface BinanceApi {
    @GET("api/v3/exchangeInfo")
    suspend fun getExchangeInfo(): BinanceExchangeInfoResponse

    @GET("api/v3/ticker/24hr")
    suspend fun getTickers(@Query("symbols") symbols: String): List<BinanceTickerRow>

    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): JsonArray
}

interface BinanceFuturesApi {
    @GET("fapi/v1/exchangeInfo")
    suspend fun getExchangeInfo(): BinanceExchangeInfoResponse

    @GET("fapi/v1/ticker/24hr")
    suspend fun getTicker(@Query("symbol") symbol: String): BinanceTickerRow

    @GET("fapi/v1/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): JsonArray
}

interface OkxApi {
    @GET("api/v5/public/instruments")
    suspend fun getSpotInstruments(@Query("instType") instType: String = "SPOT"): OkxInstrumentsResponse

    @GET("api/v5/public/instruments")
    suspend fun getInstruments(@Query("instType") instType: String): OkxInstrumentsResponse

    @GET("api/v5/market/ticker")
    suspend fun getTicker(@Query("instId") instId: String): OkxTickerResponse

    @GET("api/v5/market/candles")
    suspend fun getCandles(
        @Query("instId") instId: String,
        @Query("bar") bar: String,
        @Query("limit") limit: Int
    ): OkxCandlesResponse
}

interface BinanceAlphaApi {
    @GET("bapi/defi/v1/public/alpha-trade/get-exchange-info")
    suspend fun getExchangeInfo(): JsonObject

    @GET("bapi/defi/v1/public/wallet-direct/buw/wallet/cex/alpha/all/token/list")
    suspend fun getTokenList(): JsonObject

    @GET("bapi/defi/v1/public/alpha-trade/ticker")
    suspend fun getTicker(@Query("symbol") symbol: String): JsonObject

    @GET("bapi/defi/v1/public/alpha-trade/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): JsonObject
}

interface DexScreenerApi {
    @GET("latest/dex/search")
    suspend fun searchPairs(@Query("q") query: String): DexScreenerSearchResponse

    @GET("token-pairs/v1/{chainId}/{tokenAddress}")
    suspend fun getTokenPairs(
        @Path("chainId") chainId: String,
        @Path("tokenAddress") tokenAddress: String
    ): List<DexScreenerPair>

    @GET("tokens/v1/{chainId}/{tokenAddresses}")
    suspend fun getTokenPairsBatch(
        @Path("chainId") chainId: String,
        @Path(value = "tokenAddresses", encoded = true) tokenAddresses: String
    ): List<DexScreenerPair>
}

interface GeckoTerminalApi {
    @Headers("Accept: application/json;version=20230302")
    @GET("api/v2/networks/{network}/pools/{poolAddress}/ohlcv/{timeframe}")
    suspend fun getPoolOhlcv(
        @Path("network") network: String,
        @Path("poolAddress") poolAddress: String,
        @Path("timeframe") timeframe: String,
        @Query("aggregate") aggregate: Int,
        @Query("limit") limit: Int,
        @Query("currency") currency: String = "usd",
        @Query("token") token: String,
        @Query("before_timestamp") beforeTimestamp: Long? = null
    ): GeckoTerminalOhlcvResponse
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
    val quoteAsset: String,
    val contractType: String? = null,
    val marginAsset: String? = null
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
    val baseCcy: String = "",
    val quoteCcy: String = "",
    val state: String = "",
    @SerialName("instType") val instType: String? = null,
    @SerialName("instFamily") val instFamily: String? = null,
    val settleCcy: String? = null,
    val ctType: String? = null
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

@Serializable
data class OkxCandlesResponse(
    val code: String,
    val data: List<List<String>>
)

@Serializable
data class DexScreenerSearchResponse(
    val pairs: List<DexScreenerPair>? = null
)

@Serializable
data class DexScreenerPair(
    val chainId: String = "",
    val dexId: String = "",
    val labels: List<String>? = null,
    val url: String? = null,
    val pairAddress: String = "",
    val baseToken: DexScreenerToken = DexScreenerToken(),
    val quoteToken: DexScreenerToken = DexScreenerToken(),
    val priceNative: String? = null,
    val priceUsd: String? = null,
    val volume: Map<String, Double> = emptyMap(),
    val priceChange: Map<String, Double>? = null,
    val liquidity: DexScreenerLiquidity? = null,
    val fdv: Double? = null,
    val marketCap: Double? = null,
    val info: DexScreenerInfo? = null
)

@Serializable
data class DexScreenerToken(
    val address: String = "",
    val name: String = "",
    val symbol: String = ""
)

@Serializable
data class DexScreenerLiquidity(
    val usd: Double? = null
)

@Serializable
data class DexScreenerInfo(
    val imageUrl: String? = null,
    val websites: List<DexScreenerWebsite> = emptyList(),
    val socials: List<DexScreenerSocial> = emptyList()
)

@Serializable
data class DexScreenerWebsite(
    val label: String? = null,
    val url: String? = null
)

@Serializable
data class DexScreenerSocial(
    val type: String? = null,
    val platform: String? = null,
    val handle: String? = null
)

@Serializable
data class GeckoTerminalOhlcvResponse(
    val data: GeckoTerminalOhlcvData
)

@Serializable
data class GeckoTerminalOhlcvData(
    val attributes: GeckoTerminalOhlcvAttributes
)

@Serializable
data class GeckoTerminalOhlcvAttributes(
    @SerialName("ohlcv_list") val ohlcvList: List<List<Double>> = emptyList()
)

internal fun JsonObject.isAlphaSuccess(): Boolean {
    val success = get("success")?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull()
    if (success == false) return false

    val code = get("code")?.toString()?.removeSurrounding("\"")
    return code == null || code == "0" || code == "000000"
}

internal fun JsonObject.jsonArray(key: String): JsonArray? = this[key] as? JsonArray
