package io.baiyanwu.coinmonitor.lib.agents

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
/**
 * 标识内置分析系统中的标准 agent。
 */
enum class AgentId {
    INDICATOR,
    MARKET,
    SYNTHESIS
}

@Serializable
/**
 * 描述一次分析请求的主要目标。
 */
enum class AnalysisGoal {
    GENERAL,
    TECHNICAL_BREAKDOWN,
    MARKET_DRIVERS,
    RISK_CHECK,
    CUSTOM
}

@Serializable
/**
 * 描述分析更偏向的时间尺度，用于控制市场搜索范围。
 */
enum class AnalysisHorizon {
    INTRADAY,
    SWING,
    POSITION
}

@Serializable
/**
 * 定义市场情报检索可以扩展到的范围层级。
 */
enum class MarketScope {
    DIRECT,
    ECOSYSTEM,
    SECTOR,
    MACRO
}

@Serializable
/**
 * 描述 agent 的联网边界，供外部运行时做权限控制。
 */
enum class AgentNetworkMode {
    LOCAL_ONLY,
    USER_DIRECT,
    USER_OPTIONAL_REMOTE
}

@Serializable
/**
 * 统一描述待分析资产的最小身份信息。
 */
data class AssetRef(
    val symbol: String,
    val displayName: String? = null,
    val baseSymbol: String? = null,
    val quoteSymbol: String? = null,
    val marketType: String? = null,
    val exchange: String? = null,
    val chainFamily: String? = null,
    val chainId: String? = null,
    val tokenAddress: String? = null,
    val aliases: List<String> = emptyList()
)

@Serializable
/**
 * 平台无关的单根 candle 快照。
 */
data class CandleSnapshot(
    val openTimeMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val confirmed: Boolean = true
)

@Serializable
/**
 * 结构化指标快照，用于在 agent 之间传递中间分析结果。
 */
sealed interface IndicatorValuePayload

@Serializable
/**
 * MA / EMA 这类多周期均线指标的值对象。
 */
data class MovingAverageIndicatorValue(
    val periods: List<Int>,
    val latest: Map<Int, Double>
) : IndicatorValuePayload

@Serializable
/**
 * 布林带的值对象。
 */
data class BollingerIndicatorValue(
    val upper: Double? = null,
    val middle: Double? = null,
    val lower: Double? = null
) : IndicatorValuePayload

@Serializable
/**
 * 成交量指标的值对象。
 */
data class VolumeIndicatorValue(
    val latest: Double,
    val average: Double
) : IndicatorValuePayload

@Serializable
/**
 * MACD 的值对象。
 */
data class MacdIndicatorValue(
    val dif: Double? = null,
    val dea: Double? = null,
    val histogram: Double? = null
) : IndicatorValuePayload

@Serializable
/**
 * RSI 的值对象。
 */
data class RsiIndicatorValue(
    val value: Double? = null
) : IndicatorValuePayload

@Serializable
/**
 * KDJ 的值对象。
 */
data class KdjIndicatorValue(
    val k: Double? = null,
    val d: Double? = null,
    val j: Double? = null
) : IndicatorValuePayload

@Serializable
/**
 * 结构化指标快照，用于在 agent 之间传递中间分析结果。
 */
data class IndicatorSnapshot(
    val indicatorKey: String,
    val summary: String,
    val value: IndicatorValuePayload? = null,
    val extensionPayload: JsonObject = emptyJsonObject(),
    val warnings: List<String> = emptyList()
)

@Serializable
/**
 * 描述某个 agent 对共享上下文的增量更新。
 */
data class SharedContextValues(
    val indicatorSummary: String? = null,
    val marketSummary: String? = null,
    val extensionPayload: JsonObject = emptyJsonObject()
)

@Serializable
/**
 * 描述某个 agent 对共享上下文的增量更新。
 */
data class ContextPatch(
    val indicatorSnapshots: List<IndicatorSnapshot> = emptyList(),
    val marketEvidence: List<MarketEvidence> = emptyList(),
    val sharedContext: SharedContextValues = SharedContextValues()
)

@Serializable
/**
 * 统一承载单个 agent 的最终结果。
 */
sealed interface AgentOutput

@Serializable
/**
 * `IndicatorAgent` 的标准输出。
 */
data class IndicatorAgentOutput(
    val symbol: String,
    val intervalLabel: String,
    val trend: String,
    val momentum: String,
    val volumeState: String,
    val bollPosition: String,
    val kdjSignal: String,
    val support: Double,
    val resistance: Double,
    val distanceToSupportPct: Double,
    val distanceToResistancePct: Double,
    val latestClose: Double,
    val rsi: Double? = null,
    val macdHistogram: Double? = null
) : AgentOutput

@Serializable
/**
 * `MarketAgent` 的标准输出。
 */
data class MarketAgentOutput(
    val scopes: Set<MarketScope>,
    val evidenceCount: Int,
    val sourceCount: Int,
    val selectionMode: MarketSourceSelectionMode,
    val sourceIds: List<String>,
    val sourceCapabilities: Set<MarketSourceCapability>,
    val authRequiredSourceCount: Int,
    val topTitles: List<String>
) : AgentOutput

