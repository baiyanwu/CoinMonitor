package io.baiyanwu.coinmonitor.ui.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** BigDecimal display rules shared by watch-only and self-custody wallets. */
object AssetAmountFormatter {
    fun fiat(value: BigDecimal): String = format(value, 2, BigDecimal("0.01"))

    fun token(value: BigDecimal, maxFractionDigits: Int = 8): String = format(
        value,
        maxFractionDigits,
        BigDecimal.ONE.movePointLeft(maxFractionDigits)
    )

    fun price(value: BigDecimal): String = format(value, 8, BigDecimal("0.00000001"))

    fun networkFee(value: BigDecimal): String = format(value, 12, BigDecimal("0.000000000001"))

    private fun format(value: BigDecimal, maxFractionDigits: Int, tinyThreshold: BigDecimal): String {
        if (value.compareTo(BigDecimal.ZERO) == 0) return "0"
        if (value.abs() < tinyThreshold) return "<${tinyThreshold.stripTrailingZeros().toPlainString()}"
        val rounded = value.setScale(maxFractionDigits, RoundingMode.HALF_UP).stripTrailingZeros()
        return DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = maxFractionDigits
            roundingMode = RoundingMode.HALF_UP
            isParseBigDecimal = true
        }.format(rounded)
    }
}
