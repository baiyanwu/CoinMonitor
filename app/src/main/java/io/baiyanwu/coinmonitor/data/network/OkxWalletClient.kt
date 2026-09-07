package io.baiyanwu.coinmonitor.data.network

import io.baiyanwu.coinmonitor.domain.model.OkxWalletCredentials
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object OkxWalletRequestSigner {
    fun signature(secretKey: String, timestamp: String, method: String, requestPath: String, body: String = ""): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val payload = timestamp + method.uppercase() + requestPath + body
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))
    }
}

internal data class OkxWalletTokenRow(
    val chainIndex: String,
    val contractAddress: String,
    val symbol: String,
    val balance: String,
    val tokenPrice: String?,
    val isRiskToken: Boolean
)

internal class OkxWalletApiException(val apiCode: String, override val message: String) : Exception(message)

internal class OkxWalletClient(
    private val httpClient: OkHttpClient,
    private val credentialsProvider: () -> OkxWalletCredentials,
    private val clock: Clock = Clock.systemUTC(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val serverTimeMutex = Mutex()
    @Volatile private var serverTimeOffsetMillis: Long? = null
    @Volatile private var supportedChainIndexes: Set<String>? = null
    private val supportedChainsMutex = Mutex()

    suspend fun getSupportedChainIndexes(): Set<String> {
        supportedChainIndexes?.let { return it }
        return supportedChainsMutex.withLock {
            supportedChainIndexes?.let { return@withLock it }
            val indexes = execute(baseUrl(SUPPORTED_CHAINS_PATH).build())
                .array("data")
                .mapNotNull { it.asObject()?.string("chainIndex") }
                .toSet()
            if (indexes.isEmpty()) throw OkxWalletApiException("INVALID_DATA", "OKX 未返回可用链列表。")
            supportedChainIndexes = indexes
            indexes
        }
    }

    suspend fun getAllTokenBalances(address: String, chains: List<String>): List<OkxWalletTokenRow> {
        val url = baseUrl(ALL_BALANCES_PATH)
            .addQueryParameter("address", address)
            .addQueryParameter("chains", chains.joinToString(","))
            .addQueryParameter("excludeRiskToken", "1")
            .build()
        val root = execute(url)
        val data = root.array("data")
        val assets = data.flatMap { element ->
            element.asObject()?.array("tokenAssets").orEmpty()
        }
        return assets.mapNotNull { element ->
            val item = element.asObject() ?: return@mapNotNull null
            val chainIndex = item.string("chainIndex") ?: return@mapNotNull null
            val balance = item.string("balance") ?: return@mapNotNull null
            OkxWalletTokenRow(
                chainIndex = chainIndex,
                contractAddress = item.string("tokenContractAddress") ?: item.string("address").orEmpty(),
                symbol = item.string("symbol").orEmpty().ifBlank { "?" },
                balance = balance,
                tokenPrice = item.string("tokenPrice")?.takeIf { it.isNotBlank() },
                isRiskToken = item.boolean("isRiskToken") ?: false
            )
        }
    }

    suspend fun getTotalValue(address: String, chains: List<String>, includeRisk: Boolean): String {
        val url = baseUrl(TOTAL_VALUE_PATH)
            .addQueryParameter("address", address)
            .addQueryParameter("chains", chains.joinToString(","))
            .addQueryParameter("assetType", "1")
            .addQueryParameter("excludeRiskToken", (!includeRisk).toString())
            .build()
        val root = execute(url)
        return root.array("data").firstOrNull()?.asObject()?.string("totalValue")
            ?: throw OkxWalletApiException("INVALID_DATA", "OKX 未返回总资产估值。")
    }

    private suspend fun execute(url: HttpUrl): JsonObject {
        val credentials = credentialsProvider()
        if (!credentials.isReady) throw OkxWalletApiException("CREDENTIALS", "请先配置并启用 OKX 钱包资产 API。")
        val timestamp = DateTimeFormatter.ISO_INSTANT.format(
            Instant.ofEpochMilli(clock.millis() + getServerTimeOffsetMillis())
        )
        val requestPath = url.encodedPath + if (url.encodedQuery != null) "?${url.encodedQuery}" else ""
        val request = Request.Builder().url(url).get()
            .header("OK-ACCESS-KEY", credentials.apiKey)
            .header("OK-ACCESS-SIGN", OkxWalletRequestSigner.signature(credentials.secretKey, timestamp, "GET", requestPath))
            .header("OK-ACCESS-PASSPHRASE", credentials.passphrase)
            .header("OK-ACCESS-TIMESTAMP", timestamp)
            .build()
        val response = httpClient.newCall(request).await()
        response.use {
            val body = it.body?.string().orEmpty()
            val root = runCatching { json.parseToJsonElement(body).jsonObject }
                .getOrElse { throw OkxWalletApiException("INVALID_RESPONSE", "OKX 返回了无法解析的数据。") }
            val code = root.string("code").orEmpty()
            if (!it.isSuccessful || code != "0") {
                throw OkxWalletApiException(code.ifBlank { it.code.toString() }, root.string("msg") ?: "OKX 请求失败。")
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
                val response = httpClient.newCall(request).await()
                response.use {
                    if (!it.isSuccessful) error("OKX server time request failed: ${it.code}")
                    val root = json.parseToJsonElement(it.body?.string().orEmpty()).jsonObject
                    val serverMillis = root.array("data").firstOrNull()?.asObject()?.string("ts")?.toLongOrNull()
                        ?: error("OKX server time response is invalid")
                    serverMillis - clock.millis()
                }
            }.getOrNull()
            if (offset != null) serverTimeOffsetMillis = offset
            offset ?: 0L
        }
    }

    private fun baseUrl(path: String): HttpUrl.Builder = HttpUrl.Builder()
        .scheme("https").host("web3.okx.com").addEncodedPathSegments(path.removePrefix("/"))

    companion object {
        const val ALL_BALANCES_PATH = "/api/v6/dex/balance/all-token-balances-by-address"
        const val TOTAL_VALUE_PATH = "/api/v6/dex/balance/total-value-by-address"
        const val SUPPORTED_CHAINS_PATH = "/api/v6/dex/balance/supported/chain"
        const val OKX_SERVER_TIME_URL = "https://www.okx.com/api/v5/public/time"
    }
}

private suspend fun okhttp3.Call.await(): okhttp3.Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { continuation.resume(response) }
    })
}

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
private fun JsonObject.boolean(name: String): Boolean? = this[name]?.jsonPrimitive?.booleanOrNull
private fun JsonObject.array(name: String): JsonArray = this[name] as? JsonArray ?: JsonArray(emptyList())
private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
