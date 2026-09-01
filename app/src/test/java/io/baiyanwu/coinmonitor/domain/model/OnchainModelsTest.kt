package io.baiyanwu.coinmonitor.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnchainModelsTest {
    @Test
    fun `registry maps all seventeen supported chains`() {
        val expected = mapOf(
            "1" to ("ethereum" to "eth"),
            "8453" to ("base" to "base"),
            "56" to ("bsc" to "bsc"),
            "42161" to ("arbitrum" to "arbitrum"),
            "137" to ("polygon" to "polygon_pos"),
            "10" to ("optimism" to "optimism"),
            "43114" to ("avalanche" to "avax"),
            "59144" to ("linea" to "linea"),
            "534352" to ("scroll" to "scroll"),
            "81457" to ("blast" to "blast"),
            "34443" to ("mode" to "mode"),
            "5000" to ("mantle" to "mantle"),
            "1101" to ("polygonzkevm" to "polygon-zkevm"),
            "324" to ("zksync" to "zksync"),
            "250" to ("fantom" to "ftm"),
            "7000" to ("zetachain" to "zetachain"),
            "501" to ("solana" to "solana")
        )

        assertEquals(17, OnchainChainRegistry.entries.size)
        assertEquals(17, OnchainChainRegistry.entries.map { it.chainIndex }.toSet().size)
        expected.forEach { (chainIndex, upstreamIds) ->
            val chain = requireNotNull(OnchainChainRegistry.find(chainIndex))
            assertEquals(upstreamIds.first, chain.dexScreenerId)
            assertEquals(upstreamIds.second, chain.geckoTerminalId)
            assertTrue(OnchainChainIconRegistry.resolveIconUrl(chainIndex).isNullOrBlank().not())
        }
    }

    @Test
    fun `evm addresses normalize to lowercase while solana keeps case`() {
        val evm = "0xABCDEFabcdefABCDEFabcdefABCDEFabcdefABCD"
        val solana = "So11111111111111111111111111111111111111112"

        assertEquals(evm.lowercase(), normalizeOnchainAddress(ChainFamily.EVM, evm))
        assertEquals(solana, normalizeOnchainAddress(ChainFamily.SOL, solana))
        assertTrue(looksLikeOnchainAddress(ChainFamily.EVM, evm))
        assertTrue(looksLikeOnchainAddress(ChainFamily.SOL, solana))
        assertFalse(looksLikeOnchainAddress(ChainFamily.EVM, "0x1234"))
    }

    @Test
    fun `legacy and new ids share the same semantic identity`() {
        val address = "0xABCDEFabcdefABCDEFabcdefABCDEFabcdefABCD"
        val legacy = WatchItem(
            id = "okx-onchain:1:$address",
            symbol = "TEST",
            name = "Test",
            exchangeSource = ExchangeSource.ONCHAIN,
            marketType = MarketType.ONCHAIN_TOKEN,
            chainFamily = ChainFamily.EVM,
            chainIndex = "1",
            tokenAddress = address,
            addedAt = 1L
        )
        val fresh = legacy.copy(
            id = "onchain:1:${address.lowercase()}",
            tokenAddress = address.lowercase()
        )

        assertEquals(fresh.semanticKey, legacy.semanticKey)
        assertEquals("onchain:1:${address.lowercase()}", legacy.semanticKey)
    }

    @Test
    fun `gecko terminal interval mapping uses only supported upstream aggregates`() {
        val mappings = mapOf(
            KlineInterval.ONE_MINUTE to Triple("minute", 1, 1),
            KlineInterval.FIVE_MINUTES to Triple("minute", 5, 1),
            KlineInterval.FIFTEEN_MINUTES to Triple("minute", 15, 1),
            KlineInterval.ONE_HOUR to Triple("hour", 1, 1),
            KlineInterval.FOUR_HOURS to Triple("hour", 4, 1),
            KlineInterval.ONE_DAY to Triple("day", 1, 1),
            KlineInterval.THREE_DAYS to Triple("day", 1, 3),
            KlineInterval.ONE_WEEK to Triple("day", 1, 7),
            KlineInterval.ONE_MONTH to Triple("day", 1, 30)
        )

        mappings.forEach { (interval, expected) ->
            val actual = interval.toGeckoTerminalInterval()
            assertEquals(expected.first, actual.timeframe)
            assertEquals(expected.second, actual.aggregate)
            assertEquals(expected.third, actual.localAggregationDays)
        }
    }

    @Test
    fun `onchain month label states that it is a fixed thirty day interval`() {
        assertEquals("30D", KlineInterval.ONE_MONTH.toOnchainDisplayLabel())
        assertEquals("1W", KlineInterval.ONE_WEEK.toOnchainDisplayLabel())
        assertEquals("1M", KlineInterval.ONE_MONTH.label)
    }

    @Test
    fun `onchain refresh setting is clamped and snapped`() {
        assertEquals(10, AppPreferences.normalizeOnchainRefreshIntervalSeconds(1))
        assertEquals(45, AppPreferences.normalizeOnchainRefreshIntervalSeconds(43))
        assertEquals(120, AppPreferences.normalizeOnchainRefreshIntervalSeconds(999))
    }
}
