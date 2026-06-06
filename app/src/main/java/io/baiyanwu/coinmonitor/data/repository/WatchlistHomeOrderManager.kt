package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.local.WatchItemEntity

internal data class HomeOrderUpdate(
    val id: String,
    val order: Long
)

internal object WatchlistHomeOrderManager {
    const val ORDER_STEP: Long = 1024L

    fun nextNormalOrder(items: List<WatchItemEntity>): Long {
        val maxOrder = items
            .filterNot { it.homePinned }
            .maxOfOrNull { it.homeOrder }
            ?: 0L
        return maxOrder + ORDER_STEP
    }

    fun nextPinnedOrder(items: List<WatchItemEntity>): Long {
        val maxOrder = items
            .filter { it.homePinned }
            .mapNotNull { it.homePinnedOrder }
            .maxOrNull()
            ?: 0L
        return maxOrder + ORDER_STEP
    }

    fun reorderNormalGroup(
        items: List<WatchItemEntity>,
        itemId: String,
        targetBeforeId: String?
    ): List<HomeOrderUpdate> {
        return reorderGroup(
            items = items,
            itemId = itemId,
            targetBeforeId = targetBeforeId,
            pinned = false
        )
    }

    fun reorderPinnedGroup(
        items: List<WatchItemEntity>,
        itemId: String,
        targetBeforeId: String?
    ): List<HomeOrderUpdate> {
        return reorderGroup(
            items = items,
            itemId = itemId,
            targetBeforeId = targetBeforeId,
            pinned = true
        )
    }

    private fun reorderGroup(
        items: List<WatchItemEntity>,
        itemId: String,
        targetBeforeId: String?,
        pinned: Boolean
    ): List<HomeOrderUpdate> {
        val group = items
            .filter { it.homePinned == pinned }
            .sortedWith(compareGroup(pinned))
        val moving = group.firstOrNull { it.id == itemId } ?: return emptyList()
        val withoutMoving = group.filterNot { it.id == itemId }.toMutableList()
        val insertionIndex = targetBeforeId
            ?.let { targetId -> withoutMoving.indexOfFirst { it.id == targetId }.takeIf { it >= 0 } }
            ?: withoutMoving.size
        withoutMoving.add(insertionIndex, moving)
        return withoutMoving.mapIndexed { index, item ->
            HomeOrderUpdate(
                id = item.id,
                order = (index + 1L) * ORDER_STEP
            )
        }
    }

    private fun compareGroup(pinned: Boolean): Comparator<WatchItemEntity> {
        return if (pinned) {
            compareBy<WatchItemEntity>(
                { it.homePinnedOrder ?: Long.MAX_VALUE },
                { it.addedAt },
                { it.id }
            )
        } else {
            compareBy<WatchItemEntity>(
                { it.homeOrder },
                { it.addedAt },
                { it.id }
            )
        }
    }
}
