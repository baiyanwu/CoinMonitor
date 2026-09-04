package io.baiyanwu.coinmonitor.data.refresh

import io.baiyanwu.coinmonitor.domain.model.ChainFamily
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OnchainRefreshMode
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnchainRefreshPolicyTest {
    @Test
    fun `same chain is split into batches of at most thirty unique addresses`() {
        val items = (1..31).map { index ->
            onchainItem(
                id = "item-$index",
                chainIndex = "1",
                address = "0x${index.toString(16).padStart(40, '0')}"
            )
        } + onchainItem(
            id = "duplicate",
            chainIndex = "1",
            address = "0x${1.toString(16).padStart(40, '0')}"
        )

        val batches = OnchainRefreshPolicy.buildRequestBatches(items)

        assertEquals(2, batches.size)
        assertEquals(listOf(31, 1), batches.map { it.items.size })
        assertTrue(batches.all { it.items.map(WatchItem::id) == it.items.map(WatchItem::id).sorted() })
    }

    @Test
    fun `smart and fixed modes enforce the source cache window`() {
        val items = listOf(onchainItem("item", "1", "0x${"1".padStart(40, '0')}"))

        val smart = OnchainRefreshPolicy.resolve(items, OnchainRefreshMode.SMART, 120)
        val fixedBelowMinimum = OnchainRefreshPolicy.resolve(items, OnchainRefreshMode.FIXED, 10)
        val fixed = OnchainRefreshPolicy.resolve(items, OnchainRefreshMode.FIXED, 60)

        assertEquals(OnchainRefreshPlan(requestBatchCount = 1, cycleIntervalSeconds = 30), smart)
        assertEquals(OnchainRefreshPlan(requestBatchCount = 1, cycleIntervalSeconds = 30), fixedBelowMinimum)
        assertEquals(OnchainRefreshPlan(requestBatchCount = 1, cycleIntervalSeconds = 60), fixed)
    }

    @Test
    fun `request pacing spreads batches and never drops below one second`() {
        assertEquals(15_000L, OnchainRefreshPolicy.requestSpacingMillis(30, 2))
        assertEquals(1_000L, OnchainRefreshPolicy.requestSpacingMillis(30, 31))
        assertEquals(0L, OnchainRefreshPolicy.requestSpacingMillis(30, 0))
    }

    private fun onchainItem(id: String, chainIndex: String, address: String) = WatchItem(
        id = id,
        symbol = id,
        name = id,
        exchangeSource = ExchangeSource.ONCHAIN,
        marketType = MarketType.ONCHAIN_TOKEN,
        chainFamily = ChainFamily.EVM,
        chainIndex = chainIndex,
        tokenAddress = address,
        addedAt = 1L
    )
}
