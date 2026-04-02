package io.baiyanwu.coinmonitor.lib.agents

/**
 * 宿主侧请求构造器，避免直接手写底层 `AnalysisRequest` 和 `AnalysisContext`。
 */
class AnalysisRequestBuilder {
    private var requestId: String = "req-${System.currentTimeMillis()}"
    private var goal: AnalysisGoal = AnalysisGoal.GENERAL
    private var userPrompt: String = ""
    private var asset: AssetRef? = null
    private var intervalLabel: String = "1H"
    private var horizon: AnalysisHorizon = AnalysisHorizon.INTRADAY
    private var config: AnalysisConfig = AnalysisConfig()
    private var requiredAgents: Set<AgentId> = setOf(
        AgentId.INDICATOR,
        AgentId.MARKET,
        AgentId.SYNTHESIS
    )
    private var preferredLanguage: String = "zh-CN"
    private var allowNetwork: Boolean = true
    private var candles: List<CandleSnapshot> = emptyList()
    private var indicatorSnapshots: List<IndicatorSnapshot> = emptyList()
    private var marketEvidence: List<MarketEvidence> = emptyList()
    private var sharedContext: SharedContextValues = SharedContextValues()
    private var runtimeOptions: AnalysisRuntimeOptions = AnalysisRuntimeOptions()

    /**
     * 设置请求 id。
     */
    fun requestId(value: String) = apply {
        requestId = value
    }

    /**
     * 设置分析目标。
     */
    fun goal(value: AnalysisGoal) = apply {
        goal = value
    }

    /**
     * 设置用户问题。
     */
    fun userPrompt(value: String) = apply {
        userPrompt = value
    }

    /**
     * 设置待分析资产。
     */
    fun asset(value: AssetRef) = apply {
        asset = value
    }

    /**
     * 设置周期标签。
     */
    fun intervalLabel(value: String) = apply {
        intervalLabel = value
    }

    /**
     * 设置分析周期偏向。
     */
    fun horizon(value: AnalysisHorizon) = apply {
        horizon = value
    }

    /**
     * 设置整套分析配置。
     */
    fun config(value: AnalysisConfig) = apply {
        config = value
    }

    /**
     * 设置需要执行的 agent 集合。
     */
    fun requiredAgents(value: Set<AgentId>) = apply {
        requiredAgents = value
    }

    /**
     * 设置输出语言偏好。
     */
    fun preferredLanguage(value: String) = apply {
        preferredLanguage = value
    }

    /**
     * 设置是否允许联网。
     */
    fun allowNetwork(value: Boolean) = apply {
        allowNetwork = value
    }

    /**
     * 设置 candle 序列。
     */
    fun candles(value: List<CandleSnapshot>) = apply {
        candles = value
    }

    /**
     * 设置初始指标快照。
     */
    fun indicatorSnapshots(value: List<IndicatorSnapshot>) = apply {
        indicatorSnapshots = value
    }

    /**
     * 设置初始市场证据。
     */
    fun marketEvidence(value: List<MarketEvidence>) = apply {
        marketEvidence = value
    }

    /**
     * 设置初始共享上下文。
     */
    fun sharedContext(value: SharedContextValues) = apply {
        sharedContext = value
    }

    /**
     * 设置运行时选项。
     */
    fun runtimeOptions(value: AnalysisRuntimeOptions) = apply {
        runtimeOptions = value
    }

    /**
     * 构建宿主可直接执行的分析输入。
     */
    fun build(): AnalysisRunInput {
        val resolvedAsset = requireNotNull(asset) {
            "Asset must be provided before building AnalysisRunInput."
        }
        val request = AnalysisRequest(
            requestId = requestId,
            goal = goal,
            userPrompt = userPrompt,
            asset = resolvedAsset,
            intervalLabel = intervalLabel,
            horizon = horizon,
            config = config,
            requiredAgents = requiredAgents,
            preferredLanguage = preferredLanguage,
            allowNetwork = allowNetwork
        )
        return AnalysisRunInput(
            request = request,
            seedContext = AnalysisContext(
                request = request,
                candles = candles,
                indicatorSnapshots = indicatorSnapshots,
                marketEvidence = marketEvidence,
                sharedContext = sharedContext
            ),
            runtimeOptions = runtimeOptions
        )
    }
}
