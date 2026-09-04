package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.local.WatchItemEntity
import io.baiyanwu.coinmonitor.domain.model.MarketType
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchlistHomeOrderManagerTest {
    @Test
    fun `next normal order should ignore pinned group`() {
        val items = listOf(
            watchItem(id = "normal-a", addedAt = 1L, homeOrder = 1024L),
            watchItem(
                id = "pinned-a",
                addedAt = 2L,
                homePinned = true,
                homeOrder = 99_999L,
                homePinnedOrder = 1024L
            ),
            watchItem(id = "normal-b", addedAt = 3L, homeOrder = 2048L)
        )

        val nextOrder = WatchlistHomeOrderManager.nextNormalOrder(items)

        assertEquals(3072L, nextOrder)
    }

    @Test
    fun `reorder normal group should only renumber normal items`() {
        val items = listOf(
            watchItem(id = "normal-a", addedAt = 1L, homeOrder = 1024L),
            watchItem(
                id = "pinned-a",
                addedAt = 2L,
                homePinned = true,
                homeOrder = 2048L,
                homePinnedOrder = 1024L
            ),
            watchItem(id = "normal-b", addedAt = 3L, homeOrder = 2048L)
        )

        val updates = WatchlistHomeOrderManager.reorderNormalGroup(
            items = items,
            itemId = "normal-b",
            targetBeforeId = "normal-a"
        )

        assertEquals(
            listOf(
                HomeOrderUpdate(id = "normal-b", order = 1024L),
                HomeOrderUpdate(id = "normal-a", order = 2048L)
            ),
            updates
        )
    }

    @Test
    fun `reorder pinned group should keep normal group untouched`() {
        val items = listOf(
            watchItem(
                id = "pinned-a",
                addedAt = 1L,
                homePinned = true,
                homeOrder = 1024L,
                homePinnedOrder = 1024L
            ),
            watchItem(id = "normal-a", addedAt = 2L, homeOrder = 2048L),
            watchItem(
                id = "pinned-b",
                addedAt = 3L,
                homePinned = true,
                homeOrder = 3072L,
                homePinnedOrder = 2048L
            )
        )

        val updates = WatchlistHomeOrderManager.reorderPinnedGroup(
            items = items,
            itemId = "pinned-b",
            targetBeforeId = "pinned-a"
        )

        assertEquals(
            listOf(
                HomeOrderUpdate(id = "pinned-b", order = 1024L),
                HomeOrderUpdate(id = "pinned-a", order = 2048L)
            ),
            updates
        )
    }

    @Test
    fun `reorder should only renumber items in the same market group`() {
        val items = listOf(
            watchItem(id = "exchange-a", addedAt = 1L, homeOrder = 1024L),
            watchItem(
                id = "onchain-a",
                addedAt = 2L,
                homeOrder = 2048L,
                marketType = MarketType.ONCHAIN_TOKEN.name
            ),
            watchItem(id = "exchange-b", addedAt = 3L, homeOrder = 3072L),
            watchItem(
                id = "onchain-b",
                addedAt = 4L,
                homeOrder = 4096L,
                marketType = MarketType.ONCHAIN_TOKEN.name
            )
        )

        val updates = WatchlistHomeOrderManager.reorderNormalGroup(
            items = items,
            itemId = "exchange-b",
            targetBeforeId = "exchange-a"
        )

        assertEquals(
            listOf(
                HomeOrderUpdate(id = "exchange-b", order = 1024L),
                HomeOrderUpdate(id = "exchange-a", order = 2048L)
            ),
            updates
        )
    }

    private fun watchItem(
        id: String,
        addedAt: Long,
        homePinned: Boolean = false,
        homeOrder: Long,
        homePinnedOrder: Long? = null,
        marketType: String = MarketType.CEX_SPOT.name
    ): WatchItemEntity {
        return WatchItemEntity(
            id = id,
            symbol = "$id/USDT",
            name = id,
            source = "BINANCE",
            marketType = marketType,
            chainFamily = null,
            chainIndex = null,
            tokenAddress = null,
            poolAddress = null,
            poolTokenSide = null,
            iconUrl = null,
            overlaySelected = false,
            overlayOrder = null,
            addedAt = addedAt,
            homePinned = homePinned,
            homeOrder = homeOrder,
            homePinnedOrder = homePinnedOrder,
            lastPrice = null,
            previousPrice = null,
            liveTrend = "NEUTRAL",
            change24hPercent = null,
            lastUpdatedAt = null
        )
    }
}
