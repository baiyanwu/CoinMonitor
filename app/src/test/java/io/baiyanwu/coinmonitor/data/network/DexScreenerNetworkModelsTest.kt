package io.baiyanwu.coinmonitor.data.network

import io.baiyanwu.coinmonitor.domain.model.ChainFamily
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DexScreenerNetworkModelsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `search response accepts an explicitly null pair list`() {
        val response = json.decodeFromString<DexScreenerSearchResponse>(
            """{"schemaVersion":"1.0.0","pairs":null}"""
        )

        assertNull(response.pairs)
    }

    @Test
    fun `pair accepts official nullable labels and price change`() {
        val response = json.decodeFromString<DexScreenerSearchResponse>(
            """
            {
              "pairs": [
                {
                  "chainId": "ethereum",
                  "dexId": "uniswap",
                  "pairAddress": "pool",
                  "labels": null,
                  "baseToken": {
                    "address": "$TARGET",
                    "name": "Target",
                    "symbol": "TGT"
                  },
                  "quoteToken": {
                    "address": "$OTHER",
                    "name": "USD Coin",
                    "symbol": "USDC"
                  },
                  "priceNative": "2",
                  "priceUsd": "2",
                  "priceChange": null,
                  "liquidity": {"usd": 1000}
                }
              ]
            }
            """.trimIndent()
        )

        val pair = response.pairs.orEmpty().single()
        assertNull(pair.labels)
        assertNull(pair.priceChange)
        val selected = DexScreenerPairSelector.select(
            pairs = listOf(pair),
            tokenAddress = TARGET,
            family = ChainFamily.EVM
        )
        assertEquals("pool", selected?.pair?.pairAddress)
        assertNull(selected?.change24hPercent)
    }

    private companion object {
        const val TARGET = "0x1111111111111111111111111111111111111111"
        const val OTHER = "0x2222222222222222222222222222222222222222"
    }
}
