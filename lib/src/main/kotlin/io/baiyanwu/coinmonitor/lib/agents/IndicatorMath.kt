package io.baiyanwu.coinmonitor.lib.agents

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 提供平台无关的基础指标计算函数。
 */
internal object IndicatorMath {
    /**
     * 计算简单移动平均。
     */
    fun sma(values: List<Double>, period: Int): List<Double?> {
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
     * 计算指数移动平均。
     */
    fun ema(values: List<Double>, period: Int): List<Double?> {
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
    fun bollinger(
        values: List<Double>,
        period: Int = 20,
        multiplier: Double = 2.0
    ): BollingerBands {
        val middle = sma(values, period)
        val upper = MutableList<Double?>(values.size) { null }
        val lower = MutableList<Double?>(values.size) { null }
        values.indices.forEach { index ->
            if (index < period - 1) return@forEach
            val window = values.subList(index - period + 1, index + 1)
            val average = middle[index] ?: return@forEach
            val variance = window.sumOf { (it - average).pow(2) } / period
            val deviation = sqrt(variance)
            upper[index] = average + deviation * multiplier
            lower[index] = average - deviation * multiplier
        }
        return BollingerBands(
            upper = upper,
            middle = middle,
            lower = lower
        )
    }

    /**
     * 计算 MACD 三元组。
     */
    fun macd(
        values: List<Double>,
        shortPeriod: Int = 12,
        longPeriod: Int = 26,
        signalPeriod: Int = 9
    ): MacdResult {
        val short = ema(values, shortPeriod)
        val long = ema(values, longPeriod)
        val dif = values.indices.map { index ->
            val shortValue = short.getOrNull(index)
            val longValue = long.getOrNull(index)
            if (shortValue == null || longValue == null) null else shortValue - longValue
        }
        val dea = emaNullable(dif, signalPeriod)
        val histogram = dif.indices.map { index ->
            val difValue = dif[index]
            val deaValue = dea[index]
            if (difValue == null || deaValue == null) null else (difValue - deaValue) * 2
        }
        return MacdResult(dif, dea, histogram)
    }

    /**
     * 计算单条 RSI。
     */
    fun rsi(
        values: List<Double>,
        period: Int = 14
    ): List<Double?> {
        if (values.isEmpty() || period <= 0) return emptyList()
        val result = MutableList<Double?>(values.size) { null }
        var avgGain = 0.0
        var avgLoss = 0.0
        values.indices.drop(1).forEach { index ->
            val change = values[index] - values[index - 1]
            val gain = change.coerceAtLeast(0.0)
            val loss = (-change).coerceAtLeast(0.0)
            if (index <= period) {
                avgGain += gain
                avgLoss += loss
                if (index == period) {
                    avgGain /= period
                    avgLoss /= period
                    result[index] = computeRsi(avgGain, avgLoss)
                }
            } else {
                avgGain = ((avgGain * (period - 1)) + gain) / period
                avgLoss = ((avgLoss * (period - 1)) + loss) / period
                result[index] = computeRsi(avgGain, avgLoss)
            }
        }
        return result
    }

    /**
     * 计算 KDJ 三元组。
     */
    fun kdj(
        candles: List<CandleSnapshot>,
        period: Int = 9,
        kSmoothing: Int = 3,
        dSmoothing: Int = 3
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
            val rsv = if (abs(highest - lowest) < 1e-9) {
                50.0
            } else {
                ((candles[index].close - lowest) / (highest - lowest)) * 100.0
            }
            val currentK = kWeight * previousK + (1.0 - kWeight) * rsv
            val currentD = dWeight * previousD + (1.0 - dWeight) * currentK
            val currentJ = (3 * currentK) - (2 * currentD)
            kValues[index] = currentK
            dValues[index] = currentD
            jValues[index] = currentJ
            previousK = currentK
            previousD = currentD
        }
        return KdjResult(kValues, dValues, jValues)
    }

    private fun emaNullable(values: List<Double?>, period: Int): List<Double?> {
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

    private fun computeRsi(avgGain: Double, avgLoss: Double): Double {
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }
}

/**
 * 布林带计算结果。
 */
internal data class BollingerBands(
    val upper: List<Double?>,
    val middle: List<Double?>,
    val lower: List<Double?>
)

/**
 * MACD 计算结果。
 */
internal data class MacdResult(
    val dif: List<Double?>,
    val dea: List<Double?>,
    val histogram: List<Double?>
)

/**
 * KDJ 计算结果。
 */
internal data class KdjResult(
    val k: List<Double?>,
    val d: List<Double?>,
    val j: List<Double?>
)