@Serializable
/**
 * `SynthesisAgent` 的标准输出。
 */
data class SynthesisAgentOutput(
    val goal: AnalysisGoal,
    val indicatorSummary: String? = null,
    val marketSummary: String? = null,
    val warnings: List<String> = emptyList()
) : AgentOutput

@Serializable
/**
 * 统一承载单个 agent 的最终结果。
 */
data class AgentResult(
    val agentId: AgentId,
    val summary: String,
    val confidence: Double? = null,
    val typedOutput: AgentOutput? = null,
    val structuredPayload: JsonObject = emptyJsonObject(),
    val contextPatch: ContextPatch = ContextPatch(),
    val evidenceIds: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val producedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
/**
 * 描述一次完整分析运行所需的输入参数。
 */
data class IndicatorAgentConfig(
    val maPeriods: List<Int> = listOf(7, 25, 99),
    val emaPeriods: List<Int> = listOf(7, 25),
    val bollPeriod: Int = 20,
    val bollMultiplier: Double = 2.0,
    val rsiPeriod: Int = 14,
    val macdShortPeriod: Int = 12,
    val macdLongPeriod: Int = 26,
    val macdSignalPeriod: Int = 9,
    val kdjPeriod: Int = 9,
    val supportResistanceWindow: Int = 20
)

@Serializable
/**
 * 描述市场情报 agent 的请求侧配置。
 */
data class MarketAgentConfig(
    val scopeOverride: Set<MarketScope> = emptySet(),
    val sourcePolicy: MarketSourceSelectionPolicy = MarketSourceSelectionPolicy(),
    val maxTotalEvidence: Int = 12,
    val publishedAfterMillis: Long? = null
)

@Serializable
/**
 * 描述综合分析阶段的行为配置。
 */
data class SynthesisAgentConfig(
    val includeWarningsInSummary: Boolean = true
)

@Serializable
/**
 * 收口一次分析运行中各个 agent 的配置。
 */
data class AnalysisConfig(
    val indicator: IndicatorAgentConfig = IndicatorAgentConfig(),
    val market: MarketAgentConfig = MarketAgentConfig(),
    val synthesis: SynthesisAgentConfig = SynthesisAgentConfig()
)

@Serializable
/**
 * 描述一次完整分析运行所需的输入参数。
 */
data class AnalysisRequest(
    val requestId: String,
    val goal: AnalysisGoal = AnalysisGoal.GENERAL,
    val userPrompt: String,
    val asset: AssetRef,
    val intervalLabel: String,
    val horizon: AnalysisHorizon = AnalysisHorizon.INTRADAY,
    val config: AnalysisConfig = AnalysisConfig(),
    val requiredAgents: Set<AgentId> = setOf(
        AgentId.INDICATOR,
        AgentId.MARKET,
        AgentId.SYNTHESIS
    ),
    val preferredLanguage: String = "zh-CN",
    val allowNetwork: Boolean = true
)

@Serializable
/**
 * 多 agent 共享的运行时上下文。
 */
data class AnalysisContext(
    val request: AnalysisRequest,
    val candles: List<CandleSnapshot> = emptyList(),
    val indicatorSnapshots: List<IndicatorSnapshot> = emptyList(),
    val marketEvidence: List<MarketEvidence> = emptyList(),
    val sharedContext: SharedContextValues = SharedContextValues(),
    val results: Map<AgentId, AgentResult> = emptyMap()
)

/**
 * 将单个 agent 结果并入共享上下文。
 */
fun AnalysisContext.apply(result: AgentResult): AnalysisContext {
    return copy(
        indicatorSnapshots = indicatorSnapshots + result.contextPatch.indicatorSnapshots,
        marketEvidence = marketEvidence + result.contextPatch.marketEvidence,
        sharedContext = mergeSharedContext(sharedContext, result.contextPatch.sharedContext),
        results = results + (result.agentId to result)
    )
}

/**
 * 生成一个空的 JSON 对象，作为结构化字段的默认值。
 */
internal fun emptyJsonObject(): JsonObject = buildJsonObject { }

/**
 * 合并两个浅层 JSON 对象，后者的同名键会覆盖前者。
 */
internal fun mergeJsonObjects(
    first: JsonObject,
    second: JsonObject
): JsonObject = buildJsonObject {
    first.forEach { (key, value) -> put(key, value) }
    second.forEach { (key, value) -> put(key, value) }
}

/**
 * 合并两份 typed shared context，后者优先覆盖前者。
 */
internal fun mergeSharedContext(
    first: SharedContextValues,
    second: SharedContextValues
): SharedContextValues {
    return SharedContextValues(
        indicatorSummary = second.indicatorSummary ?: first.indicatorSummary,
        marketSummary = second.marketSummary ?: first.marketSummary,
        extensionPayload = mergeJsonObjects(first.extensionPayload, second.extensionPayload)
    )
}
