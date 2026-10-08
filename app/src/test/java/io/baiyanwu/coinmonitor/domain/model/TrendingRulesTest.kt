package io.baiyanwu.coinmonitor.domain.model

import org.junit.Assert.*
import org.junit.Test

class TrendingRulesTest {
    private val address = "0x" + "a".repeat(40)
    private fun token(chain: String = "8453", address: String = this.address, marketCap: Double? = null) =
        TrendingToken(chain, "Chain", address, "SAME", marketCap = marketCap)
    @Test fun `shared intersection is chain and address with gmgn metric priority`() {
        val gmgn = token(marketCap = 100.0).copy(links = mapOf(TrendingSource.GMGN to "gmgn"))
        val okx = token(address = "0x" + "A".repeat(40), marketCap = 200.0).copy(links = mapOf(TrendingSource.OKX to "okx"))
        val rows = TrendingRules.intersection(setOf(TrendingSource.GMGN, TrendingSource.OKX),
            mapOf(TrendingSource.GMGN to listOf(gmgn, token("56")), TrendingSource.OKX to listOf(okx)))
        assertEquals(100.0, rows.single().marketCap!!, 0.0)
        assertEquals(2, rows.single().links.size)
    }
    @Test fun `missing source or one selected source cannot produce shared results`() {
        val snapshots = mapOf(TrendingSource.GMGN to listOf(token()))
        assertTrue(TrendingRules.intersection(TrendingSource.entries.toSet(), snapshots).isEmpty())
        assertTrue(TrendingRules.intersection(setOf(TrendingSource.GMGN), snapshots).isEmpty())
    }
    @Test fun `solana address case is identity`() {
        val a = token("501", "So11111111111111111111111111111111111111112")
        val b = a.copy(address = a.address.lowercase())
        assertNotEquals(a.id, b.id)
        assertTrue(TrendingRules.intersection(setOf(TrendingSource.GMGN, TrendingSource.PUMP), mapOf(TrendingSource.GMGN to listOf(a), TrendingSource.PUMP to listOf(b))).isEmpty())
    }
    @Test fun `unselected gmgn never supplies shared metrics`() {
        val okx = token(marketCap = 200.0)
        val pump = token(marketCap = 300.0)
        val row = TrendingRules.intersection(setOf(TrendingSource.OKX, TrendingSource.PUMP), mapOf(TrendingSource.GMGN to listOf(token(marketCap = 100.0)), TrendingSource.OKX to listOf(okx), TrendingSource.PUMP to listOf(pump))).single()
        assertEquals(200.0, row.marketCap!!, 0.0); assertNull(row.smartMoney)
    }
    @Test fun `each phone tab requests only demanded sources`() {
        val selected = setOf(TrendingSource.OKX, TrendingSource.PUMP)
        assertEquals(selected, TrendingRules.demand(TrendingBoard.SHARED, selected))
        assertEquals(setOf(TrendingSource.GMGN), TrendingRules.demand(TrendingBoard.GMGN, selected))
        assertEquals(setOf(TrendingSource.PUMP), TrendingRules.demand(TrendingBoard.PUMP, selected))
        assertEquals(setOf(TrendingSource.OKX), TrendingRules.demand(TrendingBoard.OKX, selected))
    }
    @Test fun `first success is baseline and later new badges never renew on refresh`() {
        assertTrue(TrendingRules.newDates(listOf("a"), null, emptyMap(), 1000).isEmpty())
        val dates = TrendingRules.newDates(listOf("a", "b"), listOf("a"), emptyMap(), 2000)
        assertEquals(mapOf("b" to 2000L), TrendingRules.newDates(listOf("a", "b"), listOf("a", "b"), dates, 6000))
        assertTrue(TrendingRules.newDates(listOf("a", "b"), listOf("a", "b"), dates, 182000).isEmpty())
    }
    @Test fun `new rows stay pinned while missing numbers sort last in either direction`() {
        val high = token(address = "high", marketCap = 10.0)
        val low = token(address = "low", marketCap = 1.0)
        val unknown = token(address = "unknown")
        val newest = token(address = "new", marketCap = 2.0)
        val options = TrendingOptions(sort = TrendingSort.MARKET_CAP)
        val rows = listOf(low, newest, unknown, high)
        assertEquals(listOf(newest, high, low, unknown), TrendingRules.sorted(rows, options, setOf(newest.id)))
        assertEquals(listOf(newest, low, high, unknown), TrendingRules.sorted(rows, options.copy(ascending = true), setOf(newest.id)))
    }
    @Test fun `display and refresh options retain query identity but filters change it`() {
        val options = TrendingOptions()
        assertEquals(options.queryKey(TrendingSource.GMGN), options.copy(sort = TrendingSort.LIQUIDITY, value = TrendingValue.PRICE, refreshSeconds = 15).queryKey(TrendingSource.GMGN))
        assertNotEquals(options.queryKey(TrendingSource.GMGN), options.copy(mode = TrendingMode.HOT_SEARCH).queryKey(TrendingSource.GMGN))
        assertEquals(options.queryKey(TrendingSource.PUMP), options.copy(chain = TrendingChain.BASE, period = "24h").queryKey(TrendingSource.PUMP))
    }
}
