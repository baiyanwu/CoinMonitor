package io.baiyanwu.coinmonitor.lib.agents

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 默认技术面分析 agent。
 */
class DefaultIndicatorAgent : AnalysisAgent {
    override val spec: AgentSpec = AgentSpec(
        id = AgentId.INDICATOR,
        displayName = "Indicator Agent",
        description = "Derives technical snapshots from candles and typed indicator config.",
        capabilities = setOf(
            AgentCapability.INDICATOR_ANALYSIS,
            AgentCapability.STREAMING_OUTPUT
        ),
        networkMode = AgentNetworkMode.LOCAL_ONLY
    )

    /**
     * 根据 candle 与参数生成一组技术面快照。
     */
    override fun execute(input: AgentInput): Flow<AgentEvent> = flow {
        emit(AgentEvent.Started(spec.id, "Computing technical indicator snapshots"))

        val candles = input.context.candles.ifEmpty {
            emptyList()
        }.filter { it.confirmed }.ifEmpty { input.context.candles }

        if (candles.size < 5) {
            emit(
                AgentEvent.Completed(
                    agentId = spec.id,
                    result = AgentResult(
                        agentId = spec.id,
                        summary = "Not enough candle data to produce a technical analysis.",
                        confidence = 0.1,
                        warnings = listOf("At least 5 candles are recommended for indicator analysis.")
                    )
                )
            )
            return@flow
        }

        val analyzer = IndicatorAnalyzer(input.request.config.indicator)
        val result = analyzer.analyze(
            asset = input.request.asset,
            intervalLabel = input.request.intervalLabel,
            candles = candles
        )
        emit(
            AgentEvent.Completed(
                agentId = spec.id,
                result = result
            )
        )
    }
}

/**
 * 将请求参数解释为指标计算配置，并产出统一结果。
 */
