package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.local.WatchItemEntity

internal data class OverlayOrderUpdate(
    val id: String,
    val order: Long
)

internal object OverlayOrderManager {
    const val ORDER_STEP: Long = 1024L

    fun nextOrder(items: List<WatchItemEntity>): Long {
        return (items.mapNotNull { it.overlayOrder }.maxOrNull() ?: 0L) + ORDER_STEP
    }

    fun reorder(
        items: List<WatchItemEntity>,
        itemId: String,
        targetBeforeId: String?
    ): List<OverlayOrderUpdate> {
        val orderedItems = items.sortedWith(
            compareBy<WatchItemEntity>(
                { it.overlayOrder ?: Long.MAX_VALUE },
                { it.addedAt },
                { it.id }
            )
        )
        val moving = orderedItems.firstOrNull { it.id == itemId } ?: return emptyList()
        if (targetBeforeId == itemId) return emptyList()

        val withoutMoving = orderedItems.filterNot { it.id == itemId }.toMutableList()
        val insertionIndex = when (targetBeforeId) {
            null -> withoutMoving.size
            else -> withoutMoving.indexOfFirst { it.id == targetBeforeId }
                .takeIf { it >= 0 }
                ?: return emptyList()
        }
        withoutMoving.add(insertionIndex, moving)

        return withoutMoving.mapIndexed { index, item ->
            OverlayOrderUpdate(
                id = item.id,
                order = (index + 1L) * ORDER_STEP
            )
        }
    }
}
