package io.baiyanwu.coinmonitor.ui.trending

import io.baiyanwu.coinmonitor.data.network.TrendingClient
import io.baiyanwu.coinmonitor.data.network.TrendingException
import io.baiyanwu.coinmonitor.data.network.TrendingFailure
import io.baiyanwu.coinmonitor.data.repository.GmgnCredentialsRepository
import io.baiyanwu.coinmonitor.data.repository.TrendingCacheState
import io.baiyanwu.coinmonitor.data.repository.TrendingPreferences
import io.baiyanwu.coinmonitor.data.repository.TrendingSnapshotCache
import io.baiyanwu.coinmonitor.domain.model.*
import io.baiyanwu.coinmonitor.domain.repository.OkxWalletCredentialsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class TrendingRefreshFlowTest {
    private class Harness(val scope: TestScope, loaded: Boolean = true) {
        val counts = TrendingSource.entries.associateWith { 0 }.toMutableMap()
        var failure: TrendingFailure? = null
        var rows: List<TrendingToken> = emptyList()
        val cacheState = MutableStateFlow(TrendingCacheState(loaded = loaded))
        val key = MutableStateFlow("test-key")
        lateinit var model: TrendingViewModel

        suspend fun create(board: TrendingBoard) {
            val client = Mockito.mock(TrendingClient::class.java)
            val preferences = Mockito.mock(TrendingPreferences::class.java)
            val gmgn = Mockito.mock(GmgnCredentialsRepository::class.java)
            val okx = Mockito.mock(OkxWalletCredentialsRepository::class.java)
            val cache = Mockito.mock(TrendingSnapshotCache::class.java)
            val credentials = OkxWalletCredentials(true, "test-key", "test-secret", "test-passphrase")
            val options = TrendingOptions()
            Mockito.`when`(preferences.board()).thenReturn(board)
            Mockito.`when`(preferences.selectedSources()).thenReturn(TrendingSource.entries.toSet())
            Mockito.`when`(preferences.loadShared()).thenReturn(options)
            Mockito.`when`(gmgn.apiKey).thenReturn(key)
            Mockito.`when`(okx.getCredentials()).thenReturn(credentials)
            Mockito.`when`(okx.observeCredentials()).thenReturn(MutableStateFlow(credentials))
            Mockito.`when`(cache.state).thenReturn(cacheState)
            Mockito.`when`(cache.awaitLoaded()).thenAnswer { cacheState.value }
            for (source in TrendingSource.entries) {
                Mockito.`when`(preferences.load(source)).thenReturn(options)
                for (apiKey in listOf("test-key", "changed-key")) {
                    Mockito.`when`(client.fetch(source, options, apiKey, credentials)).thenAnswer {
                        counts[source] = counts.getValue(source) + 1
                        failure?.let { throw TrendingException(it) }
                        rows
                    }
                }
            }
            model = TrendingViewModel(client, preferences, gmgn, okx, cache) { 100_000 + scope.testScheduler.currentTime }
        }
    }

    private fun verifyFlow(board: TrendingBoard = TrendingBoard.PUMP, loaded: Boolean = true,
        block: suspend Harness.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val harness = Harness(this, loaded)
        try {
            harness.create(board)
            harness.block()
        } finally {
            harness.model.stop()
            // Cancel the credential observer as well as the ticker owned by the ViewModel.
            androidx.lifecycle.ViewModelStore().apply { put("test", harness.model); clear() }
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun `entry requests once and time passing never polls`() = verifyFlow {
        model.start(); scope.runCurrent()
        assertEquals(1, counts.getValue(TrendingSource.PUMP))
        scope.advanceTimeBy(600_000); scope.runCurrent()
        assertEquals(1, counts.getValue(TrendingSource.PUMP))
    }

    @Test fun `pump chain switch keeps complete snapshot and updates shared intersection without requests`() = verifyFlow(TrendingBoard.SHARED) {
        val sol = TrendingToken("501", "Solana", "So11111111111111111111111111111111111111112", "SOL")
        val bsc = TrendingToken("56", "BSC", "0x" + "ab".repeat(20), "BSC")
        rows = listOf(sol, bsc)
        model.start(); scope.runCurrent()
        assertEquals(2, model.state.value.rows(TrendingBoard.SHARED).size)
        model.updateOptions(TrendingSource.PUMP, TrendingOptions(pumpChainIndex = "56")); scope.runCurrent()
        assertEquals(listOf(bsc), model.state.value.rows(TrendingBoard.SHARED))
        assertEquals(listOf(bsc), model.state.value.rows(TrendingBoard.PUMP))
        assertEquals(listOf(sol, bsc), model.state.value.snapshots.getValue(TrendingSource.PUMP).tokens)
        model.updateOptions(TrendingSource.PUMP, TrendingOptions()); scope.runCurrent()
        assertEquals(2, model.state.value.rows(TrendingBoard.SHARED).size)
        assertTrue(counts.values.all { it == 1 })
    }

    @Test fun `button drops repeated requests until exactly five seconds`() = verifyFlow {
        model.start(); scope.runCurrent()
        repeat(10) { model.refresh() }
        scope.runCurrent()
        assertEquals(1, counts.getValue(TrendingSource.PUMP))
        scope.advanceTimeBy(4_999); model.refresh(); scope.runCurrent()
        assertEquals(1, counts.getValue(TrendingSource.PUMP))
        scope.advanceTimeBy(1); model.refresh(); scope.runCurrent()
        assertEquals(2, counts.getValue(TrendingSource.PUMP))
        scope.advanceTimeBy(30_000); scope.runCurrent()
        assertEquals(2, counts.getValue(TrendingSource.PUMP))
    }

    @Test fun `switch requests target once and rapid return shares the same gate`() = verifyFlow {
        model.start(); scope.runCurrent()
        model.selectBoard(TrendingBoard.OKX); scope.runCurrent()
        assertEquals(1, counts.getValue(TrendingSource.OKX))
        model.selectBoard(TrendingBoard.PUMP); scope.runCurrent()
        assertEquals(1, counts.getValue(TrendingSource.PUMP))
        scope.advanceTimeBy(5_000)
        model.selectBoard(TrendingBoard.OKX); scope.runCurrent()
        model.selectBoard(TrendingBoard.PUMP); scope.runCurrent()
        assertEquals(2, counts.getValue(TrendingSource.PUMP))
        model.selectBoard(TrendingBoard.PUMP); scope.runCurrent()
        assertEquals(2, counts.getValue(TrendingSource.PUMP))
    }

    @Test fun `shared board does not repeat recently refreshed individual source`() = verifyFlow {
        model.start(); scope.runCurrent()
        model.selectBoard(TrendingBoard.SHARED); scope.runCurrent()
        assertEquals(1, counts.getValue(TrendingSource.PUMP))
        assertEquals(1, counts.getValue(TrendingSource.GMGN))
        assertEquals(1, counts.getValue(TrendingSource.OKX))
        scope.advanceTimeBy(20_000); scope.runCurrent()
        assertTrue(counts.values.all { it == 1 })
    }

    @Test fun `failed request never retries when cooldown expires`() = verifyFlow {
        failure = TrendingFailure.NETWORK
        model.start(); scope.runCurrent()
        scope.advanceTimeBy(120_000); scope.runCurrent()
        assertEquals(1, counts.getValue(TrendingSource.PUMP))
        failure = null
        model.refresh(); scope.runCurrent()
        assertEquals(2, counts.getValue(TrendingSource.PUMP))
    }

    @Test fun `credential change does not refresh or bypass request spacing`() = verifyFlow(TrendingBoard.GMGN) {
        model.start(); scope.runCurrent()
        key.value = "changed-key"; scope.runCurrent()
        model.refresh(); scope.runCurrent()
        assertEquals(1, counts.getValue(TrendingSource.GMGN))
        scope.advanceTimeBy(5_000); model.refresh(); scope.runCurrent()
        assertEquals(2, counts.getValue(TrendingSource.GMGN))
    }

    @Test fun `changing filters does not start a request`() = verifyFlow(TrendingBoard.GMGN) {
        model.start(); scope.runCurrent()
        scope.advanceTimeBy(5_000)
        model.updateOptions(TrendingSource.GMGN, TrendingOptions(chain = TrendingChain.BASE))
        scope.runCurrent()
        assertEquals(1, counts.getValue(TrendingSource.GMGN))
        scope.advanceTimeBy(60_000); scope.runCurrent()
        assertEquals(1, counts.getValue(TrendingSource.GMGN))
    }

    @Test fun `inactive page never refreshes and reopening observes the same five second gate`() = verifyFlow {
        model.start(); scope.runCurrent()
        model.stop(); model.refresh(); scope.runCurrent()
        assertEquals(1, counts.getValue(TrendingSource.PUMP))
        model.start(); scope.runCurrent()
        assertEquals(1, counts.getValue(TrendingSource.PUMP))
        model.stop()
        scope.advanceTimeBy(5_000)
        model.start(); scope.runCurrent()
        assertEquals(2, counts.getValue(TrendingSource.PUMP))
    }

    @Test fun `cache restoration refreshes only the final selected board`() = verifyFlow(loaded = false) {
        model.start()
        model.selectBoard(TrendingBoard.OKX)
        assertTrue(counts.values.all { it == 0 })
        cacheState.value = TrendingCacheState(loaded = true)
        scope.runCurrent()
        assertEquals(1, counts.getValue(TrendingSource.OKX))
        assertEquals(0, counts.getValue(TrendingSource.PUMP))
        scope.advanceTimeBy(20_000); scope.runCurrent()
        assertEquals(1, counts.getValue(TrendingSource.OKX))
    }
}
