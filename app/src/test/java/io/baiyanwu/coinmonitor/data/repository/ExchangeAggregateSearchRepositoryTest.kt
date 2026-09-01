package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.network.BinanceAlphaApi
import io.baiyanwu.coinmonitor.data.network.BinanceApi
import io.baiyanwu.coinmonitor.data.network.BinanceExchangeInfoResponse
import io.baiyanwu.coinmonitor.data.network.BinanceFuturesApi
import io.baiyanwu.coinmonitor.data.network.BinanceSymbolRow
import io.baiyanwu.coinmonitor.data.network.BinanceTickerRow
import io.baiyanwu.coinmonitor.data.network.DexScreenerApi
import io.baiyanwu.coinmonitor.data.network.DexScreenerClient
import io.baiyanwu.coinmonitor.data.network.DexScreenerPair
import io.baiyanwu.coinmonitor.data.network.DexScreenerSearchResponse
import io.baiyanwu.coinmonitor.data.network.OkxApi
import io.baiyanwu.coinmonitor.data.network.OkxCandlesResponse
import io.baiyanwu.coinmonitor.data.network.OkxInstrumentRow
import io.baiyanwu.coinmonitor.data.network.OkxInstrumentsResponse
import io.baiyanwu.coinmonitor.data.network.OkxTickerResponse
import io.baiyanwu.coinmonitor.data.network.RequestRateLimiter
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeAggregateSearchRepositoryTest {
    @Test
    fun `one exchange failure does not hide successful exchange results`() = runBlocking {
        val repository = repository(
            binanceApi = SuccessfulBinanceApi,
            okxApi = PartiallySuccessfulOkxApi
        )

        var terminalError: Throwable? = null
        val emissions = mutableListOf<List<io.baiyanwu.coinmonitor.domain.model.WatchItem>>()
        repository.searchExchange("btc")
            .catch { terminalError = it }
            .toList(emissions)
        val results = emissions.last()

        assertEquals(setOf(ExchangeSource.BINANCE, ExchangeSource.OKX), results.map { it.exchangeSource }.toSet())
        assertEquals(setOf("binance:BTCUSDT", "okx:BTC-USDT"), results.map { it.id }.toSet())
        assertTrue(terminalError is PartialExchangeSearchException)
        assertEquals(1, (terminalError as PartialExchangeSearchException).failedSourceCount)
    }

    @Test
    fun `all exchange failures still surface an error`() = runBlocking {
        val error = runCatching {
            repository(
                alphaApi = FailingAlphaApi,
                binanceApi = FailingBinanceApi,
                binanceFuturesApi = FailingBinanceFuturesApi,
                okxApi = FailingOkxApi
            ).searchExchange("btc").toList()
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalStateException)
    }

    @Test
    fun `exchange results are emitted once after every source completes`() = runBlocking {
        val repository = repository(
            binanceApi = DelayedBinanceApi,
            okxApi = DelayedOkxApi
        )

        val emissions = repository.searchExchange("btc").toList()

        assertEquals(1, emissions.size)
        assertEquals(
            listOf("binance:BTCUSDT", "okx:BTC-USDT"),
            emissions.single().map { it.id }
        )
    }

    @Test
    fun `a transient source failure is retried before that exchange is omitted`() = runBlocking {
        val binanceApi = FlakyBinanceApi()
        val emissions = repository(
            binanceApi = binanceApi,
            okxApi = EmptyOkxApi
        ).searchExchange("btc").toList()

        assertEquals(2, binanceApi.calls)
        assertEquals(listOf("binance:BTCUSDT"), emissions.last().map { it.id })
    }

    private fun repository(
        alphaApi: BinanceAlphaApi = EmptyAlphaApi,
        binanceApi: BinanceApi,
        binanceFuturesApi: BinanceFuturesApi = EmptyBinanceFuturesApi,
        okxApi: OkxApi
    ) = DefaultMarketSearchRepository(
        alphaApi = alphaApi,
        binanceApi = binanceApi,
        binanceFuturesApi = binanceFuturesApi,
        okxApi = okxApi,
        dexScreenerClient = DexScreenerClient(
            api = UnusedDexScreenerApi,
            limiter = RequestRateLimiter(1_000, 1_000L)
        )
    )

    private object EmptyAlphaApi : BinanceAlphaApi {
        override suspend fun getExchangeInfo(): JsonObject = JsonObject(emptyMap())
        override suspend fun getTokenList(): JsonObject = JsonObject(emptyMap())
        override suspend fun getTicker(symbol: String): JsonObject = error("not used")
        override suspend fun getKlines(symbol: String, interval: String, limit: Int): JsonObject = error("not used")
    }

    private object FailingAlphaApi : BinanceAlphaApi {
        override suspend fun getExchangeInfo(): JsonObject = error("alpha unavailable")
        override suspend fun getTokenList(): JsonObject = error("alpha unavailable")
        override suspend fun getTicker(symbol: String): JsonObject = error("not used")
        override suspend fun getKlines(symbol: String, interval: String, limit: Int): JsonObject = error("not used")
    }

    private object SuccessfulBinanceApi : BinanceApi {
        override suspend fun getExchangeInfo() = BinanceExchangeInfoResponse(
            symbols = listOf(
                BinanceSymbolRow(
                    symbol = "BTCUSDT",
                    status = "TRADING",
                    baseAsset = "BTC",
                    quoteAsset = "USDT"
                )
            )
        )

        override suspend fun getTickers(symbols: String): List<BinanceTickerRow> = error("not used")
        override suspend fun getKlines(symbol: String, interval: String, limit: Int): JsonArray = error("not used")
    }

    private object FailingBinanceApi : BinanceApi {
        override suspend fun getExchangeInfo(): BinanceExchangeInfoResponse = error("binance unavailable")
        override suspend fun getTickers(symbols: String): List<BinanceTickerRow> = error("not used")
        override suspend fun getKlines(symbol: String, interval: String, limit: Int): JsonArray = error("not used")
    }

    private class FlakyBinanceApi : BinanceApi {
        var calls = 0

        override suspend fun getExchangeInfo(): BinanceExchangeInfoResponse {
            calls += 1
            if (calls == 1) error("temporary binance failure")
            return SuccessfulBinanceApi.getExchangeInfo()
        }

        override suspend fun getTickers(symbols: String): List<BinanceTickerRow> = error("not used")
        override suspend fun getKlines(symbol: String, interval: String, limit: Int): JsonArray = error("not used")
    }

    private object DelayedBinanceApi : BinanceApi {
        override suspend fun getExchangeInfo(): BinanceExchangeInfoResponse {
            delay(20)
            return SuccessfulBinanceApi.getExchangeInfo()
        }

        override suspend fun getTickers(symbols: String): List<BinanceTickerRow> = error("not used")
        override suspend fun getKlines(symbol: String, interval: String, limit: Int): JsonArray = error("not used")
    }

    private object FailingBinanceFuturesApi : BinanceFuturesApi {
        override suspend fun getExchangeInfo(): BinanceExchangeInfoResponse = error("binance futures unavailable")
        override suspend fun getTicker(symbol: String): BinanceTickerRow = error("not used")
        override suspend fun getKlines(symbol: String, interval: String, limit: Int): JsonArray = error("not used")
    }

    private object EmptyBinanceFuturesApi : BinanceFuturesApi {
        override suspend fun getExchangeInfo() = BinanceExchangeInfoResponse(emptyList())
        override suspend fun getTicker(symbol: String): BinanceTickerRow = error("not used")
        override suspend fun getKlines(symbol: String, interval: String, limit: Int): JsonArray = error("not used")
    }

    private object PartiallySuccessfulOkxApi : OkxApi {
        override suspend fun getSpotInstruments(instType: String) = OkxInstrumentsResponse(
            code = "0",
            data = listOf(
                OkxInstrumentRow(
                    instId = "BTC-USDT",
                    baseCcy = "BTC",
                    quoteCcy = "USDT",
                    state = "live"
                )
            )
        )

        override suspend fun getInstruments(instType: String): OkxInstrumentsResponse = error("okx futures unavailable")
        override suspend fun getTicker(instId: String): OkxTickerResponse = error("not used")
        override suspend fun getCandles(instId: String, bar: String, limit: Int): OkxCandlesResponse = error("not used")
    }

    private object FailingOkxApi : OkxApi {
        override suspend fun getSpotInstruments(instType: String): OkxInstrumentsResponse = error("okx unavailable")
        override suspend fun getInstruments(instType: String): OkxInstrumentsResponse = error("okx unavailable")
        override suspend fun getTicker(instId: String): OkxTickerResponse = error("not used")
        override suspend fun getCandles(instId: String, bar: String, limit: Int): OkxCandlesResponse = error("not used")
    }

    private object EmptyOkxApi : OkxApi {
        override suspend fun getSpotInstruments(instType: String) = OkxInstrumentsResponse("0", emptyList())
        override suspend fun getInstruments(instType: String) = OkxInstrumentsResponse("0", emptyList())
        override suspend fun getTicker(instId: String): OkxTickerResponse = error("not used")
        override suspend fun getCandles(instId: String, bar: String, limit: Int): OkxCandlesResponse = error("not used")
    }

    private object DelayedOkxApi : OkxApi {
        override suspend fun getSpotInstruments(instType: String): OkxInstrumentsResponse {
            delay(100)
            return PartiallySuccessfulOkxApi.getSpotInstruments(instType)
        }

        override suspend fun getInstruments(instType: String) = OkxInstrumentsResponse("0", emptyList())
        override suspend fun getTicker(instId: String): OkxTickerResponse = error("not used")
        override suspend fun getCandles(instId: String, bar: String, limit: Int): OkxCandlesResponse = error("not used")
    }

    private object UnusedDexScreenerApi : DexScreenerApi {
        override suspend fun searchPairs(query: String): DexScreenerSearchResponse = error("not used")
        override suspend fun getTokenPairs(chainId: String, tokenAddress: String): List<DexScreenerPair> = error("not used")
        override suspend fun getTokenPairsBatch(
            chainId: String,
            tokenAddresses: String
        ): List<DexScreenerPair> = error("not used")
    }
}
