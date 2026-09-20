package io.baiyanwu.coinmonitor.ui.wallet

import io.baiyanwu.coinmonitor.domain.model.SelfCustodyAsset
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.domain.model.WalletPortfolio
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class WalletDisplayPolicyTest {
    @Test
    fun `wallet display follows watch wallet small value and verification filters`() {
        val state = WalletUiState(
            portfolio = WalletPortfolio(
                assets = listOf(
                    asset("visible", "2", verified = true),
                    asset("small", "0.5", verified = true),
                    asset("unverified", "3", verified = false)
                ),
                activities = emptyList(),
                tokenIndexAvailable = true
            )
        )

        assertEquals(listOf("visible"), state.visibleAssets.map(SelfCustodyAsset::id))
        assertEquals(
            listOf("visible", "small", "unverified"),
            state.copy(hideAssetsBelowOneUsd = false, includeUnverifiedAssets = true)
                .visibleAssets.map(SelfCustodyAsset::id)
        )
    }

    private fun asset(id: String, value: String, verified: Boolean) = SelfCustodyAsset(
        id = id,
        network = WalletNetwork.ETHEREUM,
        tokenAddress = "0x0000000000000000000000000000000000000001",
        name = id,
        symbol = id.uppercase(),
        decimals = 18,
        rawBalance = "1",
        balance = BigDecimal.ONE,
        priceUsd = value.toBigDecimal(),
        valueUsd = value.toBigDecimal(),
        logoUrl = null,
        verified = verified,
        isNative = false
    )
}
