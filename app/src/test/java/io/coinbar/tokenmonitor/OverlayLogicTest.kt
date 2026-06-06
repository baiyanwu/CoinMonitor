package io.baiyanwu.coinmonitor

import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.overlay.OverlayBatchPlanner
import io.baiyanwu.coinmonitor.overlay.PriceTextSizer
import io.baiyanwu.coinmonitor.overlay.QuoteFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayLogicTest {
    @Test
    fun `overlay batch planner returns all items without pagination`() {
        val items = (1..8).map { index ->
            WatchItem(
                id = "binance:TEST$index",
                symbol = "TEST$index/USDT",
                name = "Test $index",
                exchangeSource = ExchangeSource.BINANCE,
                addedAt = index.toLong()
            )
        }

        val batch = OverlayBatchPlanner.plan(items, maxPerPage = 5, cursor = 0)

        assertEquals(8, batch.items.size)
        assertEquals(0, batch.nextCursor)
        assertEquals(null, batch.pageLabel)
    }

    @Test
    fun `price formatter keeps small values precision`() {
        assertEquals("0.0₄1234", QuoteFormatter.formatPrice(0.00001234))
        assertEquals("12.340", QuoteFormatter.formatPrice(12.34))
    }

    @Test
    fun `price text sizer shrinks for long numbers`() {
        val shortSize = PriceTextSizer.resolveTextSizeSp("12.34")
        val longSize = PriceTextSizer.resolveTextSizeSp("123456789.123456")

        assertEquals(true, shortSize > longSize)
    }
}
