package io.baiyanwu.coinmonitor.ui.wallet

import io.baiyanwu.coinmonitor.domain.model.*
import io.baiyanwu.coinmonitor.domain.repository.WalletVaultState
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class WalletHomeDisplayTest {
    @Test
    fun `unloaded and entirely unpriced portfolios do not show a false zero`() {
        assertNull(WalletUiState().homeSummary().totalUsd)
        val unpriced = state(listOf(asset("unpriced", null)))
        assertNull(unpriced.homeSummary().totalUsd)
        assertEquals(1, unpriced.homeSummary().unpricedAssetCount)
        assertEquals(BigDecimal.ZERO, state(emptyList()).homeSummary().totalUsd)
    }

    @Test
    fun `summary keeps precise priced subset and discloses missing valuations`() {
        val summary = state(listOf(asset("one", "12345678901234567890.12"), asset("two", "0.01"), asset("unknown", null))).homeSummary()
        assertEquals(BigDecimal("12345678901234567890.13"), summary.totalUsd)
        assertEquals(3, summary.assetCount)
        assertEquals(1, summary.unpricedAssetCount)
    }

    @Test
    fun `summary follows visibility filters and remains across network selection and refresh failure`() {
        val original = state(listOf(asset("eth", "100"), asset("sol", "50", WalletNetwork.SOLANA), asset("small", "0.5")))
        val filtered = original.copy(hideAssetsBelowOneUsd = true)
        assertEquals(BigDecimal("150"), filtered.homeSummary().totalUsd)
        assertEquals(2, filtered.homeSummary().assetCount)
        val refreshing = filtered.copy(
            selectedNetwork = WalletNetwork.SOLANA,
            loading = true, refreshing = true,
            portfolio = filtered.portfolio!!.copy(refreshFailures = WalletRefreshFailures(nativeBalance = setOf(WalletNetwork.SOLANA)))
        )
        assertEquals(listOf("sol"), refreshing.visibleAssets.map { it.id })
        assertEquals(filtered.homeSummary(), refreshing.homeSummary())
        assertEquals(1234L, refreshing.homeSummary().updatedAtMillis)
    }

    @Test
    fun `displayed address follows selected chain and private key family`() {
        val wallet = profile()
        assertEquals("evm-address", wallet.homeAddress(null))
        assertEquals("solana-address", wallet.homeAddress(WalletNetwork.SOLANA))
        val solanaOnly = wallet.copy(kind = WalletKind.PRIVATE_KEY, privateKeyType = WalletPrivateKeyType.SOLANA)
        assertEquals("solana-address", solanaOnly.homeAddress(null))
        assertNull(solanaOnly.homeAddress(WalletNetwork.ETHEREUM))
        assertNull(wallet.copy(kind = WalletKind.PRIVATE_KEY, privateKeyType = null).homeAddress(null))
    }

    private fun state(assets: List<SelfCustodyAsset>): WalletUiState {
        val wallet = profile()
        return WalletUiState(
            vault = WalletVaultState(initialized = true, unlocked = true, wallets = listOf(wallet), activeWalletId = wallet.id),
            networkConfiguration = WalletNetworkConfiguration(enabledNetworkIds = setOf(WalletNetwork.ETHEREUM.id, WalletNetwork.SOLANA.id)),
            portfolio = WalletPortfolio(assets = assets, activities = emptyList(), tokenIndexAvailable = true, updatedAtMillis = 1234L),
            hideAssetsBelowOneUsd = false
        )
    }

    private fun profile() = WalletProfile("wallet", "Main", WalletKind.MNEMONIC, "evm-address", "solana-address", createdAtMillis = 0)

    private fun asset(id: String, value: String?, network: WalletNetwork = WalletNetwork.ETHEREUM) = SelfCustodyAsset(
        id = id, network = network, tokenAddress = null, name = id, symbol = id.uppercase(), decimals = 18,
        rawBalance = "1000000000000000000", balance = BigDecimal.ONE, priceUsd = null, valueUsd = value?.toBigDecimal(),
        logoUrl = null, verified = true, isNative = true
    )
}
