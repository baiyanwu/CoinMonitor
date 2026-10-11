package io.baiyanwu.coinmonitor.ui.components

import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import org.junit.Assert.*
import org.junit.Test

class WatchItemIconModelTest {
    @Test fun `same named tokens on different chains never guess or substitute the chain image`() {
        val sol = watchItemIconModel(item(MarketType.ONCHAIN_TOKEN, "501"))
        val base = watchItemIconModel(item(MarketType.ONCHAIN_TOKEN, "8453"))
        assertFalse(sol.allowSymbolLookup)
        assertFalse(base.allowSymbolLookup)
        assertNull(sol.tokenIconUrl)
        assertNull(base.tokenIconUrl)
        assertEquals("501", sol.chainIndex)
        assertEquals("8453", base.chainIndex)
        assertEquals("P", coinIconInitial(sol.symbol))
    }

    @Test fun `token image stays separate from the chain badge and blank URLs remain missing`() {
        val token = item(MarketType.ONCHAIN_TOKEN, "1").copy(iconUrl = " https://example.com/pepe.png ")
        assertEquals("https://example.com/pepe.png", watchItemIconModel(token).tokenIconUrl)
        assertEquals("1", watchItemIconModel(token).chainIndex)
        assertNull(watchItemIconModel(token.copy(iconUrl = " ")).tokenIconUrl)
    }

    @Test fun `exchange futures use base currency lookup without attaching accidental chain metadata`() {
        val future = item(MarketType.CEX_USDT_FUTURES, "1").copy(symbol = "BTCUSDT")
        val model = watchItemIconModel(future)
        assertEquals("BTC", model.symbol)
        assertTrue(model.allowSymbolLookup)
        assertNull(model.chainIndex)
    }

    @Test fun `initial fallback keeps valid unicode and supports names with whitespace`() {
        assertEquals("P", coinIconInitial(" pepe "))
        assertEquals("币", coinIconInitial("币种"))
        assertEquals("🚀", coinIconInitial("🚀Moon"))
        assertEquals("?", coinIconInitial(" "))
    }

    private fun item(type: MarketType, chain: String?) = WatchItem(
        id = "test", symbol = "PEPE", name = "Pepe", exchangeSource = ExchangeSource.ONCHAIN,
        marketType = type, chainIndex = chain, addedAt = 0L
    )
}
