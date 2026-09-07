package io.baiyanwu.coinmonitor.ui.home

import io.baiyanwu.coinmonitor.domain.model.WalletAsset
import io.baiyanwu.coinmonitor.ui.search.SearchMode
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletWatchEntryPolicyTest {
    @Test fun `wallet watch entry is only visible on onchain page`() {
        assertFalse(shouldShowWalletWatchEntry(SearchMode.EXCHANGE))
        assertTrue(shouldShowWalletWatchEntry(SearchMode.ONCHAIN))
    }

    @Test fun `wallet watch summary follows manual chain token and display filters`() {
        val summary = buildHomeWalletWatchSummary(
            address = "0x1234",
            assets = listOf(
                asset(id = "visible", chain = "1", value = "12.5"),
                asset(id = "hidden-token", chain = "1", value = "8"),
                asset(id = "hidden-chain-token", chain = "8453", value = "20"),
                asset(id = "small", chain = "1", value = "0.5"),
                asset(id = "unpriced", chain = "1", value = null),
                asset(id = "risk", chain = "1", value = "30", risk = true)
            ),
            hiddenAssetIds = setOf("1:hidden-token"),
            hiddenChainIndexes = setOf("8453"),
            hideSmallAssets = true,
            includeRiskAssets = false
        )

        assertEquals(BigDecimal("12.5"), summary.totalValueUsd)
        assertEquals(1, summary.assetCount)
    }

    private fun asset(id: String, chain: String, value: String?, risk: Boolean = false) = WalletAsset(
        chainIndex = chain,
        chainName = chain,
        symbol = id,
        contractAddress = id,
        balance = BigDecimal.ONE,
        tokenPriceUsd = value?.toBigDecimal(),
        holdingValueUsd = value?.toBigDecimal(),
        isRiskToken = risk
    )
}
