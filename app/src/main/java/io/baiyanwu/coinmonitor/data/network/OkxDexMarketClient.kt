package io.baiyanwu.coinmonitor.data.network

import io.baiyanwu.coinmonitor.domain.model.OkxWalletCredentials
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class OkxDexChain(
    val chainIndex: String,
    val chainName: String,
    val chainSymbol: String,
    val chainLogoUrl: String?
)

internal data class OkxDexToken(
    val chainIndex: String,
    val tokenName: String,
    val tokenSymbol: String,
    val tokenLogoUrl: String?,
    val tokenContractAddress: String,
    val price: Double?,
    val change24hPercent: Double?,
    val marketCap: Double?
)

internal data class OkxDexQuote(
    val chainIndex: String,
    val tokenContractAddress: String,
    val price: Double,
    val change24hPercent: Double?,
    val marketCap: Double?
)

internal class OkxDexApiException(
    val apiCode: String,
    override val message: String
) : Exception(message)

/** Authenticated OKX Onchain Market API client. Credentials are always supplied by the user. */
internal class OkxDexMarketClient(
    private val httpClient: OkHttpClient,
    private val credentialsProvider: () -> OkxWalletCredentials,
    private val clock: Clock = Clock.systemUTC(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    @Volatile private var supportedChainsCache: CachedChains? = null
    @Volatile private var serverTimeOffsetMillis: Long? = null
    private val serverTimeMutex = Mutex()

    fun isConfigured(): Boolean = credentialsProvider().isReady

    suspend fun searchTokens(keyword: String): List<OkxDexToken> {
        val chains = getSupportedChains()
        if (chains.isEmpty()) return emptyList()

        val successful = mutableListOf<List<OkxDexToken>>()
        var firstFailure: Exception? = null
        chains.map(OkxDexChain::chainIndex).distinct().chunked(MAX_SEARCH_CHAINS_PER_REQUEST).forEach { batch ->
            try {
                val url = baseUrl(TOKEN_SEARCH_PATH)
                    .addQueryParameter("chains", batch.joinToString(","))
                    .addQueryParameter("search", keyword)
                    .addQueryParameter("limit", MAX_SEARCH_RESULTS.toString())
                    .build()
                successful += parseTokenRows(executeGet(url)).mapNotNull(::parseToken)
            } catch (error: Exception) {
                firstFailure = firstFailure ?: error
            }
        }
        if (successful.isEmpty() && firstFailure != null) throw firstFailure
        return successful.flatten()
            .distinctBy { "${it.chainIndex}:${it.tokenContractAddress}" }
    }

    suspend fun getQuotes(requests: List<Pair<String, String>>): List<OkxDexQuote> {
        if (requests.isEmpty()) return emptyList()
        return requests.distinct().chunked(MAX_QUOTES_PER_REQUEST).flatMap { batch ->
            val body = buildJsonArray {
                batch.forEach { (chainIndex, address) ->
                    add(buildJsonObject {
                        put("chainIndex", chainIndex)
                        put("tokenContractAddress", address)
                    })
                }
            }.toString()
            parseTokenRows(executePost(PRICE_INFO_PATH, body)).mapNotNull { row ->
                val price = row.string("price")?.toDoubleOrNull() ?: return@mapNotNull null
                OkxDexQuote(
                    chainIndex = row.string("chainIndex") ?: return@mapNotNull null,
                    tokenContractAddress = row.string("tokenContractAddress") ?: return@mapNotNull null,
                    price = price,
                    change24hPercent = row.string("priceChange24H")?.toDoubleOrNull(),
                    marketCap = row.string("marketCap")?.toDoubleOrNull()
                )
            }
        }
    }

    suspend fun getSupportedChains(): List<OkxDexChain> {
        supportedChainsCache?.takeIf { clock.millis() - it.savedAtMillis < CHAIN_CACHE_TTL_MILLIS }
            ?.let { return it.chains }
        val rows = parseTokenRows(executeGet(baseUrl(SUPPORTED_CHAINS_PATH).build()))
        val chains = rows.mapNotNull { row ->
            OkxDexChain(
                chainIndex = row.string("chainIndex") ?: return@mapNotNull null,
                chainName = row.string("chainName").orEmpty(),
                chainSymbol = row.string("chainSymbol").orEmpty(),
                chainLogoUrl = row.string("chainLogoUrl")
            )
        }
        if (chains.isEmpty()) throw OkxDexApiException("INVALID_DATA", "OKX 未返回可用链列表。")
        supportedChainsCache = CachedChains(clock.millis(), chains)
        return chains
    }

    private suspend fun executeGet(url: HttpUrl): JsonObject {
        val requestPath = url.encodedPath + url.encodedQuery?.let { "?$it" }.orEmpty()
        return execute(
            request = signedRequest(method = "GET", requestPath = requestPath) {
                url(url).get()
            }
        )
    }

    private suspend fun executePost(path: String, body: String): JsonObject = execute(
        request = signedRequest(method = "POST", requestPath = path, body = body) {
            url(baseUrl(path).build())
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
        }
    )

    private suspend fun signedRequest(
        method: String,
        requestPath: String,
        body: String = "",
        configure: Request.Builder.() -> Request.Builder
    ): Request {
        val credentials = credentialsProvider()
        if (!credentials.isReady) {
            throw OkxDexApiException("CREDENTIALS", "请先配置并启用 OKX Onchain API。")
        }
        val timestamp = DateTimeFormatter.ISO_INSTANT.format(
            Instant.ofEpochMilli(clock.millis() + getServerTimeOffsetMillis())
        )
        return Request.Builder().configure()
            .header("OK-ACCESS-KEY", credentials.apiKey)
            .header(
                "OK-ACCESS-SIGN",
                OkxOnchainRequestSigner.signature(credentials.secretKey, timestamp, method, requestPath, body)
            )
            .header("OK-ACCESS-PASSPHRASE", credentials.passphrase)
            .header("OK-ACCESS-TIMESTAMP", timestamp)
            .header("Content-Type", "application/json")
            .build()
    }

    private suspend fun execute(request: Request): JsonObject {
        val response = httpClient.newCall(request).awaitOkxDex()
        response.use {
            val root = runCatching { json.parseToJsonElement(it.body?.string().orEmpty()) as JsonObject }
                .getOrElse { throw OkxDexApiException("INVALID_RESPONSE", "OKX 返回了无法解析的数据。") }
            val code = root.string("code").orEmpty()
            if (!it.isSuccessful || code != "0") {
                throw OkxDexApiException(
                    code.ifBlank { it.code.toString() },
                    root.string("msg") ?: "OKX 请求失败。"
                )
            }
            return root
        }
    }

    private suspend fun getServerTimeOffsetMillis(): Long {
        serverTimeOffsetMillis?.let { return it }
        return serverTimeMutex.withLock {
            serverTimeOffsetMillis?.let { return@withLock it }
            val offset = runCatching {
                val request = Request.Builder().url(OKX_SERVER_TIME_URL).get().build()
                val response = httpClient.newCall(request).awaitOkxDex()
                response.use {
                    if (!it.isSuccessful) error("OKX server time request failed: ${it.code}")
                    val root = json.parseToJsonElement(it.body?.string().orEmpty()) as JsonObject
                    val serverMillis = root.array("data").firstOrNull()
                        ?.let { row -> row as? JsonObject }
                        ?.string("ts")
                        ?.toLongOrNull()
                        ?: error("OKX server time response is invalid")
                    serverMillis - clock.millis()
                }
            }.getOrNull()
            if (offset != null) serverTimeOffsetMillis = offset
            offset ?: 0L
        }
    }

    private fun parseToken(row: JsonObject): OkxDexToken? {
        val address = row.string("tokenContractAddress")?.takeIf(String::isNotBlank) ?: return null
        return OkxDexToken(
            chainIndex = row.string("chainIndex") ?: return null,
            tokenName = row.string("tokenName").orEmpty(),
            tokenSymbol = row.string("tokenSymbol").orEmpty(),
            tokenLogoUrl = row.string("tokenLogoUrl"),
            tokenContractAddress = address,
            price = row.string("price")?.toDoubleOrNull(),
            change24hPercent = row.string("change")?.toDoubleOrNull(),
            marketCap = row.string("marketCap")?.toDoubleOrNull()
        )
    }

    private fun parseTokenRows(root: JsonObject): List<JsonObject> = root.array("data").flatMap { element ->
        val row = element as? JsonObject ?: return@flatMap emptyList()
        val nested = row["tokens"] as? JsonArray
        if (nested == null) listOf(row) else nested.mapNotNull { it as? JsonObject }
    }

    private fun baseUrl(path: String): HttpUrl.Builder = HttpUrl.Builder()
        .scheme("https")
        .host("web3.okx.com")
        .addEncodedPathSegments(path.removePrefix("/"))

    private data class CachedChains(val savedAtMillis: Long, val chains: List<OkxDexChain>)

    private companion object {
        const val SUPPORTED_CHAINS_PATH = "/api/v6/dex/market/supported/chain"
        const val TOKEN_SEARCH_PATH = "/api/v6/dex/market/token/search"
        const val PRICE_INFO_PATH = "/api/v6/dex/market/price-info"
        const val OKX_SERVER_TIME_URL = "https://www.okx.com/api/v5/public/time"
        const val MAX_SEARCH_CHAINS_PER_REQUEST = 20
        const val MAX_SEARCH_RESULTS = 100
        const val MAX_QUOTES_PER_REQUEST = 100
        const val CHAIN_CACHE_TTL_MILLIS = 24 * 60 * 60 * 1_000L
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

private suspend fun okhttp3.Call.awaitOkxDex(): okhttp3.Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, error: java.io.IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                continuation.resume(response)
            }
        })
    }

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
private fun JsonObject.array(name: String): JsonArray = this[name] as? JsonArray ?: JsonArray(emptyList())
