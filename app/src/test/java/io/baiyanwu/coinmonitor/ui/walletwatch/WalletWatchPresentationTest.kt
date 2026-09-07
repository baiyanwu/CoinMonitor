package io.baiyanwu.coinmonitor.ui.walletwatch

import io.baiyanwu.coinmonitor.domain.model.WalletAsset
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletWatchPresentationTest {
    @Test
    fun `default chain is the chain with the largest priced holding value`() {
        val assets = listOf(
            asset(chainIndex = "1", value = "10.25"),
            asset(chainIndex = "8453", value = "8.00"),
            asset(chainIndex = "8453", value = "7.00"),
            asset(chainIndex = "137", value = null)
        )

        assertEquals("8453", defaultWalletChainIndex(assets))
    }

    @Test
    fun `contract address keeps head and tail`() {
        assertEquals("0x1234…cdef", shortenWalletContract("0x1234567890abcdef"))
        assertEquals("native", shortenWalletContract("native"))
    }

    @Test
    fun `wallet values prices and quantities remove trailing zeroes consistently`() {
        assertEquals("1,234.5", formatWalletValue(BigDecimal("1234.5000")))
        assertEquals("0.1234", formatWalletPrice(BigDecimal("0.12340000")))
        assertEquals("42.5", formatWalletQuantity(BigDecimal("42.500000000")))
        assertEquals("<0.01", formatWalletValue(BigDecimal("0.0001")))
        assertEquals("<0.00000001", formatWalletPrice(BigDecimal("0.000000001")))
    }

    @Test
    fun `small asset filter hides unpriced assets and known values below one dollar`() {
        assertFalse(shouldShowWalletAsset(asset(chainIndex = "1", value = "0.99"), hideSmallAssets = true, hideRiskAssets = false))
        assertTrue(shouldShowWalletAsset(asset(chainIndex = "1", value = "1.00"), hideSmallAssets = true, hideRiskAssets = false))
        assertFalse(shouldShowWalletAsset(asset(chainIndex = "1", value = null), hideSmallAssets = true, hideRiskAssets = false))
        assertTrue(shouldShowWalletAsset(asset(chainIndex = "1", value = "0.01"), hideSmallAssets = false, hideRiskAssets = false))
        assertTrue(shouldShowWalletAsset(asset(chainIndex = "1", value = null), hideSmallAssets = false, hideRiskAssets = false))
    }

    @Test
    fun `risk asset filter removes risk rows only when enabled`() {
        val riskAsset = asset(chainIndex = "1", value = null).copy(isRiskToken = true)
        assertFalse(shouldShowWalletAsset(riskAsset, hideSmallAssets = false, hideRiskAssets = true))
        assertTrue(shouldShowWalletAsset(riskAsset, hideSmallAssets = false, hideRiskAssets = false))
    }

    @Test
    fun `portfolio total contains only visible priced assets`() {
        val visibleAssets = listOf(
            asset(chainIndex = "1", value = "10.25"),
            asset(chainIndex = "8453", value = null),
            asset(chainIndex = "137", value = "2.50")
        )

        assertEquals(BigDecimal("12.75"), visibleWalletPortfolioTotal(visibleAssets))
    }

    private fun asset(chainIndex: String, value: String?) = WalletAsset(
        chainIndex = chainIndex,
        chainName = chainIndex,
        symbol = "TOKEN",
        contractAddress = chainIndex,
        balance = BigDecimal.ONE,
        tokenPriceUsd = value?.toBigDecimal(),
        holdingValueUsd = value?.toBigDecimal(),
        isRiskToken = false
    )
}