private class IndicatorAnalyzer(
    private val config: IndicatorAgentConfig
) {
    private val maPeriods = config.maPeriods
    private val emaPeriods = config.emaPeriods
    private val bollPeriod = config.bollPeriod
    private val bollMultiplier = config.bollMultiplier
    private val rsiPeriod = config.rsiPeriod
    private val macdShort = config.macdShortPeriod
    private val macdLong = config.macdLongPeriod
    private val macdSignal = config.macdSignalPeriod
    private val kdjPeriod = config.kdjPeriod
    private val supportResistanceWindow = config.supportResistanceWindow

    /**
     * 对当前资产和 candle 序列执行完整技术面分析。
     */
    fun analyze(
        asset: AssetRef,
        intervalLabel: String,
        candles: List<CandleSnapshot>
    ): AgentResult {
        val closes = candles.map { it.close }
        val volumes = candles.map { it.volume }
        val lastCandle = candles.last()
        val previousClose = closes.getOrElse((closes.size - 2).coerceAtLeast(0)) { closes.last() }
        val avgVolume = volumes.average().takeUnless { it.isNaN() } ?: 0.0
        val recentWindow = candles.takeLast(supportResistanceWindow.coerceAtLeast(5))
        val support = recentWindow.minOf { it.low }
        val resistance = recentWindow.maxOf { it.high }

        val maValues = maPeriods.associateWith { period ->
            IndicatorMath.sma(closes, period)
        }
        val emaValues = emaPeriods.associateWith { period ->
            IndicatorMath.ema(closes, period)
        }
        val boll = IndicatorMath.bollinger(
            values = closes,
            period = bollPeriod,
            multiplier = bollMultiplier
        )
        val macd = IndicatorMath.macd(
            values = closes,
            shortPeriod = macdShort,
            longPeriod = macdLong,
            signalPeriod = macdSignal
        )
        val rsi = IndicatorMath.rsi(
            values = closes,
            period = rsiPeriod
        )
        val kdj = IndicatorMath.kdj(
            candles = candles,
            period = kdjPeriod
        )

        val latestMa = maValues.mapValues { (_, values) -> values.lastOrNull() }
        val latestEma = emaValues.mapValues { (_, values) -> values.lastOrNull() }
        val latestBollMiddle = boll.middle.lastOrNull()
        val latestBollUpper = boll.upper.lastOrNull()
        val latestBollLower = boll.lower.lastOrNull()
        val latestMacdDif = macd.dif.lastOrNull()
        val latestMacdDea = macd.dea.lastOrNull()
        val latestHistogram = macd.histogram.lastOrNull()
        val latestRsi = rsi.lastOrNull()
        val latestK = kdj.k.lastOrNull()
        val latestD = kdj.d.lastOrNull()
        val latestJ = kdj.j.lastOrNull()

        val trendLabel = resolveTrend(
            lastClose = lastCandle.close,
            maReference = latestMa[maPeriods.firstOrNull()],
            emaReference = latestEma[emaPeriods.firstOrNull()],
            previousClose = previousClose
        )
        val momentumLabel = resolveMomentum(
            macdHistogram = latestHistogram,
            rsi = latestRsi
        )
        val volumeLabel = resolveVolume(
            latestVolume = lastCandle.volume,
            averageVolume = avgVolume
        )
        val bollPosition = resolveBollPosition(
            close = lastCandle.close,
            upper = latestBollUpper,
            lower = latestBollLower
        )
        val kdjSignal = resolveKdjSignal(
            k = latestK,
            d = latestD
        )
        val distanceToSupport = percentageDistance(
            value = lastCandle.close,
            reference = support
        )
        val distanceToResistance = percentageDistance(
            value = resistance,
            reference = lastCandle.close
        )

        val snapshots = buildList {
            add(
                IndicatorSnapshot(
                    indicatorKey = "MA",
                    summary = "Price is $trendLabel against MA bands.",
                    value = MovingAverageIndicatorValue(
                        periods = maPeriods,
                        latest = latestMa.filterValues { it != null }.mapValues { it.value!! }
                    )
                )
            )
            add(
                IndicatorSnapshot(
                    indicatorKey = "EMA",
                    summary = "Fast EMA alignment is $trendLabel.",
                    value = MovingAverageIndicatorValue(
                        periods = emaPeriods,
                        latest = latestEma.filterValues { it != null }.mapValues { it.value!! }
                    )
                )
            )
            add(
                IndicatorSnapshot(
                    indicatorKey = "BOLL",
                    summary = "Price is $bollPosition within Bollinger Bands.",
                    value = BollingerIndicatorValue(
                        upper = latestBollUpper,
                        middle = latestBollMiddle,
                        lower = latestBollLower
                    )
                )
            )
            add(
                IndicatorSnapshot(
                    indicatorKey = "VOL",
                    summary = "Volume is currently $volumeLabel.",
                    value = VolumeIndicatorValue(
                        latest = lastCandle.volume,
                        average = avgVolume
                    )
                )
            )
            add(
                IndicatorSnapshot(
                    indicatorKey = "MACD",
                    summary = "Momentum is $momentumLabel on MACD.",
                    value = MacdIndicatorValue(
                        dif = latestMacdDif,
                        dea = latestMacdDea,
                        histogram = latestHistogram
                    )
                )
            )
            add(
                IndicatorSnapshot(
                    indicatorKey = "RSI",
                    summary = "RSI indicates ${resolveRsiState(latestRsi)}.",
                    value = RsiIndicatorValue(
                        value = latestRsi
                    )
                )
            )
            add(
                IndicatorSnapshot(
                    indicatorKey = "KDJ",
                    summary = "KDJ signal is $kdjSignal.",
                    value = KdjIndicatorValue(
                        k = latestK,
                        d = latestD,
                        j = latestJ
                    )
                )
            )
        }

        val summary = buildString {
            append(asset.symbol)
            append(" on ")
            append(intervalLabel)
            append(" is ")
            append(trendLabel)
            append(", momentum is ")
            append(momentumLabel)
            append(", volume is ")
            append(volumeLabel)
            append(", with support near ")
            append(formatDouble(support))
            append(" and resistance near ")
            append(formatDouble(resistance))
            append('.')
        }

        return AgentResult(
            agentId = AgentId.INDICATOR,
            summary = summary,
            confidence = calculateConfidence(candles.size),
            typedOutput = IndicatorAgentOutput(
                symbol = asset.symbol,
                intervalLabel = intervalLabel,
                trend = trendLabel,
                momentum = momentumLabel,
                volumeState = volumeLabel,
                bollPosition = bollPosition,
                kdjSignal = kdjSignal,
                support = support,
                resistance = resistance,
                distanceToSupportPct = distanceToSupport,
                distanceToResistancePct = distanceToResistance,
                latestClose = lastCandle.close,
                rsi = latestRsi,
                macdHistogram = latestHistogram
            ),
            structuredPayload = buildJsonObject {
                put("symbol", asset.symbol)
                put("interval", intervalLabel)
                put("trend", trendLabel)
                put("momentum", momentumLabel)
                put("volume", volumeLabel)
                put("bollPosition", bollPosition)
                put("kdjSignal", kdjSignal)
                put("support", support)
                put("resistance", resistance)
                put("distanceToSupportPct", distanceToSupport)
                put("distanceToResistancePct", distanceToResistance)
                put("latestClose", lastCandle.close)
                latestRsi?.let { put("rsi", it) }
                latestHistogram?.let { put("macdHistogram", it) }
            },
            contextPatch = ContextPatch(
                indicatorSnapshots = snapshots,
                sharedContext = SharedContextValues(
                    indicatorSummary = summary
                )
            )
        )
    }

}

