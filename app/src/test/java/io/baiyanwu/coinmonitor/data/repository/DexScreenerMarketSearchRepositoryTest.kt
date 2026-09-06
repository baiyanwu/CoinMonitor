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
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.ChainFamily
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DexScreenerMarketSearchRepositoryTest {
    @Test
    fun `name search spans supported chains and deduplicates token within each chain`() = runBlocking {
        val fakeDex = FakeDexScreenerApi().apply {
            searchResponse = listOf(
                pair("ethereum", "eth-small", TARGET, 100.0),
                pair("ethereum", "eth-large", TARGET, 1_000.0),
                pair("bsc", "bsc-pool", TARGET, 9_000.0)
            )
        }
        val repository = repository(fakeDex)

        val results = repository.searchOnchain("TGT")

        assertEquals(2, results.size)
        assertEquals(setOf("eth-large", "bsc-pool"), results.mapNotNull { it.poolAddress }.toSet())
        assertEquals(setOf("1", "56"), results.mapNotNull { it.chainIndex }.toSet())
        assertTrue(results.all { it.exchangeSource == ExchangeSource.ONCHAIN })

        val ethereumResult = results.single { it.chainIndex == "1" }
        assertEquals("TGT / USDC", ethereumResult.symbol)
        assertEquals("TGT / USDC", ethereumResult.selectedPool?.pairLabel)
        assertEquals("eth-large", ethereumResult.selectedPool?.poolAddress)
        assertEquals(listOf("eth-large", "eth-small"), ethereumResult.poolOptions.map { it.poolAddress })
        assertEquals(1_000.0, ethereumResult.selectedPool?.liquidityUsd ?: 0.0, 0.0)
    }

    @Test
    fun `evm address search ignores selected chain and returns exact matches across chains`() = runBlocking {
        val mixedCaseAddress = "0xABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD"
        val fakeDex = FakeDexScreenerApi().apply {
            searchResponse = listOf(
                pair("ethereum", "eth-pool", mixedCaseAddress, 500.0),
                pair("bsc", "bsc-pool", mixedCaseAddress, 800.0),
                pair("base", "unrelated", OTHER, 2_000.0)
            )
        }
        val repository = repository(fakeDex)

        val results = repository.searchOnchain(mixedCaseAddress)

        assertEquals(1, fakeDex.searchCalls)
        assertEquals(null, fakeDex.lastTokenPairsChain)
        assertEquals(setOf("1", "56"), results.mapNotNull { it.chainIndex }.toSet())
        assertEquals(
            setOf(
                "onchain:1:${mixedCaseAddress.lowercase()}",
                "onchain:56:${mixedCaseAddress.lowercase()}"
            ),
            results.map { it.id }.toSet()
        )
        assertTrue(results.all { it.semanticKey.endsWith(mixedCaseAddress.lowercase()) })
    }

    @Test
    fun `solana address search detects solana without selecting sol tab`() = runBlocking {
        val solanaAddress = "So11111111111111111111111111111111111111112"
        val fakeDex = FakeDexScreenerApi().apply {
            searchResponse = listOf(pair("solana", "sol-pool", solanaAddress, 900.0))
        }
        val repository = repository(fakeDex)

        val result = repository.searchOnchain(solanaAddress).single()

        assertEquals(1, fakeDex.searchCalls)
        assertEquals("501", result.chainIndex)
        assertEquals(solanaAddress, result.tokenAddress)
        assertEquals("onchain:501:$solanaAddress", result.id)
    }

    @Test
    fun `unknown robinhood chain from upstream is displayed without local registration`() = runBlocking {
        val robinhoodAddress = "0x66c9ba158b2b80c2a51ca75f3e4155a682676c7a"
        val fakeDex = FakeDexScreenerApi().apply {
            searchResponse = listOf(pair("robinhood", "rh-pool", robinhoodAddress, 12_000.0))
        }
        val repository = repository(fakeDex)

        val result = repository.searchOnchain(robinhoodAddress).single()

        assertEquals("robinhood", result.chainIndex)
        assertEquals(ChainFamily.EVM, result.chainFamily)
        assertEquals(robinhoodAddress, result.tokenAddress)
        assertEquals("onchain:robinhood:$robinhoodAddress", result.id)
        assertEquals("rh-pool", result.poolAddress)
    }

    @Test
    fun `unknown non evm chain from upstream is displayed and preserves address case`() = runBlocking {
        val suiAddress = "0x${"Ab".repeat(32)}"
        val suiQuoteAddress = "0x${"Cd".repeat(32)}"
        val fakeDex = FakeDexScreenerApi().apply {
            searchResponse = listOf(
                DexScreenerPair(
                    chainId = "sui",
                    dexId = "cetus",
                    pairAddress = "0x${"Ef".repeat(32)}",
                    baseToken = DexScreenerToken(suiAddress, "Target", "TGT"),
                    quoteToken = DexScreenerToken(suiQuoteAddress, "USD Coin", "USDC"),
                    priceNative = "2",
                    priceUsd = "2",
                    volume = mapOf("h24" to 100.0),
                    priceChange = mapOf("h24" to 1.5),
                    liquidity = DexScreenerLiquidity(2_000.0)
                )
            )
        }
        val repository = repository(fakeDex)

        val result = repository.searchOnchain("TGT").single()

        assertEquals("sui", result.chainIndex)
        assertEquals(ChainFamily.OTHER, result.chainFamily)
        assertEquals(suiAddress, result.tokenAddress)
    }

    @Test
    fun `onchain results are not capped by a local result limit`() = runBlocking {
        val fakeDex = FakeDexScreenerApi().apply {
            searchResponse = (1..81).map { index ->
                val address = "0x${index.toString(16).padStart(40, '0')}"
                pair("ethereum", "pool-$index", address, index.toDouble())
            }
        }

        val results = repository(fakeDex).searchOnchain("TGT")

        assertEquals(81, results.size)
    }

    private fun repository(fakeDex: FakeDexScreenerApi): DefaultMarketSearchRepository {
        return DefaultMarketSearchRepository(
            alphaApi = UnusedAlphaApi,
            binanceApi = UnusedBinanceApi,
            binanceFuturesApi = UnusedBinanceFuturesApi,
            okxApi = UnusedOkxApi,
            dexScreenerClient = DexScreenerClient(
                api = fakeDex,
                limiter = RequestRateLimiter(1_000, 1_000L)
            )
        )
    }

    private fun pair(
        chainId: String,
        pairAddress: String,
        tokenAddress: String,
        liquidityUsd: Double
    ) = DexScreenerPair(
        chainId = chainId,
        dexId = "uniswap",
        labels = listOf("v3"),
        pairAddress = pairAddress,
        baseToken = DexScreenerToken(tokenAddress, "Target", "TGT"),
        quoteToken = DexScreenerToken(OTHER, "USD Coin", "USDC"),
        priceNative = "2",
        priceUsd = "2",
        volume = mapOf("h24" to 100.0),
        priceChange = mapOf("h24" to 1.5),
        liquidity = DexScreenerLiquidity(liquidityUsd)
    )

    private class FakeDexScreenerApi : DexScreenerApi {
        var searchResponse: List<DexScreenerPair> = emptyList()
        var tokenPairsResponse: List<DexScreenerPair> = emptyList()
        var searchCalls: Int = 0
        var lastTokenPairsChain: String? = null
        var lastTokenAddress: String? = null

        override suspend fun searchPairs(query: String): DexScreenerSearchResponse {
            searchCalls += 1
            return DexScreenerSearchResponse(searchResponse)
        }

        override suspend fun getTokenPairs(chainId: String, tokenAddress: String): List<DexScreenerPair> {
            lastTokenPairsChain = chainId
            lastTokenAddress = tokenAddress
            return tokenPairsResponse
        }

        override suspend fun getTokenPairsBatch(
            chainId: String,
            tokenAddresses: String
        ): List<DexScreenerPair> = error("not used")
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
        const val TARGET = "0x1111111111111111111111111111111111111111"
        const val OTHER = "0x2222222222222222222222222222222222222222"
    }
}
