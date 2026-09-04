package io.baiyanwu.coinmonitor.ui.home

import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.ui.search.SearchMode
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeMarketModeTest {
    @Test
    fun `home modes separate market types without changing their order`() {
        val spot = watchItem("spot", MarketType.CEX_SPOT, ExchangeSource.BINANCE)
        val onchainA = watchItem("onchain-a", MarketType.ONCHAIN_TOKEN, ExchangeSource.ONCHAIN)
        val futures = watchItem("futures", MarketType.CEX_USDT_FUTURES, ExchangeSource.OKX)
        val onchainB = watchItem("onchain-b", MarketType.ONCHAIN_TOKEN, ExchangeSource.ONCHAIN)
        val items = listOf(spot, onchainA, futures, onchainB)

        assertEquals(
            listOf(spot, futures),
            filterHomeWatchItems(items, SearchMode.EXCHANGE)
        )
        assertEquals(
            listOf(onchainA, onchainB),
            filterHomeWatchItems(items, SearchMode.ONCHAIN)
        )
    }

    private fun watchItem(
        id: String,
        marketType: MarketType,
        exchangeSource: ExchangeSource
    ): WatchItem = WatchItem(
        id = id,
        symbol = id.uppercase(),
        name = id,
        exchangeSource = exchangeSource,
        marketType = marketType,
        addedAt = id.length.toLong()
    )
}
