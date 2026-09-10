package io.baiyanwu.coinmonitor.domain.model

import io.baiyanwu.coinmonitor.overlay.QuoteFormatter
import io.baiyanwu.coinmonitor.overlay.MarketCapFlashPolicy
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

    @Test
    fun `market cap mode only replaces the onchain primary value`() {
        val onchain = WatchItem(
            id = "onchain:1:token",
            symbol = "TGT / USDC",
            name = "Target",
            exchangeSource = ExchangeSource.ONCHAIN,
            marketType = MarketType.ONCHAIN_TOKEN,
            lastPrice = 0.25,
            marketCap = 12_340_000.0,
            addedAt = 1L
        )
        val spot = WatchItem(
            id = "binance:BTCUSDT",
            symbol = "BTC/USDT",
            name = "BTC",
            exchangeSource = ExchangeSource.BINANCE,
            lastPrice = 1234.56,
            marketCap = 12_340_000.0,
            addedAt = 1L
        )

        assertEquals("\$12.34M", QuoteFormatter.formatWatchValue(onchain, true))
        assertEquals("--", QuoteFormatter.formatWatchValue(onchain.copy(marketCap = null), true))
        assertEquals("0.2500", QuoteFormatter.formatWatchValue(onchain, false))
        assertEquals("1234.6", QuoteFormatter.formatWatchValue(spot, true))
    }

    @Test
    fun `market cap flashes only for a new real price movement`() {
        val item = WatchItem(
            id = "onchain:1:token",
            symbol = "TGT / USDC",
            name = "Target",
            exchangeSource = ExchangeSource.ONCHAIN,
            marketType = MarketType.ONCHAIN_TOKEN,
            lastPrice = 2.0,
            previousPrice = 1.0,
            lastUpdatedAt = 20L,
            addedAt = 1L
        )

        assertEquals(true, MarketCapFlashPolicy.shouldFlash(item, true, 10L))
        assertEquals(false, MarketCapFlashPolicy.shouldFlash(item, true, 20L))
        assertEquals(false, MarketCapFlashPolicy.shouldFlash(item.copy(previousPrice = 2.0), true, 10L))
        assertEquals(false, MarketCapFlashPolicy.shouldFlash(item, false, 10L))
    }
}
