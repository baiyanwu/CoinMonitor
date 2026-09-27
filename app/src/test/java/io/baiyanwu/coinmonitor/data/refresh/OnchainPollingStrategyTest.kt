package io.baiyanwu.coinmonitor.data.refresh

import io.baiyanwu.coinmonitor.domain.model.ChainFamily
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OnchainDataProvider
import io.baiyanwu.coinmonitor.domain.model.OnchainRefreshMode
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import org.junit.Assert.assertEquals
import org.junit.Test

class OnchainPollingStrategyTest {
    @Test
    fun `DexScreener strategy splits at thirty unique addresses`() {
        val items = (1..31).map(::target) + target(1, id = "duplicate")

        val batches = OnchainPollingStrategies.dexScreener.createBatches(
            provider = OnchainDataProvider.DEX_SCREENER,
            items = items,
            mode = OnchainRefreshMode.SMART,
            fixedIntervalSeconds = 120
        )

        assertEquals(2, batches.size)
        assertEquals(listOf(31, 1), batches.map { it.itemIds.size })
        assertEquals(listOf(30_000L, 30_000L), batches.map { it.cycleIntervalMillis })
    }

    @Test
    fun `OKX strategy keeps its own one hundred address batch limit`() {
        val items = (1..101).map(::target)

        val batches = OnchainPollingStrategies.okxDex.createBatches(
            provider = OnchainDataProvider.OKX_DEX,
            items = items,
            mode = OnchainRefreshMode.SMART,
            fixedIntervalSeconds = 30
        )

        assertEquals(listOf(100, 1), batches.map { it.itemIds.size })
        assertEquals(listOf(67_000L, 67_000L), batches.map { it.cycleIntervalMillis })
    }

    @Test
    fun `OKX strategy reserves part of the free monthly quota`() {
        val batches = OnchainPollingStrategies.okxDex.createBatches(
            provider = OnchainDataProvider.OKX_DEX,
            items = listOf(target(1)),
            mode = OnchainRefreshMode.SMART,
            fixedIntervalSeconds = 30
        )

        assertEquals(34_000L, batches.single().cycleIntervalMillis)
    }

    @Test
    fun `fixed mode respects provider minimum cycle`() {
        val target = listOf(target(1))
        val belowMinimum = OnchainPollingStrategies.dexScreener.createBatches(
            OnchainDataProvider.DEX_SCREENER,
            target,
            OnchainRefreshMode.FIXED,
            fixedIntervalSeconds = 10
        )
        val fixed = OnchainPollingStrategies.dexScreener.createBatches(
            OnchainDataProvider.DEX_SCREENER,
            target,
            OnchainRefreshMode.FIXED,
            fixedIntervalSeconds = 60
        )

        assertEquals(30_000L, belowMinimum.single().cycleIntervalMillis)
        assertEquals(60_000L, fixed.single().cycleIntervalMillis)
    }

    @Test
    fun `retry backoff belongs to provider strategy`() {
        assertEquals(5_000L, OnchainPollingStrategies.dexScreener.retryDelayMillis(1))
        assertEquals(10_000L, OnchainPollingStrategies.dexScreener.retryDelayMillis(2))
        assertEquals(20_000L, OnchainPollingStrategies.okxDex.retryDelayMillis(3))
        assertEquals(30_000L, OnchainPollingStrategies.okxDex.retryDelayMillis(4))
    }

    @Test
    fun `smart cycle expands when provider batches cannot fit minimum request gap`() {
        val strategy = ChainBatchingOnchainPollingStrategy(
            maxAddressesPerRequest = 1,
            smartCycleIntervalSeconds = 30,
            minimumRequestGapMillis = 1_000L
        )

        val batches = strategy.createBatches(
            provider = OnchainDataProvider.DEX_SCREENER,
            items = (1..31).map(::target),
            mode = OnchainRefreshMode.SMART,
            fixedIntervalSeconds = 30
        )

        assertEquals(31_000L, batches.first().cycleIntervalMillis)
    }

    private fun target(index: Int, id: String = "item-$index"): PreparedOnchainPollingItem {
        val item = WatchItem(
            id = id,
            symbol = id,
            name = id,
            exchangeSource = ExchangeSource.ONCHAIN,
            marketType = MarketType.ONCHAIN_TOKEN,
            chainFamily = ChainFamily.EVM,
            chainIndex = "1",
            tokenAddress = "0x${index.toString(16).padStart(40, '0')}",
            addedAt = 1L
        )
        return PreparedOnchainPollingItem(item, item)
    }
}
