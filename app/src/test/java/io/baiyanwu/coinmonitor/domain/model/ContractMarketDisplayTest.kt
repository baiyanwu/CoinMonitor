package io.baiyanwu.coinmonitor.domain.model

import io.baiyanwu.coinmonitor.overlay.QuoteFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class ContractMarketDisplayTest {
    @Test
    fun `usdt futures display symbol keeps compact exchange format but icon base uses base asset`() {
        val item = WatchItem(
            id = "binance-futures:BTCUSDT",
            symbol = "BTCUSDT",
            name = "BTC",
            exchangeSource = ExchangeSource.BINANCE,
            marketType = MarketType.CEX_USDT_FUTURES,
            addedAt = 1L
        )

        assertEquals("BTCUSDT", item.symbol)
        assertEquals("BTC", item.baseSymbol)
    }

    @Test
    fun `overlay price prefixes usdt futures with u marker`() {
        val item = WatchItem(
            id = "okx-futures:BTC-USDT-SWAP",
            symbol = "BTCUSDT",
            name = "BTC",
            exchangeSource = ExchangeSource.OKX,
            marketType = MarketType.CEX_USDT_FUTURES,
            lastPrice = 1234.56,
            addedAt = 1L
        )

        assertEquals("U1234.6", QuoteFormatter.formatOverlayPrice(item))
    }

    @Test
    fun `spot overlay price keeps existing format`() {
        val item = WatchItem(
            id = "binance:BTCUSDT",
            symbol = "BTC/USDT",
            name = "BTC",
            exchangeSource = ExchangeSource.BINANCE,
            lastPrice = 1234.56,
            addedAt = 1L
        )

        assertEquals("1234.6", QuoteFormatter.formatOverlayPrice(item))
    }
}
