package io.baiyanwu.coinmonitor.ui.trending

import io.baiyanwu.coinmonitor.data.network.TrendingFailure
import io.baiyanwu.coinmonitor.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class TrendingPresentationTest {
    @Test fun `PumpFun summary defaults to all chains and actual one hour window`() {
        val summary = TrendingUiState().summary(TrendingBoard.PUMP)
        assertEquals(listOf(TrendingSource.PUMP), summary.sources)
        assertNull(summary.chain)
        assertNull(summary.chainTitle)
        assertFalse(summary.mixedChains)
        assertEquals("1h", summary.period)
        assertNull(summary.mode)
    }

    @Test fun `shared filters do not collapse different chains or windows into one label`() {
        val options = TrendingSource.entries.associateWith { TrendingOptions() } +
            (TrendingSource.OKX to TrendingOptions(chain = TrendingChain.BASE, period = "24h"))
        val summary = TrendingUiState(options = options).summary(TrendingBoard.SHARED)
        assertTrue(summary.mixedChains)
        assertTrue(summary.mixedPeriods)
        assertNull(summary.chain)
        assertNull(summary.period)
    }

    @Test fun `matching source filters expose actual period instead of mockup period`() {
        val state = TrendingUiState(sources = setOf(TrendingSource.GMGN, TrendingSource.OKX))
        val summary = state.summary(TrendingBoard.SHARED)
        assertEquals(TrendingChain.SOLANA, summary.chain)
        assertEquals("5m", summary.period)
        assertFalse(summary.mixedPeriods)
    }

    @Test fun `all-chain source is distinguishable from mixed independent filters`() {
        val options = TrendingSource.entries.associateWith { TrendingOptions(chain = null) }
        val summary = TrendingUiState(options = options).summary(TrendingBoard.GMGN)
        assertNull(summary.chain)
        assertFalse(summary.mixedChains)
    }

    @Test fun `failed refresh labels the retained shared snapshot rather than newer source data`() {
        val state = TrendingUiState(
            ready = TrendingSource.entries.toSet(),
            sharedSnapshot = TrendingSnapshot(updatedAt = 1_000),
            snapshots = mapOf(
                TrendingSource.GMGN to TrendingSnapshot(updatedAt = 3_000),
                TrendingSource.PUMP to TrendingSnapshot(updatedAt = 4_000, failure = TrendingFailure.NETWORK),
                TrendingSource.OKX to TrendingSnapshot(updatedAt = 5_000, loading = true)
            )
        )
        val summary = state.summary(TrendingBoard.SHARED)
        assertEquals(1_000L, summary.updatedAt)
        assertTrue(summary.loading)
        assertEquals(listOf(TrendingSource.PUMP), summary.failures)
        assertTrue(summary.missing.isEmpty())
    }

    @Test fun `unconfigured source remains explicit even with a previously successful snapshot`() {
        val summary = TrendingUiState(snapshots = mapOf(TrendingSource.GMGN to TrendingSnapshot(updatedAt = 1_000)))
            .summary(TrendingBoard.GMGN)
        assertEquals(listOf(TrendingSource.GMGN), summary.missing)
        assertEquals(1_000L, summary.updatedAt)
    }

    @Test fun `partial sources cannot imply a successfully updated shared ranking`() {
        val state = TrendingUiState(snapshots = mapOf(TrendingSource.PUMP to TrendingSnapshot(updatedAt = 1_000)))
        assertNull(state.summary(TrendingBoard.SHARED).updatedAt)
    }
}
