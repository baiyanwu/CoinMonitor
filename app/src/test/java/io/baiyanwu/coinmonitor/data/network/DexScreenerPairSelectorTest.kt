package io.baiyanwu.coinmonitor.data.network

import io.baiyanwu.coinmonitor.domain.model.ChainFamily
import io.baiyanwu.coinmonitor.domain.model.PoolTokenSide
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response as RetrofitResponse

class DexScreenerPairSelectorTest {
    @Test
    fun `selector rejects invalid pools and prioritizes base side`() {
        val invalid = pair("invalid", targetInBase = true, liquidityUsd = 0.0)
        val quote = pair("quote", targetInBase = false, liquidityUsd = 1_000_000.0)
        val base = pair("base", targetInBase = true, liquidityUsd = 100.0)

        val selected = DexScreenerPairSelector.select(
            pairs = listOf(invalid, quote, base),
            tokenAddress = TARGET,
            family = ChainFamily.EVM
        )

        assertEquals("base", selected?.pair?.pairAddress)
        assertEquals(PoolTokenSide.BASE, selected?.tokenSide)
    }

    @Test
    fun `candidates preserve ranking and remove duplicate pool rows`() {
        val invalid = pair("invalid", targetInBase = true, liquidityUsd = 0.0)
        val quote = pair("quote", targetInBase = false, liquidityUsd = 1_000_000.0)
        val baseSmall = pair("base-small", targetInBase = true, liquidityUsd = 100.0)
        val baseLarge = pair("base-large", targetInBase = true, liquidityUsd = 10_000.0)

        val candidates = DexScreenerPairSelector.candidates(
            pairs = listOf(quote, invalid, baseSmall, baseLarge, baseLarge),
            tokenAddress = TARGET,
            family = ChainFamily.EVM
        )

        assertEquals(
            listOf("base-large", "base-small", "quote"),
            candidates.map { it.pair.pairAddress }
        )
    }

    @Test
    fun `selector reuses preferred pool before liquidity ranking`() {
        val preferred = pair("preferred", targetInBase = true, liquidityUsd = 100.0)
        val larger = pair("larger", targetInBase = true, liquidityUsd = 10_000.0)

        val selected = DexScreenerPairSelector.select(
            pairs = listOf(larger, preferred),
            tokenAddress = TARGET.uppercase(),
            family = ChainFamily.EVM,
            preferredPoolAddress = "PREFERRED"
        )

        assertEquals("preferred", selected?.pair?.pairAddress)
    }

    @Test
    fun `quote side price is derived without inventing change percent`() {
        val selected = DexScreenerPairSelector.select(
            pairs = listOf(
                pair(
                    pairAddress = "quote",
                    targetInBase = false,
                    liquidityUsd = 100.0,
                    basePriceUsd = "2000",
                    priceNative = "1000"
                )
            ),
            tokenAddress = TARGET,
            family = ChainFamily.EVM
        )

        assertEquals(PoolTokenSide.QUOTE, selected?.tokenSide)
        assertEquals("TGT / OTH", selected?.pairLabel)
        assertEquals(2.0, selected?.priceUsd ?: 0.0, 0.000001)
        assertNull(selected?.change24hPercent)
    }

    @Test
    fun `pinned pool changes only after repeated absence but invalid pool changes immediately`() {
        val alternative = pair("alternative", targetInBase = true, liquidityUsd = 500.0)
        val missingPolicy = PinnedPoolSelectionPolicy(missThreshold = 3)

        assertNull(missingPolicy.select("id", listOf(alternative), TARGET, ChainFamily.EVM, "old"))
        assertNull(missingPolicy.select("id", listOf(alternative), TARGET, ChainFamily.EVM, "old"))
        assertEquals(
            "alternative",
            missingPolicy.select("id", listOf(alternative), TARGET, ChainFamily.EVM, "old")
                ?.pair?.pairAddress
        )

        val invalidOld = pair("old", targetInBase = true, liquidityUsd = 0.0)
        val invalidPolicy = PinnedPoolSelectionPolicy(missThreshold = 3)
        assertEquals(
            "alternative",
            invalidPolicy.select(
                "id",
                listOf(invalidOld, alternative),
                TARGET,
                ChainFamily.EVM,
                "old"
            )?.pair?.pairAddress
        )
    }

