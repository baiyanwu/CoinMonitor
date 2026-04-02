package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.ai.OpenAiCompatibleAiClient
import io.baiyanwu.coinmonitor.data.ai.AppAnalysisHost
import io.baiyanwu.coinmonitor.data.ai.OpenAiCompatibleStreamingClient
import io.baiyanwu.coinmonitor.domain.model.AiAnalysisOption
import io.baiyanwu.coinmonitor.domain.model.AiChatMessage
import io.baiyanwu.coinmonitor.domain.model.AiChatRole
import io.baiyanwu.coinmonitor.domain.model.CandleEntry
import io.baiyanwu.coinmonitor.domain.model.KlineIndicator
import io.baiyanwu.coinmonitor.domain.model.KlineIndicatorSettings
import io.baiyanwu.coinmonitor.domain.model.KlineInterval
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.AiChatRepository
import io.baiyanwu.coinmonitor.domain.repository.AiConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DefaultAiChatRepository(
    private val aiConfigRepository: AiConfigRepository,
    private val analysisHost: AppAnalysisHost = AppAnalysisHost(),
    private val streamingClient: OpenAiCompatibleAiClient = OpenAiCompatibleStreamingClient()
) : AiChatRepository {
    override fun streamMessage(
        item: WatchItem,
        interval: KlineInterval,
        indicators: Set<KlineIndicator>,
        indicatorSettings: KlineIndicatorSettings,
        analysisOptions: Set<AiAnalysisOption>,
        candles: List<CandleEntry>,
        messages: List<AiChatMessage>
    ): Flow<String> = flow {
        val config = aiConfigRepository.getConfig()
        require(config.enabled && config.isReady) { "请先在设置页完成 AI 配置" }

        val latestUserPrompt = messages.lastOrNull { it.role == AiChatRole.USER }?.content.orEmpty()
        val analysis = analysisHost.analyze(
            item = item,
            interval = interval,
            indicatorSettings = indicatorSettings,
            analysisOptions = analysisOptions,
            candles = candles,
            userPrompt = latestUserPrompt
        )
        val requestJson = buildJsonObject {
            put("model", config.model)
            put("stream", true)
            put("messages", buildJsonArray {
                val systemPrompt = config.systemPrompt.ifBlank {
                    "You are a concise crypto market analysis assistant."
                }
                add(buildJsonObject {
                    put("role", "system")
                    put(
                        "content",
                        buildSystemPrompt(
                            basePrompt = systemPrompt,
                            item = item,
                            interval = interval,
                            indicators = indicators,
                            analysisOptions = analysisOptions,
                            analysisContext = analysisHost.formatPromptContext(
                                item = item,
                                interval = interval,
                                analysis = analysis
                            )
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

        emitAll(
            streamingClient.streamChatCompletion(
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                payload = requestJson
            )
        )
    }

    private fun buildSystemPrompt(
        basePrompt: String,
        item: WatchItem,
        interval: KlineInterval,
        indicators: Set<KlineIndicator>,
        analysisOptions: Set<AiAnalysisOption>,
        analysisContext: String
    ): String {
        return buildString {
            append(basePrompt.trim())
            append('\n')
            if (AiAnalysisOption.INDICATOR_INFO in analysisOptions) {
                append("Current visible indicators: ")
                append(indicators.joinToString { it.label })
                append(".\n")
            }
            append("Current asset: ")
            append(item.symbol)
            append(". Interval: ")
            append(interval.label)
            append(".\n")
            append("Enabled analysis inputs: ")
            append(
                analysisOptions.joinToString { option ->
                    when (option) {
                        AiAnalysisOption.INDICATOR_INFO -> "indicator info"
                        AiAnalysisOption.BINANCE_ANNOUNCEMENT -> "binance announcements"
                        AiAnalysisOption.OKX_ANNOUNCEMENT -> "okx announcements"
                        AiAnalysisOption.PROJECT_INFO -> "project info"
                    }
                }
            )
            append(".\n")
            append(analysisContext)
            append("\nUse the analysis context above as the primary basis for your answer. ")
            append("Do not fabricate external market evidence that is not present in the context.")
        }
    }
}
