package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.network.BinanceAlphaApi
import io.baiyanwu.coinmonitor.data.network.BinanceApi
import io.baiyanwu.coinmonitor.data.network.BinanceExchangeInfoResponse
import io.baiyanwu.coinmonitor.data.network.BinanceFuturesApi
import io.baiyanwu.coinmonitor.data.network.BinanceTickerRow
import io.baiyanwu.coinmonitor.data.network.OkxApi
import io.baiyanwu.coinmonitor.data.network.OkxCandlesResponse
import io.baiyanwu.coinmonitor.data.network.OkxInstrumentsResponse
import io.baiyanwu.coinmonitor.data.network.OkxTickerResponse
import io.baiyanwu.coinmonitor.data.refresh.OnchainPollingStrategies
import io.baiyanwu.coinmonitor.data.refresh.OnchainPollingStrategy
import io.baiyanwu.coinmonitor.domain.model.ChainFamily
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OnchainDataProvider
import io.baiyanwu.coinmonitor.domain.model.OnchainRefreshMode
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class OnchainProviderRoutingTest {
    @Test
    fun `search falls back only when configured primary returns empty`() = runBlocking {
        val dex = FakeProvider(OnchainDataProvider.DEX_SCREENER, searchResults = emptyList())
        val okxItem = item("okx", OnchainDataProvider.OKX_DEX)
        val okx = FakeProvider(OnchainDataProvider.OKX_DEX, searchResults = listOf(okxItem))

        val results = searchRepository(listOf(dex, okx)).searchOnchain("TGT")

        assertEquals(listOf(okxItem), results)
        assertEquals(1, dex.searchCalls)
        assertEquals(1, okx.searchCalls)
    }

    @Test
    fun `search does not call fallback when primary has results`() = runBlocking {
        val dexItem = item("dex", OnchainDataProvider.DEX_SCREENER)
        val dex = FakeProvider(OnchainDataProvider.DEX_SCREENER, searchResults = listOf(dexItem))
        val okx = FakeProvider(OnchainDataProvider.OKX_DEX, searchResults = listOf(item("okx", OnchainDataProvider.OKX_DEX)))

        val results = searchRepository(listOf(dex, okx)).searchOnchain("TGT")

        assertEquals(listOf(dexItem), results)
        assertEquals(1, dex.searchCalls)
        assertEquals(0, okx.searchCalls)
    }

    @Test
    fun `quote repository uses configured priority before persisted provider`() = runBlocking {
        val dexItem = item("dex", OnchainDataProvider.DEX_SCREENER)
        val okxItem = item("okx", OnchainDataProvider.OKX_DEX)
        val dex = FakeProvider(OnchainDataProvider.DEX_SCREENER)
        val okx = FakeProvider(OnchainDataProvider.OKX_DEX)

        val quotes = quoteRepository(listOf(dex, okx)).fetchQuotes(listOf(dexItem, okxItem))

        assertEquals(setOf(dexItem.id, okxItem.id), quotes.map(MarketQuote::id).toSet())
        assertEquals(listOf(dexItem.id, okxItem.id), dex.quotedIds)
        assertEquals(emptyList<String>(), okx.quotedIds)
    }

    @Test
    fun `quote repository falls back only for unresolved items`() = runBlocking {
        val dexItem = item("dex", OnchainDataProvider.DEX_SCREENER)
        val okxItem = item("okx", OnchainDataProvider.OKX_DEX)
        val dex = FakeProvider(
            OnchainDataProvider.DEX_SCREENER,
            quotedResultIds = setOf(dexItem.id)
        )
        val okx = FakeProvider(OnchainDataProvider.OKX_DEX)

        val quotes = quoteRepository(listOf(dex, okx)).fetchQuotes(listOf(dexItem, okxItem))

        assertEquals(setOf(dexItem.id, okxItem.id), quotes.map(MarketQuote::id).toSet())
        assertEquals(listOf(dexItem.id, okxItem.id), dex.quotedIds)
        assertEquals(listOf(okxItem.id), okx.quotedIds)
    }

    @Test
    fun `quote repository respects reversed user order`() = runBlocking {
        val item = item("token", OnchainDataProvider.DEX_SCREENER)
        val dex = FakeProvider(OnchainDataProvider.DEX_SCREENER)
        val okx = FakeProvider(OnchainDataProvider.OKX_DEX)

        val quotes = quoteRepository(
            providers = listOf(dex, okx),
            order = listOf(OnchainDataProvider.OKX_DEX, OnchainDataProvider.DEX_SCREENER)
        ).fetchQuotes(listOf(item))

        assertEquals(listOf(item.id), quotes.map(MarketQuote::id))
        assertEquals(emptyList<String>(), dex.quotedIds)
        assertEquals(listOf(item.id), okx.quotedIds)
    }

    @Test
    fun `user order can make OKX primary`() = runBlocking {
        val dex = FakeProvider(
            OnchainDataProvider.DEX_SCREENER,
            searchResults = listOf(item("dex", OnchainDataProvider.DEX_SCREENER))
        )
        val okxItem = item("okx", OnchainDataProvider.OKX_DEX)
        val okx = FakeProvider(OnchainDataProvider.OKX_DEX, searchResults = listOf(okxItem))

        val results = searchRepository(
            providers = listOf(dex, okx),
            order = listOf(OnchainDataProvider.OKX_DEX, OnchainDataProvider.DEX_SCREENER)
        ).searchOnchain("TGT")

        assertEquals(listOf(okxItem), results)
        assertEquals(0, dex.searchCalls)
        assertEquals(1, okx.searchCalls)
    }

    @Test
    fun `search request failure falls back to next provider`() = runBlocking {
        val dex = FakeProvider(OnchainDataProvider.DEX_SCREENER, searchError = true)
        val okxItem = item("okx", OnchainDataProvider.OKX_DEX)
        val okx = FakeProvider(OnchainDataProvider.OKX_DEX, searchResults = listOf(okxItem))

        assertEquals(listOf(okxItem), searchRepository(listOf(dex, okx)).searchOnchain("TGT"))
        assertEquals(1, dex.searchCalls)
        assertEquals(1, okx.searchCalls)
    }

    @Test
    fun `polling plan binds batches to the user preferred provider`() {
        val dex = FakeProvider(OnchainDataProvider.DEX_SCREENER)
        val okx = FakeProvider(
            OnchainDataProvider.OKX_DEX,
            pollingStrategy = OnchainPollingStrategies.okxDex
        )
        val router = OnchainProviderRouter(
            providers = listOf(dex, okx),
            providerOrder = {
                listOf(OnchainDataProvider.OKX_DEX, OnchainDataProvider.DEX_SCREENER)
            }
        )

        val plan = router.createPollingPlan(
            items = listOf(item("token", OnchainDataProvider.DEX_SCREENER)),
            mode = OnchainRefreshMode.SMART,
            fixedIntervalSeconds = 30
        )

        assertEquals(OnchainDataProvider.OKX_DEX, plan.batches.single().provider)
    }

    @Test
    fun `router creates one shot fallback batch after preferred provider`() {
        val dex = FakeProvider(OnchainDataProvider.DEX_SCREENER)
        val okx = FakeProvider(OnchainDataProvider.OKX_DEX)
        val watchedItem = item("token", OnchainDataProvider.OKX_DEX)
        val router = OnchainProviderRouter(
            providers = listOf(dex, okx),
            providerOrder = {
                listOf(OnchainDataProvider.OKX_DEX, OnchainDataProvider.DEX_SCREENER)
            }
        )

        val fallback = router.createFallbackBatches(
            items = listOf(watchedItem),
            failedProvider = OnchainDataProvider.OKX_DEX,
            mode = OnchainRefreshMode.SMART,
            fixedIntervalSeconds = 30
        )

        assertEquals(OnchainDataProvider.DEX_SCREENER, fallback.single().provider)
        assertEquals(false, fallback.single().recurring)
    }

    @Test
    fun `provider bound batch executes only its planned item ids`() = runBlocking {
        val first = item("first", OnchainDataProvider.DEX_SCREENER)
        val second = item("second", OnchainDataProvider.DEX_SCREENER)
        val dex = FakeProvider(OnchainDataProvider.DEX_SCREENER)
        val router = OnchainProviderRouter(listOf(dex))
        val batch = router.createPollingPlan(
            items = listOf(first),
            mode = OnchainRefreshMode.SMART,
            fixedIntervalSeconds = 30
        ).batches.single()

        val result = router.executeBatch(batch, listOf(first, second))

        assertEquals(listOf(first.id), dex.quotedIds)
        assertEquals(listOf(first.id), result.quotes.map(MarketQuote::id))
    }

    @Test
    fun `unconfigured fallback is skipped`() = runBlocking {
        val dex = FakeProvider(OnchainDataProvider.DEX_SCREENER, searchResults = emptyList())
        val okx = FakeProvider(
            OnchainDataProvider.OKX_DEX,
            configured = false,
            searchResults = listOf(item("okx", OnchainDataProvider.OKX_DEX))
        )

        assertEquals(emptyList<WatchItem>(), searchRepository(listOf(dex, okx)).searchOnchain("TGT"))
        assertEquals(0, okx.searchCalls)
    }

    private fun searchRepository(
        providers: List<OnchainMarketProvider>,
        order: List<OnchainDataProvider> = OnchainDataProvider.entries.toList()
    ) = DefaultMarketSearchRepository(
        alphaApi = UnusedAlphaApi,
        binanceApi = UnusedBinanceApi,
        binanceFuturesApi = UnusedBinanceFuturesApi,
        okxApi = UnusedOkxApi,
        onchainProviders = providers,
        onchainProviderOrder = { order }
    )

    private fun quoteRepository(
        providers: List<OnchainMarketProvider>,
        order: List<OnchainDataProvider> = OnchainDataProvider.entries.toList()
    ) = DefaultMarketQuoteRepository(
        alphaApi = UnusedAlphaApi,
        binanceApi = UnusedBinanceApi,
        binanceFuturesApi = UnusedBinanceFuturesApi,
        okxApi = UnusedOkxApi,
        onchainProviders = providers,
        onchainProviderOrder = { order }
    )

    private fun item(id: String, provider: OnchainDataProvider) = WatchItem(
        id = "onchain:1:0x${id.hashCode().toUInt().toString(16).padStart(40, '0')}",
        symbol = id.uppercase(),
        name = id,
        exchangeSource = ExchangeSource.ONCHAIN,
        marketType = MarketType.ONCHAIN_TOKEN,
        chainFamily = ChainFamily.EVM,
        chainIndex = "1",
        tokenAddress = "0x${id.hashCode().toUInt().toString(16).padStart(40, '0')}",
        onchainDataProvider = provider,
        addedAt = 1L
    )

    private class FakeProvider(
        override val type: OnchainDataProvider,
        private val configured: Boolean = true,
        private val searchResults: List<WatchItem> = emptyList(),
        private val searchError: Boolean = false,
        private val quotedResultIds: Set<String>? = null,
        override val pollingStrategy: OnchainPollingStrategy = OnchainPollingStrategies.default
    ) : OnchainMarketProvider {
        var searchCalls = 0
        val quotedIds = mutableListOf<String>()

        override fun isConfigured(): Boolean = configured

        override suspend fun search(keyword: String, chainFamilyFilter: ChainFamily?): List<WatchItem> {
            searchCalls += 1
            if (searchError) error("provider failed")
            return searchResults
        }

        override suspend fun fetchQuotes(items: List<WatchItem>): List<MarketQuote> {
            quotedIds += items.map(WatchItem::id)
            return items.filter { item -> quotedResultIds?.let { item.id in it } ?: true }.map { item ->
                MarketQuote(item.id, item.symbol, item.name, priceUsd = 1.0, change24hPercent = 0.0)
            }
        }
    }

    private object UnusedAlphaApi : BinanceAlphaApi {
        override suspend fun getExchangeInfo(): JsonObject = error("not used")
        override suspend fun getTokenList(): JsonObject = error("not used")
        override suspend fun getTicker(symbol: String): JsonObject = error("not used")
        override suspend fun getKlines(symbol: String, interval: String, limit: Int): JsonObject = error("not used")
    }

    private object UnusedBinanceApi : BinanceApi {
        override suspend fun getExchangeInfo(): BinanceExchangeInfoResponse = error("not used")
        override suspend fun getTickers(symbols: String): List<BinanceTickerRow> = error("not used")
        override suspend fun getKlines(symbol: String, interval: String, limit: Int): JsonArray = error("not used")
    }

    private object UnusedBinanceFuturesApi : BinanceFuturesApi {
        override suspend fun getExchangeInfo(): BinanceExchangeInfoResponse = error("not used")
        override suspend fun getTicker(symbol: String): BinanceTickerRow = error("not used")
        override suspend fun getKlines(symbol: String, interval: String, limit: Int): JsonArray = error("not used")
    }

    private object UnusedOkxApi : OkxApi {
        override suspend fun getSpotInstruments(instType: String): OkxInstrumentsResponse = error("not used")
        override suspend fun getInstruments(instType: String): OkxInstrumentsResponse = error("not used")
        override suspend fun getTicker(instId: String): OkxTickerResponse = error("not used")
        override suspend fun getCandles(instId: String, bar: String, limit: Int): OkxCandlesResponse = error("not used")
    }
}
