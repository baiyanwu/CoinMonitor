package io.baiyanwu.coinmonitor.ui.kline.chart

import io.baiyanwu.coinmonitor.domain.model.CandleEntry
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * K 线指标计算器。
 *
 * 当前把常见指标的计算逻辑统一放在这一处，避免散落在 UI 或图表库适配层里，
 * 后续不管继续使用第三方库还是迁移到自研库，都可以直接复用这套结果。
 */
object KlineIndicatorCalculator {

    /**
     * 对任意数值序列计算简单移动平均。
     */
    fun simpleMovingAverageValues(values: List<Double>, period: Int): List<Double?> {
        if (values.isEmpty() || period <= 0) return emptyList()
        val result = MutableList<Double?>(values.size) { null }
        var rollingSum = 0.0
        values.forEachIndexed { index, value ->
            rollingSum += value
            if (index >= period) {
                rollingSum -= values[index - period]
            }
            if (index >= period - 1) {
                result[index] = rollingSum / period
            }
        }
        return result
    }

    /**
     * 计算简单移动平均线。
     */
    fun simpleMovingAverage(candles: List<CandleEntry>, period: Int): List<Double?> {
        return simpleMovingAverageValues(candles.map { it.close }, period)
    }

    /**
     * 计算指数移动平均线。
     */
    fun exponentialMovingAverage(candles: List<CandleEntry>, period: Int): List<Double?> {
        return exponentialMovingAverageValues(candles.map { it.close }, period)
    }

    /**
     * 对任意数值序列计算指数移动平均。
     */
    fun exponentialMovingAverageValues(values: List<Double>, period: Int): List<Double?> {
        if (values.isEmpty() || period <= 0) return emptyList()
        val result = MutableList<Double?>(values.size) { null }
        val multiplier = 2.0 / (period + 1)
        var previous: Double? = null
        values.forEachIndexed { index, value ->
            previous = if (previous == null) {
                value
            } else {
                (value - previous!!) * multiplier + previous!!
            }
            if (index >= period - 1) {
                result[index] = previous
            }
        }
        return result
    }

    /**
     * 计算布林带三轨。
     */
    fun bollingerBands(
        candles: List<CandleEntry>,
        period: Int = 20,
        multiplier: Double = 2.0
    ): BollingerBands {
        val middle = simpleMovingAverage(candles, period)
        val upper = MutableList<Double?>(candles.size) { null }
        val lower = MutableList<Double?>(candles.size) { null }
        candles.indices.forEach { index ->
            if (index < period - 1) return@forEach
            val window = candles.subList(index - period + 1, index + 1)
            val average = middle[index] ?: return@forEach
            val variance = window.sumOf { (it.close - average).pow(2) } / period
            val deviation = sqrt(variance)
            upper[index] = average + deviation * multiplier
            lower[index] = average - deviation * multiplier
        }
        return BollingerBands(upper = upper, middle = middle, lower = lower)
    }

    /**
     * 计算 MACD。
     */
    fun macd(
        candles: List<CandleEntry>,
        shortPeriod: Int = 12,
        longPeriod: Int = 26,
        signalPeriod: Int = 9
    ): MacdResult {
        val emaShort = exponentialMovingAverage(candles, shortPeriod)
        val emaLong = exponentialMovingAverage(candles, longPeriod)
        val dif = candles.indices.map { index ->
            val shortValue = emaShort.getOrNull(index)
            val longValue = emaLong.getOrNull(index)
            if (shortValue == null || longValue == null) null else shortValue - longValue
        }
        val dea = exponentialMovingAverageFromValues(dif, signalPeriod)
        val histogram = dif.indices.map { index ->
            val difValue = dif[index]
            val deaValue = dea[index]
            if (difValue == null || deaValue == null) null else (difValue - deaValue) * 2
        }
        return MacdResult(dif = dif, dea = dea, histogram = histogram)
    }

    /**
     * 计算 RSI，默认给出 6 / 12 / 24 三条线。
     */
    fun rsi(candles: List<CandleEntry>): RsiResult {
        return RsiResult(
            rsi6 = relativeStrengthIndex(candles, 6),
            rsi12 = relativeStrengthIndex(candles, 12),
            rsi24 = relativeStrengthIndex(candles, 24)
        )
    }

