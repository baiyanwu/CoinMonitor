package io.baiyanwu.coinmonitor.clipboard

import io.baiyanwu.coinmonitor.R
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ClipboardPanelStateTest {
    @Test fun multipleAddressesDoNotStartRequestsUntilChosen() = runBlocking {
        var calls = 0
        val source = object : ClipboardDataSource {
            override suspend fun lookup(address: String): List<ClipboardMatch> { calls++; return emptyList() }
            override suspend fun chart(match: ClipboardMatch) = emptyList<ClipboardChartPoint>()
            override suspend fun holders(match: ClipboardMatch): Int? = null
        }
        val state = ClipboardPanelState(source, this)
        state.receive("$CA 0x0000000000000000000000000000000000000000")
        yield()
        assertEquals(0, calls)
        assertEquals(R.string.clipboard_choose_address, state.message)
        state.chooseAddress(CA)
        yield()
        assertEquals(1, calls)
        assertEquals(R.string.clipboard_no_pool, state.message)
    }

    @Test fun switchingAddressCancelsOldLookupWithoutOverwritingNewSelection() = runBlocking {
        val first = CompletableDeferred<List<ClipboardMatch>>()
        val result = clipboardMatches(CA, listOf(testPair("base")))
        val source = object : ClipboardDataSource {
            override suspend fun lookup(address: String) = if (address == "old") first.await() else result
            override suspend fun chart(match: ClipboardMatch) = emptyList<ClipboardChartPoint>()
            override suspend fun holders(match: ClipboardMatch): Int? = null
        }
        val state = ClipboardPanelState(source, this)
        state.chooseAddress("old")
        yield()
        state.chooseAddress(CA)
        yield()
        first.complete(clipboardMatches(CA, listOf(testPair())))
        yield()
        assertEquals("base", state.selected?.chain?.dexScreenerId)
        assertFalse(state.loading)
    }

    @Test fun multipleChainsRequireSelectionAndChartFailureKeepsQuote() = runBlocking {
        var chartCalls = 0
        val results = clipboardMatches(CA, listOf(testPair(), testPair("base")))
        val source = object : ClipboardDataSource {
            override suspend fun lookup(address: String) = results
            override suspend fun chart(match: ClipboardMatch): List<ClipboardChartPoint> { chartCalls++; error("offline") }
            override suspend fun holders(match: ClipboardMatch): Int? = 2_874
        }
        val state = ClipboardPanelState(source, this)
        state.receive(CA)
        yield()
        assertNull(state.selected)
        assertEquals(0, chartCalls)
        state.chooseChain(results.first())
        yield()
        assertEquals(results.first(), state.selected)
        assertTrue(state.chartUnavailable)
        assertFalse(state.chartLoading)
        assertEquals(2_874, state.holderCount)
    }

    @Test fun closingSessionCancelsPendingNetworkWork() = runBlocking {
        var cancelled = false
        val source = object : ClipboardDataSource {
            override suspend fun lookup(address: String): List<ClipboardMatch> {
                try { awaitCancellation() } finally { cancelled = true }
            }
            override suspend fun chart(match: ClipboardMatch) = emptyList<ClipboardChartPoint>()
            override suspend fun holders(match: ClipboardMatch): Int? = null
        }
        val job = SupervisorJob(coroutineContext[Job])
        val session = CoroutineScope(coroutineContext + job)
        val state = ClipboardPanelState(source, session)
        state.receive(CA)
        yield()
        session.cancel()
        job.join()
        assertTrue(cancelled)
        assertNull(state.selected)
    }
}
