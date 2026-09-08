package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.network.BinanceAlphaApi
import io.baiyanwu.coinmonitor.data.network.BinanceApi
import io.baiyanwu.coinmonitor.data.network.BinanceExchangeInfoResponse
import io.baiyanwu.coinmonitor.data.network.BinanceFuturesApi
import io.baiyanwu.coinmonitor.data.network.BinanceTickerRow
import io.baiyanwu.coinmonitor.data.network.DexScreenerApi
import io.baiyanwu.coinmonitor.data.network.DexScreenerClient
import io.baiyanwu.coinmonitor.data.network.DexScreenerLiquidity
import io.baiyanwu.coinmonitor.data.network.DexScreenerPair
import io.baiyanwu.coinmonitor.data.network.DexScreenerSearchResponse
import io.baiyanwu.coinmonitor.data.network.DexScreenerToken
import io.baiyanwu.coinmonitor.data.network.OkxApi
import io.baiyanwu.coinmonitor.data.network.OkxCandlesResponse
import io.baiyanwu.coinmonitor.data.network.OkxInstrumentsResponse
import io.baiyanwu.coinmonitor.data.network.OkxTickerResponse
import io.baiyanwu.coinmonitor.data.network.RequestRateLimiter
import io.baiyanwu.coinmonitor.domain.model.ChainFamily
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.PoolTokenSide
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DexScreenerMarketQuoteRepositoryTest {
    @Test
    fun `one chain timeout does not discard another chain quote`() = runBlocking {
        val fakeDex = QuoteDexApi(timeoutChain = "bsc")
        val repository = repository(fakeDex)
        val ethereum = item("eth", "1", ETH_ADDRESS)
        val bsc = item("bsc", "56", BSC_ADDRESS)

        val quotes = repository.fetchQuotes(listOf(ethereum, bsc))

        assertEquals(listOf(ethereum.id), quotes.map { it.id })
        assertEquals("pool-ethereum", quotes.single().poolAddress)
        assertEquals(null, quotes.single().requestedPoolAddress)
        assertEquals(2_000_000.0, quotes.single().marketCap!!, 0.0)
    }

    @Test
    fun `dex quote batching never submits more than thirty addresses`() = runBlocking {
        val fakeDex = QuoteDexApi(returnPairs = false)
        val repository = repository(fakeDex)
        val items = (1..31).map { index ->
            item(
                id = "eth-$index",
                chainIndex = "1",
                address = "0x${index.toString(16).padStart(40, '0')}"
            )
        }

        assertEquals(emptyList<MarketQuote>(), repository.fetchQuotes(items))
        assertEquals(listOf(30, 1), fakeDex.batchSizes)
    }

    @Test
    fun `quote cancellation is never converted to an empty partial result`() = runBlocking {
        val repository = repository(QuoteDexApi(cancellationChain = "ethereum"))

        val error = runCatching {
            repository.fetchQuotes(listOf(item("eth", "1", ETH_ADDRESS)))
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
    }

    @Test
    fun `dynamic chain quote uses upstream chain id without local registration`() = runBlocking {
        val fakeDex = QuoteDexApi()
        val repository = repository(fakeDex)
        val robinhood = item("robinhood", "robinhood", ETH_ADDRESS)

        val quote = repository.fetchQuotes(listOf(robinhood)).single()

        assertEquals("pool-robinhood", quote.poolAddress)
        assertEquals(listOf("robinhood"), fakeDex.requestedChains)
    }

    @Test
    fun `quote-side token never inherits base-token market cap`() = runBlocking {
        val repository = repository(QuoteDexApi(targetAsQuote = true))

        val quote = repository.fetchQuotes(listOf(item("eth", "1", ETH_ADDRESS))).single()

        assertEquals(null, quote.marketCap)
    }

    private fun repository(fakeDex: QuoteDexApi) = DefaultMarketQuoteRepository(
        alphaApi = UnusedAlphaApi,
        binanceApi = UnusedBinanceApi,
        binanceFuturesApi = UnusedBinanceFuturesApi,
        okxApi = UnusedOkxApi,
        dexScreenerClient = DexScreenerClient(
            fakeDex,
            RequestRateLimiter(1_000, 1_000L)
        )
    )

    private fun item(id: String, chainIndex: String, address: String) = WatchItem(
        id = "onchain:$chainIndex:$address",
        symbol = id.uppercase(),
        name = id,
        exchangeSource = ExchangeSource.ONCHAIN,
        marketType = MarketType.ONCHAIN_TOKEN,
        chainFamily = ChainFamily.EVM,
        chainIndex = chainIndex,
        tokenAddress = address,
        addedAt = 1L
    )

    private class QuoteDexApi(
        private val timeoutChain: String? = null,
        private val cancellationChain: String? = null,
        private val returnPairs: Boolean = true,
        private val targetAsQuote: Boolean = false
    ) : DexScreenerApi {
        val batchSizes = mutableListOf<Int>()
        val requestedChains = mutableListOf<String>()

        override suspend fun searchPairs(query: String): DexScreenerSearchResponse = error("not used")

        override suspend fun getTokenPairs(
            chainId: String,
            tokenAddress: String
        ): List<DexScreenerPair> = error("not used")

        override suspend fun getTokenPairsBatch(
            chainId: String,
            tokenAddresses: String
        ): List<DexScreenerPair> {
            requestedChains += chainId
            val addresses = tokenAddresses.split(',')
            batchSizes += addresses.size
            if (chainId == cancellationChain) throw CancellationException("cancelled")
            if (chainId == timeoutChain) throw SocketTimeoutException("simulated timeout")
            if (!returnPairs) return emptyList()
            return addresses.map { address ->
                DexScreenerPair(
                    chainId = chainId,
                    pairAddress = "pool-$chainId",
                    baseToken = if (targetAsQuote) {
                        DexScreenerToken(USD_ADDRESS, "USD Coin", "USDC")
                    } else {
                        DexScreenerToken(address, "Target", "TGT")
                    },
                    quoteToken = if (targetAsQuote) {
                        DexScreenerToken(address, "Target", "TGT")
                    } else {
                        DexScreenerToken(USD_ADDRESS, "USD Coin", "USDC")
                    },
                    priceNative = "2",
                    priceUsd = "2",
                    volume = mapOf("h24" to 100.0),
                    priceChange = mapOf("h24" to 1.0),
                    liquidity = DexScreenerLiquidity(1_000.0),
                    marketCap = 2_000_000.0
                )
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

    private companion object {
        const val ETH_ADDRESS = "0x1111111111111111111111111111111111111111"
        const val BSC_ADDRESS = "0x2222222222222222222222222222222222222222"
        const val USD_ADDRESS = "0x3333333333333333333333333333333333333333"
    }
}