/**
 * 根据价格相对均线位置和最近收盘变化给出趋势标签。
 */
private fun resolveTrend(
    lastClose: Double,
    maReference: Double?,
    emaReference: Double?,
    previousClose: Double
): String {
    val positiveBias = sequenceOf(maReference, emaReference).filterNotNull().count { lastClose >= it }
    return when {
        positiveBias >= 2 && lastClose >= previousClose -> "bullish"
        positiveBias == 0 && lastClose < previousClose -> "bearish"
        else -> "mixed"
    }
}

/**
 * 根据 MACD 与 RSI 的组合给出动量标签。
 */
private fun resolveMomentum(
    macdHistogram: Double?,
    rsi: Double?
): String {
    return when {
        (macdHistogram ?: 0.0) > 0 && (rsi ?: 50.0) >= 55.0 -> "strengthening"
        (macdHistogram ?: 0.0) < 0 && (rsi ?: 50.0) <= 45.0 -> "weakening"
        else -> "neutral"
    }
}

/**
 * 根据当前量能相对均量的变化给出量能标签。
 */
private fun resolveVolume(
    latestVolume: Double,
    averageVolume: Double
): String {
    if (averageVolume <= 0.0) return "unavailable"
    val ratio = latestVolume / averageVolume
    return when {
        ratio >= 1.5 -> "expanding"
        ratio <= 0.7 -> "contracting"
        else -> "stable"
    }
}

/**
 * 判断当前价格位于布林带的哪个区域。
 */
private fun resolveBollPosition(
    close: Double,
    upper: Double?,
    lower: Double?
): String {
    if (upper == null || lower == null) return "unknown"
    return when {
        close >= upper -> "near upper band"
        close <= lower -> "near lower band"
        else -> "inside bands"
    }
}

/**
 * 将 RSI 数值映射为更易读的状态文案。
 */
private fun resolveRsiState(rsi: Double?): String {
    val value = rsi ?: return "unknown conditions"
    return when {
        value >= 70.0 -> "overbought conditions"
        value <= 30.0 -> "oversold conditions"
        else -> "balanced conditions"
    }
}

/**
 * 根据 K 和 D 的相对位置给出 KDJ 信号。
 */
private fun resolveKdjSignal(
    k: Double?,
    d: Double?
): String {
    if (k == null || d == null) return "unknown"
    return if (k >= d) "bullish crossover bias" else "bearish crossover bias"
}

/**
 * 计算两个价格之间的百分比距离。
 */
private fun percentageDistance(
    value: Double,
    reference: Double
): Double {
    if (reference == 0.0) return 0.0
    return ((value - reference) / reference) * 100.0
}

/**
 * 依据样本数量给一个粗略的技术面置信度。
 */
private fun calculateConfidence(candleCount: Int): Double {
    return when {
        candleCount >= 120 -> 0.85
        candleCount >= 60 -> 0.75
        candleCount >= 20 -> 0.6
        else -> 0.4
    }
}

/**
 * 统一格式化浮点价格，便于摘要输出。
 */
private fun formatDouble(value: Double): String = "%.4f".format(value)
