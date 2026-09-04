package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.local.WatchItemEntity
import io.baiyanwu.coinmonitor.domain.model.MarketType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayOrderManagerTest {
    @Test
    fun `next order appends after current selected items`() {
        val items = listOf(
            watchItem(id = "exchange", overlayOrder = 1024L),
            watchItem(id = "onchain", overlayOrder = 3072L, onchain = true)
        )

        assertEquals(4096L, OverlayOrderManager.nextOrder(items))
    }

    @Test
    fun `reorder crosses market groups and renumbers the global list`() {
        val items = listOf(
            watchItem(id = "exchange-a", overlayOrder = 1024L),
            watchItem(id = "exchange-b", overlayOrder = 2048L),
            watchItem(id = "onchain", overlayOrder = 3072L, onchain = true)
        )

        val updates = OverlayOrderManager.reorder(
            items = items,
            itemId = "onchain",
            targetBeforeId = "exchange-a"
        )

        assertEquals(
            listOf(
                OverlayOrderUpdate("onchain", 1024L),
                OverlayOrderUpdate("exchange-a", 2048L),
                OverlayOrderUpdate("exchange-b", 3072L)
            ),
            updates
        )
    }

    @Test
    fun `null target moves item to the end`() {
        val items = listOf(
            watchItem(id = "a", overlayOrder = 1024L),
            watchItem(id = "b", overlayOrder = 2048L),
            watchItem(id = "c", overlayOrder = 3072L)
        )

        val updates = OverlayOrderManager.reorder(items, "a", null)

        assertEquals(listOf("b", "c", "a"), updates.map { it.id })
    }

    @Test
    fun `invalid item or target leaves order unchanged`() {
        val items = listOf(
            watchItem(id = "a", overlayOrder = 1024L),
            watchItem(id = "b", overlayOrder = 2048L)
        )

        assertTrue(OverlayOrderManager.reorder(items, "missing", "a").isEmpty())
        assertTrue(OverlayOrderManager.reorder(items, "a", "missing").isEmpty())
        assertTrue(OverlayOrderManager.reorder(items, "a", "a").isEmpty())
    }

    private fun watchItem(
        id: String,
        overlayOrder: Long?,
        onchain: Boolean = false
    ): WatchItemEntity {
        return WatchItemEntity(
            id = id,
            symbol = id.uppercase(),
            name = id,
            source = if (onchain) "ONCHAIN" else "BINANCE",
            marketType = if (onchain) {
                MarketType.ONCHAIN_TOKEN.name
            } else {
                MarketType.CEX_SPOT.name
            },
            chainFamily = null,
            chainIndex = null,
            tokenAddress = null,
            poolAddress = null,
            poolTokenSide = null,
            iconUrl = null,
            overlaySelected = true,
            overlayOrder = overlayOrder,
            addedAt = overlayOrder ?: 0L,
            homePinned = false,
            homeOrder = 0L,
            homePinnedOrder = null,
            lastPrice = null,
            previousPrice = null,
            liveTrend = "NEUTRAL",
            change24hPercent = null,
            lastUpdatedAt = null
        )
    }
}
