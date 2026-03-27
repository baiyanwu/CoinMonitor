package io.baiyanwu.coinmonitor.overlay

import java.text.DecimalFormat
import kotlin.math.abs

object QuoteFormatter {
    fun formatPrice(value: Double?): String {
        if (value == null) return "--"

        val pattern = when {
            value >= 1000 -> "0.0"
            value >= 100 -> "0.00"
            value >= 1 -> "0.000"
            value >= 0.01 -> "0.0000"
            else -> "0.00000"
        }
        return DecimalFormat(pattern).format(value)
    }

    fun formatChange(value: Double?): String {
        if (value == null) return "--"
        val formatted = DecimalFormat("0.00").format(abs(value))
        return if (value >= 0) "+$formatted%" else "-$formatted%"
    }
}
