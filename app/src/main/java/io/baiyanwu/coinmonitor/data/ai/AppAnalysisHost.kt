package io.baiyanwu.coinmonitor.data.ai

import io.baiyanwu.coinmonitor.domain.model.AiAnalysisOption
import io.baiyanwu.coinmonitor.domain.model.CandleEntry
import io.baiyanwu.coinmonitor.domain.model.KlineIndicatorSettings
import io.baiyanwu.coinmonitor.domain.model.KlineInterval
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.lib.agents.AgentId
import io.baiyanwu.coinmonitor.lib.agents.AnalysisConfig
import io.baiyanwu.coinmonitor.lib.agents.AnalysisGoal
import io.baiyanwu.coinmonitor.lib.agents.AnalysisRequestBuilder
import io.baiyanwu.coinmonitor.lib.agents.AnalysisRunResult
import io.baiyanwu.coinmonitor.lib.agents.AnalysisService
import io.baiyanwu.coinmonitor.lib.agents.AssetRef
import io.baiyanwu.coinmonitor.lib.agents.CandleSnapshot
import io.baiyanwu.coinmonitor.lib.agents.IndicatorAgentConfig
import io.baiyanwu.coinmonitor.lib.agents.MarketAgentConfig
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceOverride
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceSelectionMode
import io.baiyanwu.coinmonitor.lib.agents.MarketSourceSelectionPolicy

/**
 * 负责把 app 内部模型映射到 `lib` 的统一分析入口。
 */
class AppAnalysisHost(
    private val analysisService: AnalysisService = AnalysisService()
) {
    /**
     * 基于当前标的、周期和 candles 运行一次完整分析。
     */
    suspend fun analyze(
        item: WatchItem,
        interval: KlineInterval,
        indicatorSettings: KlineIndicatorSettings,
        analysisOptions: Set<AiAnalysisOption>,
        candles: List<CandleEntry>,
        userPrompt: String
    ): AnalysisRunResult {
        val requiredAgents = analysisOptions.toRequiredAgents()
        val input = AnalysisRequestBuilder()
            .requestId("app-ai-${System.currentTimeMillis()}")
            .goal(AnalysisGoal.GENERAL)
            .userPrompt(userPrompt)
            .asset(item.toAnalysisAsset())
            .intervalLabel(interval.label)
            .config(
                AnalysisConfig(
                    indicator = indicatorSettings.toIndicatorAgentConfig(),
                    market = MarketAgentConfig(
                        sourcePolicy = analysisOptions.toMarketSourcePolicy()
                    )
                )
            )
            .allowNetwork(analysisOptions.any { it.isMarketSource })
            .requiredAgents(requiredAgents)
            .candles(candles.map { it.toAnalysisCandle() })
            .build()

        return analysisService.run(input)
    }

    /**
     * 将 `lib` 输出结果转成适合放进大模型系统 prompt 的上下文块。
     */
    fun formatPromptContext(
        item: WatchItem,
        interval: KlineInterval,
        analysis: AnalysisRunResult
    ): String {
        val indicatorSummary = analysis.finalContext.sharedContext.indicatorSummary
        val marketSummary = analysis.finalContext.sharedContext.marketSummary
        val finalSummary = analysis.finalResult?.summary

        return buildString {
            appendLine("Analysis Context:")
            append("Asset: ").append(item.symbol)
            item.name.takeIf { it.isNotBlank() }?.let { append(" (").append(it).append(')') }
            appendLine()
            append("Interval: ").append(interval.label).appendLine()
            item.lastPrice?.let { append("Latest price: ").append(it).appendLine() }
            item.change24hPercent?.let { append("24h change: ").append(it).append("%").appendLine() }
            finalSummary?.takeIf { it.isNotBlank() }?.let {
                append("Final analysis summary: ").append(it).appendLine()
            }
            indicatorSummary?.takeIf { it.isNotBlank() }?.let {
                append("Indicator summary: ").append(it).appendLine()
            }
            marketSummary
                ?.takeIf { it.isNotBlank() }
                ?.takeIf { !it.startsWith("No market source adapters") && !it.startsWith("No relevant low-noise market evidence") }
                ?.let {
                    append("Market summary: ").append(it).appendLine()
                }
        }.trim()
    }
}

private fun WatchItem.toAnalysisAsset(): AssetRef {
    return AssetRef(
        symbol = symbol,
        displayName = name,
        baseSymbol = baseSymbol,
        marketType = marketType.name,
        exchange = exchangeSource.name,
        chainFamily = chainFamily?.name,
        chainId = chainIndex,
        tokenAddress = tokenAddress,
        aliases = listOfNotNull(name.takeIf { it.isNotBlank() })
    )
}

private fun Set<AiAnalysisOption>.toRequiredAgents(): Set<AgentId> {
    val includeIndicator = any { it.isIndicator }
    val includeMarket = any { it.isMarketSource }
    return buildSet {
        if (includeIndicator) add(AgentId.INDICATOR)
        if (includeMarket) add(AgentId.MARKET)
        if (includeIndicator && includeMarket) add(AgentId.SYNTHESIS)
    }
}

private fun Set<AiAnalysisOption>.toMarketSourcePolicy(): MarketSourceSelectionPolicy {
    return MarketSourceSelectionPolicy(
        selectionMode = MarketSourceSelectionMode.EXPLICIT_ONLY,
        sourceOverrides = asSequence()
            .mapNotNull { option -> option.sourceId }
            .mapIndexed { index, sourceId ->
                MarketSourceOverride(
                    sourceId = sourceId,
                    enabled = true,
                    priority = index
                )
            }
            .toList()
    )
}

private fun CandleEntry.toAnalysisCandle(): CandleSnapshot {
    return CandleSnapshot(
        openTimeMillis = openTimeMillis,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume,
        confirmed = isConfirmed
    )
}

private fun KlineIndicatorSettings.toIndicatorAgentConfig(): IndicatorAgentConfig {
    return IndicatorAgentConfig(
        maPeriods = ma.lines.mapNotNull { config -> config.period?.takeIf { config.enabled } }.ifEmpty { listOf(7, 25, 99) },
        emaPeriods = ema.lines.mapNotNull { config -> config.period?.takeIf { config.enabled } }.ifEmpty { listOf(7, 25) },
        bollPeriod = boll.period,
        bollMultiplier = boll.width,
        rsiPeriod = rsi.lines.firstOrNull { it.enabled }?.period ?: 14,
        macdShortPeriod = macd.shortPeriod,
        macdLongPeriod = macd.longPeriod,
        macdSignalPeriod = macd.signalPeriod,
        kdjPeriod = kdj.period,
        supportResistanceWindow = 20
    )
}
