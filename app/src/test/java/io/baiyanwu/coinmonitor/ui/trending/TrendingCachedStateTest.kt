package io.baiyanwu.coinmonitor.ui.trending

import io.baiyanwu.coinmonitor.data.network.TrendingFailure
import io.baiyanwu.coinmonitor.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class TrendingCachedStateTest {
    private val row = TrendingToken("501", "Solana", "So11111111111111111111111111111111111111112", "SOL")
    @Test fun `background loading and failure retain visible individual rows`() {
        val state = TrendingUiState(snapshots = mapOf(TrendingSource.GMGN to TrendingSnapshot(listOf(row), 1000, loading = true)))
        assertEquals(listOf(row), state.rows(TrendingBoard.GMGN))
        assertEquals(listOf(row), state.copy(snapshots = mapOf(TrendingSource.GMGN to TrendingSnapshot(listOf(row), 1000,
            failure = TrendingFailure.NETWORK))).rows(TrendingBoard.GMGN))
    }
    @Test fun `complete shared cache stays visible while sources are incomplete or refreshing`() {
        val shared = TrendingSnapshot(listOf(row), 1000, newDates = mapOf(row.id to 900L))
        val state = TrendingUiState(sharedSnapshot = shared, now = 1100,
            snapshots = mapOf(TrendingSource.PUMP to TrendingSnapshot(emptyList(), 1050, loading = true)))
        assertEquals(listOf(row), state.rows(TrendingBoard.SHARED))
        assertEquals(setOf(row.id), state.newIds(TrendingBoard.SHARED))
    }
    @Test fun `warm cache uses exact source chain mode and period`() {
        val first = TrendingSource.entries.associateWith { TrendingOptions() }
        val second = first + (TrendingSource.GMGN to TrendingOptions(chain = TrendingChain.BASE))
        val third = first + (TrendingSource.GMGN to TrendingOptions(mode = TrendingMode.HOT_SEARCH))
        val sources = setOf(TrendingSource.GMGN, TrendingSource.OKX)
        assertNotEquals(sharedQueryKey(sources, first), sharedQueryKey(sources, second))
        assertNotEquals(sharedQueryKey(sources, first), sharedQueryKey(sources, third))
        assertEquals(sharedQueryKey(sources, first), sharedQueryKey(sources, first +
            (TrendingSource.GMGN to TrendingOptions(value = TrendingValue.PRICE))))
    }
    @Test fun `entering with a fresh cache does not force an immediate request`() {
        val options = TrendingOptions(refreshSeconds = 30)
        assertFalse(TrendingRefreshPolicy.isDue(100000, options, 100001))
        assertFalse(TrendingRefreshPolicy.isDue(100000, options, 129999))
        assertTrue(TrendingRefreshPolicy.isDue(100000, options, 130000))
    }
    @Test fun `manual refresh and stale cache respect pause and server cooldown`() {
        val options = TrendingOptions(autoRefresh = false)
        assertFalse(TrendingRefreshPolicy.isDue(1000, options, 90000))
        assertTrue(TrendingRefreshPolicy.isDue(1000, options, 90000, requested = true))
        assertFalse(TrendingRefreshPolicy.isDue(1000, options, 90000, nextAllowed = 100000, requested = true))
        assertFalse(TrendingRefreshPolicy.isDue(1000, options, 90000, blocked = true, requested = true))
        assertTrue(TrendingRefreshPolicy.isDue(null, options, 90000))
    }
}
