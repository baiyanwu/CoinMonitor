package io.baiyanwu.coinmonitor.overlay

import io.baiyanwu.coinmonitor.domain.model.WatchItem

data class OverlayBatch(
    val items: List<WatchItem>,
    val nextCursor: Int,
    val pageLabel: String?
)

object OverlayBatchPlanner {
    fun plan(items: List<WatchItem>, maxPerPage: Int, cursor: Int): OverlayBatch {
        return OverlayBatch(
            items = items,
            nextCursor = 0,
            pageLabel = null
        )
    }
}
