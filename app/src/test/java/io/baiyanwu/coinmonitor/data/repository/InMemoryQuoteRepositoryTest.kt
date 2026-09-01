package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.domain.model.LivePriceTrend
import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryQuoteRepositoryTest {
    @Test
    fun `pool switch resets trend previous price and stale change`() {
        val repository = InMemoryQuoteRepository()
        repository.applyQuotes(
            listOf(MarketQuote("id", "TGT", "Target", 1.0, 12.0))
        )
        repository.applyQuotes(
            listOf(MarketQuote("id", "TGT", "Target", 2.0, null, resetTrend = true))
        )

        val quote = requireNotNull(repository.getQuote("id"))
        assertEquals(2.0, quote.lastPrice, 0.0)
        assertEquals(LivePriceTrend.NEUTRAL, quote.liveTrend)
        assertNull(quote.previousPrice)
        assertNull(quote.change24hPercent)
    }
}
