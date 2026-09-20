package io.baiyanwu.coinmonitor.ui.format

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class AssetAmountFormatterTest {
    @Test
    fun `wallet formatters preserve semantics without string truncation`() {
        assertEquals("1,234.5", AssetAmountFormatter.fiat(BigDecimal("1234.5000")))
        assertEquals("12,345,678,901,234,567,890.12345679", AssetAmountFormatter.token(
            BigDecimal("12345678901234567890.123456789")
        ))
        assertEquals("<0.00000001", AssetAmountFormatter.token(BigDecimal("0.000000001")))
        assertEquals("0", AssetAmountFormatter.price(BigDecimal.ZERO))
    }

    @Test
    fun `network fee retains more precision than token display`() {
        assertEquals("0.000000000123", AssetAmountFormatter.networkFee(BigDecimal("0.0000000001234")))
    }
}
