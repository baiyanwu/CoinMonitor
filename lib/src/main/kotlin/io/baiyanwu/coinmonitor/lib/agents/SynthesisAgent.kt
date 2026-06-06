package io.baiyanwu.coinmonitor.lib.agents

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 综合分析引擎协议，允许外部替换默认综合逻辑。
 */
interface SynthesisEngine {
    /**
     * 将指标分析和市场情报合成为最终输出。
     */
    suspend fun synthesize(input: SynthesisInput): SynthesisOutput
}

/**
 * 综合分析阶段的标准输入。
 */
data class SynthesisInput(
    val request: AnalysisRequest,
    val context: AnalysisContext,
    val indicatorResult: AgentResult?,
    val marketResult: AgentResult?
)

@Serializable
/**
 * 综合分析阶段的标准输出。
 */
data class SynthesisOutput(
    val summary: String,
    val confidence: Double? = null,
    val structuredPayload: kotlinx.serialization.json.JsonObject = emptyJsonObject(),
    val warnings: List<String> = emptyList()
)

/**
 * 默认综合分析 agent。
 */
class DefaultSynthesisAgent(
    private val engine: SynthesisEngine = RuleBasedSynthesisEngine()
) : AnalysisAgent {
    override val spec: AgentSpec = AgentSpec(
        id = AgentId.SYNTHESIS,
        displayName = "Synthesis Agent",
        description = "Combines indicator and market outputs into a final analysis result.",
        dependencies = setOf(AgentId.INDICATOR, AgentId.MARKET),
        capabilities = setOf(
            AgentCapability.SYNTHESIS,
            AgentCapability.STREAMING_OUTPUT
        ),
        networkMode = AgentNetworkMode.USER_OPTIONAL_REMOTE
    )

    /**
     * 执行综合阶段，并将依赖 agent 的结果收敛为最终分析。
     */
    override fun execute(input: AgentInput): Flow<AgentEvent> = flow {
        emit(AgentEvent.Started(spec.id, "Synthesizing final analysis"))

        val indicatorResult = input.context.results[AgentId.INDICATOR]
        val marketResult = input.context.results[AgentId.MARKET]
        val output = engine.synthesize(
            SynthesisInput(
                request = input.request,
                context = input.context,
                indicatorResult = indicatorResult,
                marketResult = marketResult
            )
        )

        emit(
            AgentEvent.Completed(
                agentId = spec.id,
                result = AgentResult(
                    agentId = spec.id,
                    summary = output.summary,
                    confidence = output.confidence,
                    typedOutput = SynthesisAgentOutput(
                        goal = input.request.goal,
                        indicatorSummary = indicatorResult?.summary,
                        marketSummary = marketResult?.summary,
                        warnings = output.warnings
                    ),
                    structuredPayload = output.structuredPayload,
                    warnings = output.warnings
                )
            )
        )
    }
}

/**
 * 一个不依赖外部模型的默认综合引擎。
 */
class RuleBasedSynthesisEngine : SynthesisEngine {
    /**
     * 用规则方式组合指标摘要和市场摘要。
     */
    override suspend fun synthesize(input: SynthesisInput): SynthesisOutput {
        val indicatorSummary = input.indicatorResult?.summary ?: "Technical view is unavailable."
        val marketSummary = input.marketResult?.summary ?: "Market evidence is unavailable."
        val indicatorWarnings = input.indicatorResult?.warnings.orEmpty()
        val marketWarnings = input.marketResult?.warnings.orEmpty()
        val includeWarnings = input.request.config.synthesis.includeWarningsInSummary

        val finalSummary = buildString {
            append(indicatorSummary)
            append(' ')
            append(marketSummary)
            if (includeWarnings && (indicatorWarnings.isNotEmpty() || marketWarnings.isNotEmpty())) {
                append(" Warnings: ")
                append((indicatorWarnings + marketWarnings).joinToString())
                append('.')
            }
        }

        return SynthesisOutput(
            summary = finalSummary,
            confidence = averageConfidence(
                input.indicatorResult?.confidence,
                input.marketResult?.confidence
            ),
            structuredPayload = buildJsonObject {
                put("goal", input.request.goal.name)
                input.indicatorResult?.summary?.let { put("indicatorSummary", it) }
                input.marketResult?.summary?.let { put("marketSummary", it) }
                putJsonArray("warnings") {
                    (indicatorWarnings + marketWarnings).distinct().forEach { add(JsonPrimitive(it)) }
                }
            },
            warnings = (indicatorWarnings + marketWarnings).distinct()
        )
    }

    /**
     * 计算多个阶段置信度的平均值。
     */
    private fun averageConfidence(
        first: Double?,
        second: Double?
    ): Double? {
        val values = listOfNotNull(first, second)
        if (values.isEmpty()) return null
        return values.average()
    }
}
