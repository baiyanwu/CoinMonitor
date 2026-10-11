package io.baiyanwu.coinmonitor.ui.trending

import org.junit.Assert.assertEquals
import org.junit.Test

class TrendingDisplayTest {
    @Test fun `trending price compresses fractional leading zeros`() {
        assertEquals("$0.0₃1234", priceUsd(0.0001234))
        assertEquals("$0.0₅1234", priceUsd(0.000001234))
        assertEquals("$0.00123", priceUsd(0.001234))
    }
    @Test fun `trending price keeps missing and nonfinite values unknown`() {
        assertEquals("--", priceUsd(null))
        assertEquals("--", priceUsd(Double.NaN))
        assertEquals("--", priceUsd(Double.POSITIVE_INFINITY))
    }
}
