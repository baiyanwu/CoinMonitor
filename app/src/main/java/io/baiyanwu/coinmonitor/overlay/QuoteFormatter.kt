package io.baiyanwu.coinmonitor.overlay

import java.math.BigDecimal
import java.text.DecimalFormat
import kotlin.math.abs

object QuoteFormatter {
    fun formatPrice(value: Double?): String {
        if (value == null) return "--"
        if (!value.isFinite()) return "--"

        val pattern = when {
            value >= 1000 -> "0.0"
            value >= 100 -> "0.00"
            value >= 1 -> "0.000"
            value >= 0.01 -> "0.0000"
            value >= 0.001 -> "0.00000"
            else -> null
        }
        if (pattern != null) {
            return DecimalFormat(pattern).format(value)
        }

        return formatTinyPrice(value)
    }

    fun formatChange(value: Double?): String {
        if (value == null) return "--"
        val formatted = DecimalFormat("0.00").format(abs(value))
        return if (value >= 0) "+$formatted%" else "-$formatted%"
    }

    /**
     * 链上报价经常出现大量前导 0，这里压缩成 TradingView 类似的可读格式：
     * 0.000001234 -> 0.0₅1234
     */
    private fun formatTinyPrice(value: Double): String {
        val absoluteValue = abs(value)
        if (absoluteValue == 0.0) return "0.0"

        val sign = if (value < 0) "-" else ""
        val plainText = BigDecimal.valueOf(absoluteValue).stripTrailingZeros().toPlainString()
        val decimalPart = plainText.substringAfter('.', missingDelimiterValue = "")
        val leadingZeroCount = decimalPart.takeWhile { it == '0' }.length
        if (leadingZeroCount < 4) {
            return sign + DecimalFormat("0.00000000").format(absoluteValue)
        }

        val significantDigits = decimalPart
            .drop(leadingZeroCount)
            .take(4)
            .padEnd(4, '0')
        return buildString {
            append(sign)
            append("0.0")
            append(toSubscriptNumber(leadingZeroCount))
            append(significantDigits)
        }
    }

    private fun toSubscriptNumber(value: Int): String {
        val subscriptDigits = charArrayOf('₀', '₁', '₂', '₃', '₄', '₅', '₆', '₇', '₈', '₉')
        return value.toString().map { digit -> subscriptDigits[digit.digitToInt()] }.joinToString("")
    }
}
