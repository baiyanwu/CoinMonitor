package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.domain.model.WalletAddressKind
import io.baiyanwu.coinmonitor.domain.model.WalletAsset
import io.baiyanwu.coinmonitor.domain.model.WalletPortfolioSnapshot
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletPortfolioCacheDtoTest {
    @Test
    fun `cache round trip preserves wallet values and risk mode`() {
        val snapshot = WalletPortfolioSnapshot(
            address = "0x000000000000000000000000000000000000dEaD",
            addressKind = WalletAddressKind.EVM,
            assets = listOf(
                WalletAsset(
                    chainIndex = "4663",
                    chainName = "Robinhood",
                    symbol = "TOKEN",
                    contractAddress = "0x1111111111111111111111111111111111111111",
                    balance = BigDecimal("123.450000"),
                    tokenPriceUsd = BigDecimal("0.012300"),
                    holdingValueUsd = BigDecimal("1.5184350000"),
                    isRiskToken = false
                )
            ),
            totalValueUsd = BigDecimal("1.5184350000"),
            totalIsEstimated = false,
            updatedAtMillis = 1234L
        )

        val restored = snapshot.toCacheDto(includeRiskInTotal = true).toDomain()

        assertEquals(snapshot, restored.snapshot)
        assertTrue(restored.includeRiskInTotal)
    }
}
