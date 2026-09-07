package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.network.BinanceAlphaApi
import io.baiyanwu.coinmonitor.data.network.BinanceApi
import io.baiyanwu.coinmonitor.data.network.BinanceExchangeInfoResponse
import io.baiyanwu.coinmonitor.data.network.BinanceFuturesApi
import io.baiyanwu.coinmonitor.data.network.BinanceTickerRow
import io.baiyanwu.coinmonitor.data.network.DexScreenerApi
import io.baiyanwu.coinmonitor.data.network.DexScreenerClient
import io.baiyanwu.coinmonitor.data.network.DexScreenerPair
import io.baiyanwu.coinmonitor.data.network.DexScreenerSearchResponse
import io.baiyanwu.coinmonitor.data.network.GeckoTerminalApi
import io.baiyanwu.coinmonitor.data.network.GeckoTerminalClient
import io.baiyanwu.coinmonitor.data.network.GeckoTerminalOhlcvAttributes
import io.baiyanwu.coinmonitor.data.network.GeckoTerminalOhlcvData
import io.baiyanwu.coinmonitor.data.network.GeckoTerminalOhlcvResponse
import io.baiyanwu.coinmonitor.data.network.GeckoTerminalTokenInfoResponse
import io.baiyanwu.coinmonitor.data.network.OkxApi
import io.baiyanwu.coinmonitor.data.network.OkxCandlesResponse
import io.baiyanwu.coinmonitor.data.network.OkxInstrumentsResponse
import io.baiyanwu.coinmonitor.data.network.OkxTickerResponse
import io.baiyanwu.coinmonitor.data.network.RequestRateLimiter
import io.baiyanwu.coinmonitor.domain.model.ChainFamily
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.KlineInterval
import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.PoolTokenSide
import io.baiyanwu.coinmonitor.domain.model.QuoteState
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoTerminalKlineRepositoryTest {
    @Test
    fun `fixed pool kline uses side mapping default limit and short cache`() = runBlocking {
        val geckoApi = RecordingGeckoApi(
            rows = listOf(listOf(1_700_000_000.0, 1.0, 2.0, 0.5, 1.5, 100.0))
        )
        val repository = repository(geckoApi)

        val first = repository.fetchCandles(ITEM, KlineInterval.FIVE_MINUTES)
        val second = repository.fetchCandles(ITEM, KlineInterval.FIVE_MINUTES)

        assertEquals(first, second)
        assertEquals(1, geckoApi.calls)
        assertEquals("eth", geckoApi.network)
        assertEquals("pool", geckoApi.poolAddress)
        assertEquals("minute", geckoApi.timeframe)
        assertEquals(5, geckoApi.aggregate)
        assertEquals(240, geckoApi.limit)
        assertEquals("quote", geckoApi.token)
    }

    @Test
    fun `empty gecko response is explicit and never falls back to okx`() = runBlocking {
        val geckoApi = RecordingGeckoApi(rows = emptyList())
        val okxApi = RecordingOkxApi
        val repository = repository(geckoApi, okxApi)

        val error = runCatching {
            repository.fetchCandles(ITEM, KlineInterval.ONE_HOUR)
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("GeckoTerminal"))
        assertEquals(0, okxApi.calls)
    }

    @Test
    fun `dynamic chain uses upstream id as gecko network`() = runBlocking {
        val geckoApi = RecordingGeckoApi(
            rows = listOf(listOf(1_700_000_000.0, 1.0, 2.0, 0.5, 1.5, 100.0))
        )
        val robinhoodItem = ITEM.copy(
            id = "onchain:robinhood:0x1111111111111111111111111111111111111111",
            chainIndex = "robinhood"
        )

        repository(geckoApi).fetchCandles(robinhoodItem, KlineInterval.FIVE_MINUTES)

        assertEquals("robinhood", geckoApi.network)
    }

    @Test
    fun `three day candles request daily data and aggregate ohlcv locally`() = runBlocking {
        val daySeconds = 86_400.0
        val geckoApi = RecordingGeckoApi(
            rows = listOf(
                listOf(3 * daySeconds, 13.0, 16.0, 12.0, 15.0, 40.0),
                listOf(2 * daySeconds, 12.0, 14.0, 11.0, 13.0, 30.0),
                listOf(daySeconds, 11.0, 13.0, 9.0, 12.0, 20.0),
                listOf(0.0, 10.0, 12.0, 8.0, 11.0, 10.0)
            )
        )
        val repository = repository(geckoApi)

        val candles = repository.fetchCandles(ITEM, KlineInterval.THREE_DAYS, limit = 2)

        assertEquals("day", geckoApi.timeframe)
        assertEquals(1, geckoApi.aggregate)
        assertEquals(6, geckoApi.limit)
        assertEquals(2, candles.size)
        assertEquals(0L, candles[0].openTimeMillis)
        assertEquals(10.0, candles[0].open, 0.0)
        assertEquals(14.0, candles[0].high, 0.0)
        assertEquals(8.0, candles[0].low, 0.0)
        assertEquals(13.0, candles[0].close, 0.0)
        assertEquals(60.0, candles[0].volume, 0.0)
        assertEquals(3 * 86_400_000L, candles[1].openTimeMillis)
        assertEquals(15.0, candles[1].close, 0.0)
    }

    @Test
    fun `thirty day candles page daily history beyond the per request maximum`() = runBlocking {
        val daySeconds = 86_400.0
        val geckoApi = RecordingGeckoApi(
            rows = (0 until 7_200).map { day ->
                listOf(day * daySeconds, 1.0, 2.0, 0.5, 1.5, 100.0)
            }
        )
        val repository = repository(geckoApi)

        val candles = repository.fetchCandles(ITEM, KlineInterval.ONE_MONTH, limit = 240)

        assertEquals("day", geckoApi.timeframe)
        assertEquals(1, geckoApi.aggregate)
        assertEquals(240, candles.size)
        assertEquals(8, geckoApi.calls)
        assertEquals(List(7) { 1_000 } + 200, geckoApi.limits)
        assertEquals(null, geckoApi.beforeTimestamps.first())
        assertTrue(geckoApi.beforeTimestamps.drop(1).all { it != null })
    }

    @Test
    fun `week starts on monday and month remains a fixed thirty day bucket`() = runBlocking {
        val daySeconds = 86_400.0
        val weeklyApi = RecordingGeckoApi(
            rows = listOf(
                listOf(3 * daySeconds, 1.0, 2.0, 0.5, 1.5, 10.0),
                listOf(4 * daySeconds, 1.5, 2.5, 1.0, 2.0, 20.0)
            )
        )
        val monthlyApi = RecordingGeckoApi(
            rows = listOf(
                listOf(29 * daySeconds, 1.0, 2.0, 0.5, 1.5, 10.0),
                listOf(30 * daySeconds, 1.5, 2.5, 1.0, 2.0, 20.0)
            )
        )

        val weekly = repository(weeklyApi)
            .fetchCandles(ITEM, KlineInterval.ONE_WEEK, limit = 2)
        val monthly = repository(monthlyApi)
            .fetchCandles(ITEM, KlineInterval.ONE_MONTH, limit = 2)

        assertEquals(listOf(-3L, 4L).map { it * 86_400_000L }, weekly.map { it.openTimeMillis })
        assertEquals(listOf(0L, 30L).map { it * 86_400_000L }, monthly.map { it.openTimeMillis })
        assertEquals(1, weeklyApi.aggregate)
        assertEquals(1, monthlyApi.aggregate)
    }

    private fun repository(
        geckoApi: RecordingGeckoApi,
        okxApi: OkxApi = RecordingOkxApi
    ) = DefaultMarketKlineRepository(
        alphaApi = UnusedAlphaApi,
        binanceApi = UnusedBinanceApi,
        binanceFuturesApi = UnusedBinanceFuturesApi,
        okxApi = okxApi,
        dexScreenerClient = DexScreenerClient(UnusedDexApi, RequestRateLimiter(100, 1_000L)),
        geckoTerminalClient = GeckoTerminalClient(
            geckoApi,
            RequestRateLimiter(100, 1_000L)
        ),
        watchlistRepository = UnusedWatchlistRepository
    )

    private class RecordingGeckoApi(
        private val rows: List<List<Double>>
    ) : GeckoTerminalApi {
        var calls = 0
        var network: String? = null
        var poolAddress: String? = null
        var timeframe: String? = null
        var aggregate: Int? = null
        var limit: Int? = null
        var token: String? = null
        val limits = mutableListOf<Int>()
        val beforeTimestamps = mutableListOf<Long?>()

        override suspend fun getTokenInfo(
            network: String,
            tokenAddress: String
        ): GeckoTerminalTokenInfoResponse = error("not used")

        override suspend fun getPoolOhlcv(
            network: String,
            poolAddress: String,
            timeframe: String,
            aggregate: Int,
            limit: Int,
            currency: String,
            token: String,
            beforeTimestamp: Long?
        ): GeckoTerminalOhlcvResponse {
            calls += 1
            this.network = network
            this.poolAddress = poolAddress
            this.timeframe = timeframe
            this.aggregate = aggregate
            this.limit = limit
            this.token = token
            limits += limit
            beforeTimestamps += beforeTimestamp
            val page = rows
                .asSequence()
                .filter { row -> beforeTimestamp == null || row.first().toLong() < beforeTimestamp }
                .sortedByDescending { row -> row.first() }
                .take(limit)
                .toList()
            return GeckoTerminalOhlcvResponse(
                GeckoTerminalOhlcvData(GeckoTerminalOhlcvAttributes(page))
            )
        }
    }

    private object RecordingOkxApi : OkxApi {
        var calls = 0
        override suspend fun getSpotInstruments(instType: String): OkxInstrumentsResponse = error("not used")
        override suspend fun getInstruments(instType: String): OkxInstrumentsResponse = error("not used")
        override suspend fun getTicker(instId: String): OkxTickerResponse = error("not used")
        override suspend fun getCandles(instId: String, bar: String, limit: Int): OkxCandlesResponse {
            calls += 1
            return OkxCandlesResponse("0", emptyList())
        }
    }

    private object UnusedDexApi : DexScreenerApi {
        override suspend fun searchPairs(query: String): DexScreenerSearchResponse = error("not used")
        override suspend fun getTokenPairs(chainId: String, tokenAddress: String): List<DexScreenerPair> = error("not used")
        override suspend fun getTokenPairsBatch(chainId: String, tokenAddresses: String): List<DexScreenerPair> = error("not used")
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

    private object UnusedWatchlistRepository : WatchlistRepository {
        override fun observeWatchlist(): Flow<List<WatchItem>> = flowOf(emptyList())
        override fun observeHomeWatchlist(): Flow<List<WatchItem>> = flowOf(emptyList())
        override suspend fun getWatchlist(): List<WatchItem> = emptyList()
        override suspend fun add(item: WatchItem) = Unit
        override suspend fun remove(id: String) = Unit
        override suspend fun setHomePinned(id: String, pinned: Boolean) = Unit
        override suspend fun moveHomeItem(id: String, targetBeforeId: String?) = Unit
        override suspend fun movePinnedHomeItem(id: String, targetBeforeId: String?) = Unit
        override suspend fun updateQuotes(quotes: List<MarketQuote>) = Unit
        override suspend fun persistQuoteSnapshot(quotes: Map<String, QuoteState>) = Unit
        override suspend fun updateOnchainPoolBinding(
            id: String,
            poolAddress: String,
            side: PoolTokenSide
        ): Boolean = true
    }

    private companion object {
        val ITEM = WatchItem(
            id = "onchain:1:0x1111111111111111111111111111111111111111",
            symbol = "TGT",
            name = "Target",
            exchangeSource = ExchangeSource.ONCHAIN,
            marketType = MarketType.ONCHAIN_TOKEN,
            chainFamily = ChainFamily.EVM,
            chainIndex = "1",
            tokenAddress = "0x1111111111111111111111111111111111111111",
            poolAddress = "pool",
            poolTokenSide = PoolTokenSide.QUOTE,
            addedAt = 1L
        )
    }
}
