package io.baiyanwu.coinmonitor.overlay

import io.baiyanwu.coinmonitor.domain.model.WatchItem

data class OverlayBatch(
    val items: List<WatchItem>,
    val nextCursor: Int,
    val pageLabel: String?
)

object OverlayBatchPlanner {
    fun plan(items: List<WatchItem>, maxPerPage: Int, cursor: Int): OverlayBatch {
        if (items.isEmpty()) return OverlayBatch(emptyList(), 0, null)
        if (items.size <= maxPerPage) {
            return OverlayBatch(items, 0, null)
        }

        val pages = items.chunked(maxPerPage)
        val safeCursor = cursor.mod(pages.size)
        val nextCursor = (safeCursor + 1).mod(pages.size)
        return OverlayBatch(
            items = pages[safeCursor],
            nextCursor = nextCursor,
            pageLabel = "${safeCursor + 1}/${pages.size}"
        )
    }
}

