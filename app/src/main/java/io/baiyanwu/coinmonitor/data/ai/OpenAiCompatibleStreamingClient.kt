package io.baiyanwu.coinmonitor.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

interface OpenAiCompatibleAiClient {
    fun streamChatCompletion(
        baseUrl: String,
        apiKey: String,
        payload: JsonObject
    ): Flow<String>

    suspend fun completeChatCompletion(
        baseUrl: String,
        apiKey: String,
        payload: JsonObject
    ): String = error("Non-streaming completion is not implemented.")
}

class OpenAiCompatibleStreamingClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()
) : OpenAiCompatibleAiClient {
    private val json = Json { ignoreUnknownKeys = true }

    override fun streamChatCompletion(
        baseUrl: String,
        apiKey: String,
        payload: JsonObject
    ): Flow<String> = callbackFlow {
        val request = Request.Builder()
            .url(buildEndpoint(baseUrl))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val call = okHttpClient.newCall(request)
        val job = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    val body = response.body ?: error("AI 响应为空")
                    if (!response.isSuccessful) {
                        val errorBody = body.string()
                        error(buildHttpError(response.code, errorBody))
                    }

                    val source = body.source()
                    var assembledText = ""
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank() || !line.startsWith(DATA_PREFIX)) continue
                        val payloadLine = line.removePrefix(DATA_PREFIX).trim()
                        if (payloadLine == DONE_MARKER) break
                        val incomingText = parseDelta(payloadLine) ?: continue
                        val delta = resolveDelta(
                            assembledText = assembledText,
                            incomingText = incomingText
                        ) ?: continue
                        assembledText = mergeStreamText(
                            assembledText = assembledText,
                            incomingText = incomingText
                        )
                        trySend(delta)
                    }
                }
                close()
            } catch (error: Throwable) {
                close(error)
            }
        }

        awaitClose {
            call.cancel()
            job.cancel()
        }
    }

    private fun buildEndpoint(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        if (normalized.endsWith("/v1")) {
            return "$normalized/chat/completions"
        }
        val pathAfterHost = normalized.substringAfter("://", "").substringAfter('/', "")
        return if (pathAfterHost.isNotEmpty()) {
            "$normalized/chat/completions"
        } else {
            "$normalized/v1/chat/completions"
        }
    }

    private fun parseDelta(raw: String): String? {
        val root = json.parseToJsonElement(raw).jsonObject
        val delta = root["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("delta")
            ?.jsonObject
            ?: return null

        val content = delta["content"] ?: return null
        return extractContentText(content)
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractContentText(content: JsonElement): String? {
        content.jsonPrimitive.contentOrNull?.let { return it }
        val textParts = runCatching {
            content.jsonArray.mapNotNull { item ->
                item.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                    ?: item.jsonObject["content"]?.jsonPrimitive?.contentOrNull
            }
        }.getOrDefault(emptyList())
        return textParts.takeIf { it.isNotEmpty() }?.joinToString("")
    }

    private fun buildHttpError(code: Int, body: String): String {
        val message = extractErrorMessage(body)
        return buildString {
            append("AI 请求失败 (HTTP ")
            append(code)
            append(')')
            if (!message.isNullOrBlank()) {
                append("：")
                append(message)
            }
        }
    }

    private fun extractErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                ?: root["message"]?.jsonPrimitive?.contentOrNull
                ?: body.take(300)
        }.getOrNull() ?: body.take(300)
    }

    /**
     * 同时兼容“真正的增量 chunk”和“累计全文 chunk”。
     */
    private fun resolveDelta(
        assembledText: String,
        incomingText: String
    ): String? {
        if (incomingText.isBlank()) return null
        if (assembledText.isEmpty()) return incomingText
        if (incomingText == assembledText) return null
        if (incomingText.startsWith(assembledText)) {
            return incomingText.removePrefix(assembledText).takeIf { it.isNotEmpty() }
        }
        val overlap = findOverlapSuffixPrefix(
            left = assembledText,
            right = incomingText
        )
        if (overlap == incomingText.length) return null
        return incomingText.drop(overlap).takeIf { it.isNotEmpty() }
    }

    /**
     * 将当前 chunk 合并到已组装文本中。
     */
    private fun mergeStreamText(
        assembledText: String,
        incomingText: String
    ): String {
        if (incomingText.isBlank()) return assembledText
        if (assembledText.isEmpty()) return incomingText
        if (incomingText == assembledText) return assembledText
        if (incomingText.startsWith(assembledText)) return incomingText
        val overlap = findOverlapSuffixPrefix(
            left = assembledText,
            right = incomingText
        )
        if (overlap == incomingText.length) return assembledText
        return assembledText + incomingText.drop(overlap)
    }

    /**
     * 查找左侧后缀与右侧前缀的最长重叠长度。
     */
    private fun findOverlapSuffixPrefix(
        left: String,
        right: String
    ): Int {
        val maxOverlap = minOf(left.length, right.length)
        for (length in maxOverlap downTo 1) {
            if (left.regionMatches(left.length - length, right, 0, length)) {
                return length
            }
        }
        return 0
    }

    private companion object {
        private const val DATA_PREFIX = "data:"
        private const val DONE_MARKER = "[DONE]"
    }
}
