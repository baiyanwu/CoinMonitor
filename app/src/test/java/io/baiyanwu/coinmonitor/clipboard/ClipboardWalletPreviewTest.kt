package io.baiyanwu.coinmonitor.clipboard

import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.domain.model.*
import java.math.BigDecimal
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ClipboardWalletPreviewTest {
    private val source = object : ClipboardDataSource {
        override suspend fun lookup(address: String) = emptyList<ClipboardMatch>()
        override suspend fun chart(match: ClipboardMatch) = emptyList<ClipboardChartPoint>()
        override suspend fun holders(match: ClipboardMatch): Int? = null
    }
    private fun snapshot(address: String = CA) = WalletPortfolioSnapshot(address, WalletAddressKind.EVM, listOf(
        WalletAsset("8453", "Base", "USDC", "usdc", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, false),
        WalletAsset("8453", "Base", "Unknown", "unknown", BigDecimal.ONE, null, null, false),
        WalletAsset("1", "Ethereum", "ETH", "", BigDecimal.ONE, BigDecimal("100"), BigDecimal("100"), false),
        WalletAsset("8453", "Base", "Risk", "risk", BigDecimal.ONE, BigDecimal("900"), BigDecimal("900"), true)
    ), BigDecimal("1010"), false, 1234)

    @Test fun selectedNetworkValueNeverUsesWholeWalletTotalOrRiskAssets() {
        val preview = ClipboardWalletPreview(snapshot(), "8453")
        assertEquals(BigDecimal.TEN, preview.value); assertEquals(2, preview.assets.size); assertTrue(preview.partial)
    }

    @Test fun evmRequiresNetworkAndCredentialsBeforeLoading() = runBlocking {
        var calls = 0
        val wallets = object : ClipboardWalletDataSource {
            override fun isConfigured() = false
            override suspend fun load(address: String): WalletPortfolioSnapshot { calls++; return snapshot(address) }
        }
        val state = ClipboardPanelState(source, this, wallets)
        state.receive(CA); yield(); state.useWalletMode(); yield()
        assertNull(state.walletChain); assertEquals(0, calls)
        state.chooseWalletChain("8453"); yield()
        assertEquals(R.string.wallet_watch_credentials_required, state.walletMessage); assertEquals(0, calls)
    }

    @Test fun refreshFailureKeepsSameAddressSnapshotButNewInputClearsIt() = runBlocking {
        var failing = false
        val wallets = object : ClipboardWalletDataSource {
            override fun isConfigured() = true
            override suspend fun load(address: String): WalletPortfolioSnapshot { if (failing) error("offline"); return snapshot(address) }
        }
        val state = ClipboardPanelState(source, this, wallets)
        state.receive(CA); yield(); state.useWalletMode(); state.chooseWalletChain("8453"); yield()
        assertNotNull(state.walletSnapshot)
        failing = true; state.loadWallet(); yield()
        assertNotNull(state.walletSnapshot); assertEquals(R.string.clipboard_wallet_failed, state.walletMessage)
        state.receive("0x" + "a".repeat(40)); yield()
        assertNull(state.walletSnapshot)
    }

    @Test fun copiedFomoProfileHasPriorityOverAddressShapedHandle() = runBlocking {
        var queried: String? = null
        val state = ClipboardPanelState(source, this, fomo = FomoProfileDataSource { link ->
            queried = link.handle; FomoProfileWallets(link.handle, link.handle, null, FomoWalletEvidence(CA, 1, "tx"), null)
        })
        state.receive("https://fomo.family/profile/$CA"); yield()
        assertEquals(CA, queried); assertEquals(ClipboardContentMode.FOMO, state.mode); assertNull(state.address)
    }

    @Test fun newCopyCancelsFomoLookupAndIgnoresLateResults() = runBlocking {
        val gate = CompletableDeferred<FomoProfileWallets>()
        val state = ClipboardPanelState(source, this, fomo = FomoProfileDataSource { gate.await() })
        state.receive("https://fomo.family/profile/first"); yield()
        state.receive(CA); yield()
        gate.complete(FomoProfileWallets("first", "first", null, FomoWalletEvidence(CA, 1, "tx"), null)); yield()
        assertNull(state.fomoWallets); assertEquals(ClipboardContentMode.TOKEN, state.mode); assertEquals(CA, state.address)
    }

    @Test fun cancelledLookupFailureCannotReplaceNewAddressResult() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val results = clipboardMatches(CA, listOf(testPair()))
        val data = object : ClipboardDataSource {
            override suspend fun lookup(address: String): List<ClipboardMatch> {
                if (address == "old") withContext(NonCancellable) { release.await(); error("late failure") }
                return results
            }
            override suspend fun chart(match: ClipboardMatch) = emptyList<ClipboardChartPoint>()
            override suspend fun holders(match: ClipboardMatch): Int? = null
        }
        val state = ClipboardPanelState(data, this)
        state.chooseAddress("old"); yield(); state.chooseAddress(CA); yield()
        release.complete(Unit); yield(); yield()
        assertEquals(results.single(), state.selected); assertNull(state.message)
    }

    @Test fun cancelledWalletFailureCannotOverwriteSuccessfulRefresh() = runBlocking {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val wallets = object : ClipboardWalletDataSource {
            override fun isConfigured() = true
            override suspend fun load(address: String): WalletPortfolioSnapshot {
                calls++
                if (calls == 1) withContext(NonCancellable) { release.await(); error("late failure") }
                return snapshot(address)
            }
        }
        val state = ClipboardPanelState(source, this, wallets)
        state.receive(CA); yield(); state.useWalletMode(); state.chooseWalletChain("8453"); yield()
        state.loadWallet(); yield(); release.complete(Unit); yield(); yield()
        assertNotNull(state.walletSnapshot); assertNull(state.walletMessage)
    }
}
