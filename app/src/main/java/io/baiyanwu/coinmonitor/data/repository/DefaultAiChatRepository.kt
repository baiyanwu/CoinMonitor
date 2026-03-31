package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.domain.model.AiChatMessage
import io.baiyanwu.coinmonitor.domain.model.AiChatRole
import io.baiyanwu.coinmonitor.domain.model.CandleEntry
import io.baiyanwu.coinmonitor.domain.model.KlineIndicator
import io.baiyanwu.coinmonitor.domain.model.KlineInterval
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.AiChatRepository
import io.baiyanwu.coinmonitor.domain.repository.AiConfigRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DefaultAiChatRepository(
    private val aiConfigRepository: AiConfigRepository,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) : AiChatRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sendMessage(
        item: WatchItem,
        interval: KlineInterval,
        indicators: Set<KlineIndicator>,
        candles: List<CandleEntry>,
        messages: List<AiChatMessage>
    ): String {
        val config = aiConfigRepository.getConfig()
        require(config.enabled && config.isReady) { "AI configuration is unavailable." }

        val latestClosedCandle = candles.lastOrNull { it.isConfirmed } ?: candles.lastOrNull()
        val endpoint = buildEndpoint(config.baseUrl)
        val requestJson = buildJsonObject {
            put("model", config.model)
            put("messages", buildJsonArray {
                val systemPrompt = config.systemPrompt.ifBlank {
                    "You are a concise crypto market analysis assistant."
                }
                add(buildJsonObject {
                    put("role", "system")
                    put(
                        "content",
                        buildContextPrompt(
                            basePrompt = systemPrompt,
                            item = item,
                            interval = interval,
                            indicators = indicators,
                            latestClosedCandle = latestClosedCandle
                        )
                    )
                })
                messages
                    .filter { it.role != AiChatRole.SYSTEM }
                    .forEach { message ->
                        add(buildJsonObject {
                            put(
                                "role",
                                when (message.role) {
                                    AiChatRole.USER -> "user"
                                    AiChatRole.ASSISTANT -> "assistant"
                                    AiChatRole.SYSTEM -> "system"
                                }
                            )
                            put("content", message.content)
                        })
                    }
            })
        }
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("AI request failed: ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val root = json.parseToJsonElement(body).jsonObject
            return root["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: error("AI response is empty.")
        }
    }

    private fun buildEndpoint(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return if (normalized.endsWith("/v1")) {
            "$normalized/chat/completions"
        } else {
            "$normalized/v1/chat/completions"
        }
    }

    private fun buildContextPrompt(
        basePrompt: String,
        item: WatchItem,
        interval: KlineInterval,
        indicators: Set<KlineIndicator>,
        latestClosedCandle: CandleEntry?
    ): String {
        val latestSummary = latestClosedCandle?.let { candle ->
            "Latest candle O=${candle.open}, H=${candle.high}, L=${candle.low}, C=${candle.close}, V=${candle.volume}."
        } ?: "Latest candle is unavailable."
        val marketKind = if (item.marketType == MarketType.ONCHAIN_TOKEN) "onchain" else "spot"
        return buildString {
            append(basePrompt.trim())
            append('\n')
            append("Current market context: ")
            append(item.symbol)
            append(" on ")
            append(marketKind)
            append(", interval ")
            append(interval.label)
            append(". Indicators: ")
            append(indicators.joinToString { it.label })
            append(". ")
            append(latestSummary)
            item.lastPrice?.let { append(" Latest price=").append(it).append('.') }
            item.change24hPercent?.let { append(" 24h change=").append(it).append("%.") }
        }
    }
}