    @Test
    fun `retry after supports seconds and http date`() {
        assertEquals(5_000L, parseRetryAfterMillis("5"))
        val now = Instant.parse("2026-09-07T12:00:00Z").toEpochMilli()
        assertEquals(
            5_000L,
            parseRetryAfterMillis("Mon, 7 Sep 2026 12:00:05 GMT", now)
        )
        assertNull(parseRetryAfterMillis("invalid"))
    }

    @Test
    fun `dex client retries a rate limited request`() = runBlocking {
        var calls = 0
        val api = object : DexScreenerApi {
            override suspend fun searchPairs(query: String): DexScreenerSearchResponse {
                calls += 1
                if (calls == 1) throw rateLimitException()
                return DexScreenerSearchResponse(listOf(pair("pool", true, 100.0)))
            }

            override suspend fun getTokenPairs(
                chainId: String,
                tokenAddress: String
            ): List<DexScreenerPair> = error("not used")

            override suspend fun getTokenPairsBatch(
                chainId: String,
                tokenAddresses: String
            ): List<DexScreenerPair> = error("not used")
        }
        val client = DexScreenerClient(api, RequestRateLimiter(100, 1_000L))

        assertEquals(1, client.searchPairs("TGT").size)
        assertEquals(2, calls)
    }

    @Test
    fun `dex client normalizes a null search pair list to empty`() = runBlocking {
        val api = object : DexScreenerApi {
            override suspend fun searchPairs(query: String): DexScreenerSearchResponse {
                return DexScreenerSearchResponse(pairs = null)
            }

            override suspend fun getTokenPairs(
                chainId: String,
                tokenAddress: String
            ): List<DexScreenerPair> = error("not used")

            override suspend fun getTokenPairsBatch(
                chainId: String,
                tokenAddresses: String
            ): List<DexScreenerPair> = error("not used")
        }
        val client = DexScreenerClient(api, RequestRateLimiter(100, 1_000L))

        assertEquals(emptyList<DexScreenerPair>(), client.searchPairs("missing"))
    }

    private fun rateLimitException(): HttpException {
        val mediaType = "application/json".toMediaType()
        val rawResponse = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://api.dexscreener.com/latest/dex/search").build())
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .header("Retry-After", "0")
            .body("{}".toResponseBody(mediaType))
            .build()
        return HttpException(
            RetrofitResponse.error<DexScreenerSearchResponse>(
                "{}".toResponseBody(mediaType),
                rawResponse
            )
        )
    }

    private fun pair(
        pairAddress: String,
        targetInBase: Boolean,
        liquidityUsd: Double,
        volume24h: Double = 100.0,
        basePriceUsd: String = "2",
        priceNative: String = "2"
    ): DexScreenerPair {
        val target = DexScreenerToken(TARGET, "Target", "TGT")
        val other = DexScreenerToken(OTHER, "Other", "OTH")
        return DexScreenerPair(
            chainId = "ethereum",
            dexId = "uniswap",
            labels = listOf("v3"),
            pairAddress = pairAddress,
            baseToken = if (targetInBase) target else other,
            quoteToken = if (targetInBase) other else target,
            priceNative = priceNative,
            priceUsd = basePriceUsd,
            volume = mapOf("h24" to volume24h),
            priceChange = mapOf("h24" to 3.5),
            liquidity = DexScreenerLiquidity(liquidityUsd)
        )
    }

    private companion object {
        const val TARGET = "0x1111111111111111111111111111111111111111"
        const val OTHER = "0x2222222222222222222222222222222222222222"
    }
}
