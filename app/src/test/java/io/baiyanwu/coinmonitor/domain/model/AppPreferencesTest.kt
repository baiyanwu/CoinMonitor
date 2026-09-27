package io.baiyanwu.coinmonitor.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun `provider order removes duplicates and appends missing providers`() {
        assertEquals(
            listOf(OnchainDataProvider.OKX_DEX, OnchainDataProvider.DEX_SCREENER),
            AppPreferences.normalizeOnchainProviderOrder(
                listOf(OnchainDataProvider.OKX_DEX, OnchainDataProvider.OKX_DEX)
            )
        )
    }

    @Test
    fun `empty provider order restores default order`() {
        assertEquals(
            AppPreferences.DEFAULT_ONCHAIN_PROVIDER_ORDER,
            AppPreferences.normalizeOnchainProviderOrder(emptyList())
        )
    }
}
