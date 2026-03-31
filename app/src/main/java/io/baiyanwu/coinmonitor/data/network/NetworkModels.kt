package io.baiyanwu.coinmonitor.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
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

interface OkxApi {
    @GET("api/v5/public/instruments")
    suspend fun getSpotInstruments(@Query("instType") instType: String = "SPOT"): OkxInstrumentsResponse

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

interface OkxOnChainApi {
    @GET("api/v6/dex/market/supported/chain")
    suspend fun getSupportedChains(
        @Header("OK-ACCESS-KEY") accessKey: String,
        @Header("OK-ACCESS-SIGN") accessSign: String,
        @Header("OK-ACCESS-TIMESTAMP") accessTimestamp: String,
        @Header("OK-ACCESS-PASSPHRASE") accessPassphrase: String,
        @Query("chainIndex") chainIndex: String? = null
    ): OkxOnChainSupportedChainsResponse

    @GET("api/v6/dex/market/token/search")
    suspend fun searchTokens(
        @Header("OK-ACCESS-KEY") accessKey: String,
        @Header("OK-ACCESS-SIGN") accessSign: String,
        @Header("OK-ACCESS-TIMESTAMP") accessTimestamp: String,
        @Header("OK-ACCESS-PASSPHRASE") accessPassphrase: String,
        @Query("chains") chains: String,
        @Query("search") search: String
    ): OkxOnChainTokenSearchResponse

    @POST("api/v6/dex/market/price")
    suspend fun getTokenPrices(
        @Header("OK-ACCESS-KEY") accessKey: String,
        @Header("OK-ACCESS-SIGN") accessSign: String,
        @Header("OK-ACCESS-TIMESTAMP") accessTimestamp: String,
        @Header("OK-ACCESS-PASSPHRASE") accessPassphrase: String,
        @Body requestBody: List<OkxOnChainPriceRequest>
    ): OkxOnChainTokenPriceResponse

    @GET("api/v6/dex/market/candles")
    suspend fun getCandles(
        @Header("OK-ACCESS-KEY") accessKey: String,
        @Header("OK-ACCESS-SIGN") accessSign: String,
        @Header("OK-ACCESS-TIMESTAMP") accessTimestamp: String,
        @Header("OK-ACCESS-PASSPHRASE") accessPassphrase: String,
        @Query("chainIndex") chainIndex: String,
        @Query("tokenContractAddress") tokenContractAddress: String,
        @Query("bar") bar: String,
        @Query("limit") limit: Int
    ): OkxOnChainCandlesResponse
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

@Serializable
data class OkxCandlesResponse(
    val code: String,
    val data: List<List<String>>
)

@Serializable
data class OkxOnChainTokenSearchResponse(
    val code: String,
    val msg: String? = null,
    val data: List<OkxOnChainTokenRow> = emptyList()
)

@Serializable
data class OkxOnChainSupportedChainsResponse(
    val code: String,
    val msg: String? = null,
    val data: List<OkxOnChainChainRow> = emptyList()
)

@Serializable
data class OkxOnChainTokenPriceResponse(
    val code: String,
    val msg: String? = null,
    val data: List<OkxOnChainTokenPriceRow> = emptyList()
)

@Serializable
data class OkxOnChainCandlesResponse(
    val code: String,
    val msg: String? = null,
    val data: List<List<String>> = emptyList()
)

@Serializable
data class OkxOnChainChainRow(
    @SerialName("chainIndex") val chainIndex: String? = null,
    @SerialName("chainName") val chainName: String? = null,
    @SerialName("chainLogoUrl") val chainLogoUrl: String? = null,
    @SerialName("chainSymbol") val chainSymbol: String? = null
)

@Serializable
data class OkxOnChainPriceRequest(
    @SerialName("chainIndex") val chainIndex: String,
    @SerialName("tokenContractAddress") val tokenContractAddress: String
)

@Serializable
data class OkxOnChainTokenRow(
    @SerialName("chainIndex") val chainIndex: String? = null,
    @SerialName("chainName") val chainName: String? = null,
    @SerialName("tokenContractAddress") val tokenContractAddress: String? = null,
    @SerialName("tokenSymbol") val tokenSymbol: String? = null,
    @SerialName("tokenName") val tokenName: String? = null,
    @SerialName("tokenLogoUrl") val tokenLogoUrl: String? = null,
    @SerialName("decimal") val decimal: String? = null,
    @SerialName("explorerUrl") val explorerUrl: String? = null,
    @SerialName("price") val price: String? = null,
    @SerialName("change") val change: String? = null
)

@Serializable
data class OkxOnChainTokenPriceRow(
    @SerialName("chainIndex") val chainIndex: String? = null,
    @SerialName("tokenContractAddress") val tokenContractAddress: String? = null,
    @SerialName("time") val time: String? = null,
    @SerialName("price") val price: String? = null
)

internal fun JsonObject.isAlphaSuccess(): Boolean {
    val success = get("success")?.toString()?.removeSurrounding("\"")?.toBooleanStrictOrNull()
    if (success == false) return false

    val code = get("code")?.toString()?.removeSurrounding("\"")
    return code == null || code == "0" || code == "000000"
}

internal fun JsonObject.jsonArray(key: String): JsonArray? = this[key] as? JsonArray
