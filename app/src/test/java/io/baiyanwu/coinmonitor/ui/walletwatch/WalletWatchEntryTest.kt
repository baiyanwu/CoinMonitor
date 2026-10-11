package io.baiyanwu.coinmonitor.ui.walletwatch

import io.baiyanwu.coinmonitor.domain.model.*
import io.baiyanwu.coinmonitor.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class WalletWatchEntryTest {
    private val address = "0x" + "a".repeat(40)
    private val snapshot get() = WalletPortfolioSnapshot(address, WalletAddressKind.EVM,
        listOf(WalletAsset("8453", "Base", "USDC", "usdc", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, false)),
        BigDecimal.TEN, false, 100)

    @Test fun entrySearchesProvidedAddressAndSelectsEthereumEvenWithNoEthAssets() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val queried = mutableListOf<String>()
            val vm = createVm(queried, "1")
            advanceUntilIdle()
            assertEquals(address, vm.uiState.value.address)
            assertEquals(listOf(address), queried)
            assertEquals("1", vm.uiState.value.selectedChainIndex)
            assertFalse("1" in vm.uiState.value.hiddenChainIndexes)
            vm.refresh()
            advanceUntilIdle()
            assertEquals("1", vm.uiState.value.selectedChainIndex)
            assertFalse("1" in vm.uiState.value.hiddenChainIndexes)
            vm.selectChain("8453")
            vm.refresh()
            advanceUntilIdle()
            assertEquals("8453", vm.uiState.value.selectedChainIndex)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun chainEntrySelectsRequestedChainInsteadOfHighestValueChain() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = createVm(mutableListOf(), "56")
            advanceUntilIdle()
            assertEquals("56", vm.uiState.value.selectedChainIndex)
            assertEquals(address, vm.uiState.value.snapshot?.address)
        } finally { Dispatchers.resetMain() }
    }

    private fun createVm(queried: MutableList<String>, chain: String) = WalletWatchViewModel(
        object : OkxWalletCredentialsRepository {
            override fun getCredentials() = OkxWalletCredentials(true, "key", "secret", "passphrase")
            override fun observeCredentials() = flowOf(getCredentials())
            override fun isSecureStorageAvailable() = true
            override suspend fun save(credentials: OkxWalletCredentials) {}
            override suspend fun clear() {}
        },
        object : WalletPortfolioRepository {
            override suspend fun load(address: String, includeRiskInTotal: Boolean): WalletPortfolioSnapshot { queried += address; return snapshot }
            override suspend fun loadTotal(address: String, includeRiskInTotal: Boolean) = WalletTotalResult.Success(BigDecimal.TEN)
        },
        object : WalletPortfolioCacheRepository {
            override suspend fun load(address: String): CachedWalletPortfolio? = null
            override suspend fun save(snapshot: WalletPortfolioSnapshot, includeRiskInTotal: Boolean) {}
        },
        object : WalletWatchPreferencesRepository {
            override fun getLastAddress() = "0x" + "b".repeat(40)
            override suspend fun saveLastAddress(address: String) {}
            override fun getHiddenAssetIds(address: String) = emptySet<String>()
            override suspend fun saveHiddenAssetIds(address: String, assetIds: Set<String>) {}
            override fun getHiddenChainIndexes(address: String) = setOf("1")
            override suspend fun saveHiddenChainIndexes(address: String, chainIndexes: Set<String>) {}
            override fun getHideSmallAssets(address: String) = true
            override suspend fun saveHideSmallAssets(address: String, hide: Boolean) {}
            override fun getIncludeRiskAssets(address: String) = false
            override suspend fun saveIncludeRiskAssets(address: String, include: Boolean) {}
        }, address, chain
    )
}
