package io.baiyanwu.coinmonitor.ui.home

import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomePresentationTest {
    @Test
    fun `currency display keeps tiny price precision and missing values`() {
        val item = item("TOKEN", MarketType.ONCHAIN_TOKEN)
        assertEquals("--", formatHomeWatchValue(item, false))
        assertEquals("--", formatHomeWatchValue(item.copy(lastPrice = Double.NaN), false))
        assertEquals("$0.0₅1234", formatHomeWatchValue(item.copy(lastPrice = 0.000001234), false))
        assertEquals("$1.5M", formatHomeWatchValue(item.copy(marketCap = 1_500_000.0), true))
    }

    @Test
    fun `display labels do not guess quote assets for chain symbols`() {
        assertEquals("BTC / USDT", homePairLabel(item("BTC/USDT", MarketType.CEX_SPOT)))
        assertEquals("ETH / USDT", homePairLabel(item("ETHUSDT", MarketType.CEX_USDT_FUTURES)))
        assertEquals("USDT", homePairLabel(item("USDT", MarketType.ONCHAIN_TOKEN)))
        assertEquals("SOL / USDC", homePairLabel(item("SOL/USDC", MarketType.ONCHAIN_TOKEN)))
    }

    @Test
    fun `copy action uses full token contract and never substitutes pool address`() {
        val chain = item("TOKEN", MarketType.ONCHAIN_TOKEN).copy(tokenAddress = " 0x1234567890abcdef ", poolAddress = "pool-not-token")
        assertEquals("0x1234567890abcdef", homeContractAddress(chain))
        assertNull(homeContractAddress(chain.copy(tokenAddress = null)))
        assertNull(homeContractAddress(chain.copy(marketType = MarketType.CEX_SPOT)))
    }

    private fun item(symbol: String, type: MarketType) = WatchItem(
        id = "test", symbol = symbol, name = symbol, marketType = type,
        exchangeSource = ExchangeSource.BINANCE, addedAt = 1L
    )
}