    /**
     * 计算单条 RSI。
     */
    fun rsi(candles: List<CandleEntry>, period: Int): List<Double?> {
        return relativeStrengthIndex(candles, period)
    }

    /**
     * 计算 KDJ。
     */
    fun kdj(candles: List<CandleEntry>, period: Int = 9): KdjResult {
        return kdj(
            candles = candles,
            period = period,
            kSmoothing = 3,
            dSmoothing = 3
        )
    }

    /**
     * 按指定平滑周期计算 KDJ。
     */
    fun kdj(
        candles: List<CandleEntry>,
        period: Int,
        kSmoothing: Int,
        dSmoothing: Int
    ): KdjResult {
        if (candles.isEmpty()) return KdjResult(emptyList(), emptyList(), emptyList())
        val kValues = MutableList<Double?>(candles.size) { null }
        val dValues = MutableList<Double?>(candles.size) { null }
        val jValues = MutableList<Double?>(candles.size) { null }
        var previousK = 50.0
        var previousD = 50.0
        val kWeight = ((kSmoothing - 1).coerceAtLeast(1)).toDouble() / kSmoothing.coerceAtLeast(1)
        val dWeight = ((dSmoothing - 1).coerceAtLeast(1)).toDouble() / dSmoothing.coerceAtLeast(1)
        candles.indices.forEach { index ->
            val fromIndex = (index - period + 1).coerceAtLeast(0)
            val window = candles.subList(fromIndex, index + 1)
            val highest = window.maxOf { it.high }
            val lowest = window.minOf { it.low }
            val rsv = if (highest == lowest) {
                50.0
            } else {
                ((candles[index].close - lowest) / (highest - lowest)) * 100
            }
            val currentK = kWeight * previousK + (1.0 - kWeight) * rsv
            val currentD = dWeight * previousD + (1.0 - dWeight) * currentK
            val currentJ = 3 * currentK - 2 * currentD
            kValues[index] = currentK
            dValues[index] = currentD
            jValues[index] = currentJ
            previousK = currentK
            previousD = currentD
        }
        return KdjResult(k = kValues, d = dValues, j = jValues)
    }

    private fun exponentialMovingAverageFromValues(
        values: List<Double?>,
        period: Int
    ): List<Double?> {
        val result = MutableList<Double?>(values.size) { null }
        val multiplier = 2.0 / (period + 1)
        var previous: Double? = null
        values.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            previous = if (previous == null) {
                value
            } else {
                (value - previous!!) * multiplier + previous!!
            }
            result[index] = previous
        }
        return result
    }

    private fun relativeStrengthIndex(
        candles: List<CandleEntry>,
        period: Int
    ): List<Double?> {
        if (candles.isEmpty() || period <= 0) return emptyList()
        val result = MutableList<Double?>(candles.size) { null }
        var avgGain = 0.0
        var avgLoss = 0.0
        candles.indices.drop(1).forEach { index ->
            val change = candles[index].close - candles[index - 1].close
            val gain = change.coerceAtLeast(0.0)
            val loss = (-change).coerceAtLeast(0.0)
            if (index <= period) {
                avgGain += gain
                avgLoss += loss
                if (index == period) {
                    avgGain /= period
                    avgLoss /= period
                    result[index] = computeRsiValue(avgGain, avgLoss)
                }
            } else {
                avgGain = ((avgGain * (period - 1)) + gain) / period
                avgLoss = ((avgLoss * (period - 1)) + loss) / period
                result[index] = computeRsiValue(avgGain, avgLoss)
            }
        }
        return result
    }

    private fun computeRsiValue(avgGain: Double, avgLoss: Double): Double {
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100 - (100 / (1 + rs))
    }
}

/**
 * 布林带结果。
 */
data class BollingerBands(
    val upper: List<Double?>,
    val middle: List<Double?>,
    val lower: List<Double?>
)

/**
 * MACD 结果。
 */
data class MacdResult(
    val dif: List<Double?>,
    val dea: List<Double?>,
    val histogram: List<Double?>
)

/**
 * RSI 结果。
 */
data class RsiResult(
    val rsi6: List<Double?>,
    val rsi12: List<Double?>,
    val rsi24: List<Double?>
)

/**
 * KDJ 结果。
 */
data class KdjResult(
    val k: List<Double?>,
    val d: List<Double?>,
    val j: List<Double?>
)
