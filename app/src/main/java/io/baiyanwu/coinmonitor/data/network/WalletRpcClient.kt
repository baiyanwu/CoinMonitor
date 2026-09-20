package io.baiyanwu.coinmonitor.data.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WalletRpcClient(private val httpClient: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val requestIds = AtomicLong(1)

    suspend fun call(url: String, method: String, params: JsonArray = JsonArray(emptyList())): JsonElement {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestIds.getAndIncrement())
            put("method", method)
            put("params", params)
        }
        val request = Request.Builder()
            .url(url)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val response = httpClient.newCall(request).await()
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw IOException("RPC HTTP ${it.code}")
            val root = json.parseToJsonElement(text).jsonObject
            root["error"]?.takeUnless { error -> error is JsonNull }?.let { error ->
                throw IOException(error.jsonObject["message"]?.jsonPrimitive?.content ?: "RPC 请求失败")
            }
            return root["result"] ?: JsonNull
        }
    }

    suspend fun evmChainId(url: String): Long = call(url, "eth_chainId").jsonPrimitive.content.removePrefix("0x").toLong(16)

    suspend fun solanaGenesisHash(url: String): String = call(url, "getGenesisHash").jsonPrimitive.content

    suspend fun evmBalance(url: String, address: String): String = call(
        url,
        "eth_getBalance",
        buildJsonArray { add(JsonPrimitive(address)); add(JsonPrimitive("latest")) }
    ).jsonPrimitive.content

    suspend fun evmTokenBalance(url: String, tokenAddress: String, ownerAddress: String): String {
        val owner = ownerAddress.removePrefix("0x").lowercase().padStart(64, '0')
        return call(
            url,
            "eth_call",
            buildJsonArray {
                add(buildJsonObject {
                    put("to", tokenAddress)
                    put("data", "0x70a08231$owner")
                })
                add(JsonPrimitive("latest"))
            }
        ).jsonPrimitive.content
    }

    suspend fun evmNonce(url: String, address: String): String = call(
        url,
        "eth_getTransactionCount",
        buildJsonArray { add(JsonPrimitive(address)); add(JsonPrimitive("pending")) }
    ).jsonPrimitive.content

    suspend fun evmGasPrice(url: String): String = call(url, "eth_gasPrice").jsonPrimitive.content

    suspend fun evmEstimateGas(url: String, from: String, to: String, value: String, data: String? = null): String = call(
        url,
        "eth_estimateGas",
        buildJsonArray {
            add(buildJsonObject {
                put("from", from)
                put("to", to)
                put("value", value)
                data?.let { put("data", it) }
            })
        }
    ).jsonPrimitive.content

    suspend fun evmSendRawTransaction(url: String, rawTransaction: String): String = call(
        url,
        "eth_sendRawTransaction",
        buildJsonArray { add(JsonPrimitive(rawTransaction)) }
    ).jsonPrimitive.content

    suspend fun evmReceipt(url: String, hash: String): JsonObject? = call(
        url,
        "eth_getTransactionReceipt",
        buildJsonArray { add(JsonPrimitive(hash)) }
    ).takeUnless { it is JsonNull }?.jsonObject

    suspend fun solanaBalance(url: String, address: String): Long = call(
        url,
        "getBalance",
        buildJsonArray { add(JsonPrimitive(address)); add(buildJsonObject { put("commitment", "confirmed") }) }
    ).jsonObject.getValue("value").jsonPrimitive.content.toLong()

    suspend fun solanaLatestBlockhash(url: String): String = call(
        url,
        "getLatestBlockhash",
        buildJsonArray { add(buildJsonObject { put("commitment", "confirmed") }) }
    ).jsonObject.getValue("value").jsonObject.getValue("blockhash").jsonPrimitive.content

    suspend fun solanaMinimumBalanceForRentExemption(url: String, dataLength: Int): Long = call(
        url,
        "getMinimumBalanceForRentExemption",
        buildJsonArray { add(JsonPrimitive(dataLength)); add(buildJsonObject { put("commitment", "confirmed") }) }
    ).jsonPrimitive.content.toLong()

    suspend fun solanaAccountExists(url: String, address: String): Boolean = call(
        url,
        "getAccountInfo",
        buildJsonArray {
            add(JsonPrimitive(address))
            add(buildJsonObject { put("encoding", "base64"); put("commitment", "confirmed") })
        }
    ).jsonObject["value"] !is JsonNull

    suspend fun solanaTokenBalance(url: String, tokenAccount: String): String = call(
        url,
        "getTokenAccountBalance",
        buildJsonArray {
            add(JsonPrimitive(tokenAccount))
            add(buildJsonObject { put("commitment", "confirmed") })
        }
    ).jsonObject.getValue("value").jsonObject.getValue("amount").jsonPrimitive.content

    suspend fun solanaSendTransaction(url: String, transaction: String): String = call(
        url,
        "sendTransaction",
        buildJsonArray {
            add(JsonPrimitive(transaction))
            add(buildJsonObject { put("encoding", "base58"); put("preflightCommitment", "confirmed") })
        }
    ).jsonPrimitive.content

    suspend fun solanaSignatureStatus(url: String, signature: String): JsonObject? = call(
        url,
        "getSignatureStatuses",
        buildJsonArray {
            add(buildJsonArray { add(JsonPrimitive(signature)) })
            add(buildJsonObject { put("searchTransactionHistory", true) })
        }
    ).jsonObject.getValue("value").jsonArray.firstOrNull()?.takeUnless { it is JsonNull }?.jsonObject

    suspend fun solanaSignatures(url: String, address: String, limit: Int = 30): JsonArray = call(
        url,
        "getSignaturesForAddress",
        buildJsonArray {
            add(JsonPrimitive(address))
            add(buildJsonObject { put("limit", limit); put("commitment", "confirmed") })
        }
    ).jsonArray

    suspend fun solanaTransaction(url: String, signature: String): JsonObject? = call(
        url,
        "getTransaction",
        buildJsonArray {
            add(JsonPrimitive(signature))
            add(buildJsonObject {
                put("encoding", "jsonParsed")
                put("commitment", "confirmed")
                put("maxSupportedTransactionVersion", 0)
            })
        }
    ).takeUnless { it is JsonNull }?.jsonObject

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
