package io.baiyanwu.coinmonitor.lib.agents

/**
 * 组装一套默认可用的 agent 集合。
 */
class DefaultAgentSet(
    marketSourceAdapters: List<MarketSourceAdapter> = emptyList(),
    synthesisEngine: SynthesisEngine = RuleBasedSynthesisEngine()
) {
    val indicatorAgent: AnalysisAgent = DefaultIndicatorAgent()
    val marketAgent: AnalysisAgent = DefaultMarketAgent(marketSourceAdapters)
    val synthesisAgent: AnalysisAgent = DefaultSynthesisAgent(synthesisEngine)

    /**
     * 返回适用于默认执行图的 agent 注册表。
     */
    fun registry(): AgentRegistry {
        return AgentRegistry(
            listOf(
                indicatorAgent,
                marketAgent,
                synthesisAgent
            )
        )
    }
}
