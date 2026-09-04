package io.baiyanwu.coinmonitor.ui.settings.overlay

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OverlayDisplayType
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

@Composable
internal fun OverlayItemSelectionSection(
    settings: OverlaySettings,
    selectedItems: List<WatchItem>,
    availableItems: List<WatchItem>,
    onToggleItem: (String) -> Unit,
    onMoveItem: (String, String?) -> Unit,
    onDraggingChange: (Boolean) -> Unit = {}
) {
    val maxVisibleItems = when (settings.displayType) {
        OverlayDisplayType.ARRANGED -> settings.arranged.maxItems
        OverlayDisplayType.MARQUEE -> settings.marquee.maxItems
    }
    val watchlistIsEmpty = selectedItems.isEmpty() && availableItems.isEmpty()

    OverlaySettingsCard {
        Text(
            text = stringResource(R.string.overlay_select_items),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(
                R.string.overlay_selected_summary,
                selectedItems.size,
                OverlaySettings.MAX_SELECTABLE_ITEMS,
                minOf(selectedItems.size, maxVisibleItems)
            ),
            modifier = Modifier.testTag("overlay-selected-summary"),
            style = MaterialTheme.typography.bodySmall,
            color = CoinMonitorThemeTokens.colors.secondaryText
        )
        Text(
            text = stringResource(R.string.overlay_order_scope_hint),
            style = MaterialTheme.typography.bodySmall,
            color = CoinMonitorThemeTokens.colors.tertiaryText
        )

        if (watchlistIsEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 104.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.overlay_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoinMonitorThemeTokens.colors.secondaryText
                )
            }
            return@OverlaySettingsCard
        }

        Text(
            text = stringResource(R.string.overlay_selected_items),
            style = MaterialTheme.typography.labelLarge,
            color = CoinMonitorThemeTokens.colors.primaryText
        )
        if (selectedItems.isEmpty()) {
            Text(
                text = stringResource(R.string.overlay_selected_empty),
                style = MaterialTheme.typography.bodySmall,
                color = CoinMonitorThemeTokens.colors.secondaryText
            )
        } else {
            ReorderableOverlayItems(
                items = selectedItems,
                maxVisibleItems = maxVisibleItems,
                onToggleItem = onToggleItem,
                onMoveItem = onMoveItem,
                onDraggingChange = onDraggingChange
            )
        }

        val exchangeItems = availableItems.filter { it.marketType != MarketType.ONCHAIN_TOKEN }
        val onchainItems = availableItems.filter { it.marketType == MarketType.ONCHAIN_TOKEN }
        if (exchangeItems.isNotEmpty() || onchainItems.isNotEmpty()) {
            Text(
                text = stringResource(R.string.overlay_available_items),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = CoinMonitorThemeTokens.colors.primaryText
            )
            AvailableOverlayItemGroup(
                title = stringResource(R.string.search_mode_exchange),
                items = exchangeItems,
                onToggleItem = onToggleItem
            )
            AvailableOverlayItemGroup(
                title = stringResource(R.string.search_mode_onchain),
                items = onchainItems,
                onToggleItem = onToggleItem
            )
        }
    }
}

@Composable
private fun ReorderableOverlayItems(
    items: List<WatchItem>,
    maxVisibleItems: Int,
    onToggleItem: (String) -> Unit,
    onMoveItem: (String, String?) -> Unit,
    onDraggingChange: (Boolean) -> Unit
) {
    var draggedItems by remember { mutableStateOf<List<WatchItem>?>(null) }
    var dragState by remember { mutableStateOf<OverlayItemDragState?>(null) }
    var pendingOrderIds by remember { mutableStateOf<List<String>?>(null) }
    // Bounds are an imperative drag cache. Observing every write as Compose state adds
    // needless snapshot work while a pointer event is being handled.
    val itemBounds = remember { mutableMapOf<String, Rect>() }
    val displayItems = draggedItems ?: items
    val upstreamOrderIds = items.map(WatchItem::id)

    LaunchedEffect(upstreamOrderIds, pendingOrderIds) {
        val pendingIds = pendingOrderIds ?: return@LaunchedEffect
        val membershipChanged = upstreamOrderIds.toSet() != pendingIds.toSet()
        if (upstreamOrderIds == pendingIds || membershipChanged) {
            draggedItems = null
            pendingOrderIds = null
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        displayItems.forEachIndexed { index, item ->
            key(item.id) {
                val isDragging = dragState?.itemId == item.id
                OverlaySelectedItemRow(
                    item = item,
                    position = index + 1,
                    isCurrentlyVisible = index < maxVisibleItems,
                    dragOffsetY = if (isDragging) dragState?.dragOffsetY ?: 0f else 0f,
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .onGloballyPositioned { coordinates ->
                            if (!isDragging) {
                                itemBounds[item.id] = coordinates.boundsInParent()
                            }
                        },
                    onToggleItem = { onToggleItem(item.id) },
                    onDragStart = {
                        draggedItems = displayItems
                        pendingOrderIds = null
                        dragState = OverlayItemDragState(itemId = item.id)
                        onDraggingChange(true)
                    },
                    onDragBy = { dragAmount ->
                        val current = dragState
                        if (current != null && current.itemId == item.id) {
                            var updated = current.copy(
                                dragOffsetY = current.dragOffsetY + dragAmount
                            )
                            var reorderedItems = draggedItems ?: items
                            var draggedBounds = itemBounds[item.id]
                            val dragDirection = dragAmount.compareTo(0f)
                            var remainingSwaps = reorderedItems.lastIndex

                            // Keep one pointer event directional and bounded. Without these
                            // guards, stale/equal bounds can make adjacent rows swap back and
                            // forth forever on the main thread and trigger an ANR.
                            while (draggedBounds != null && remainingSwaps > 0) {
                                val fromIndex = reorderedItems.indexOfFirst { it.id == item.id }
                                if (fromIndex < 0) break
                                val draggedCenterY = draggedBounds.center.y + updated.dragOffsetY
                                val previousItem = reorderedItems.getOrNull(fromIndex - 1)
                                val nextItem = reorderedItems.getOrNull(fromIndex + 1)
                                val previousBounds = previousItem?.let { itemBounds[it.id] }
                                val nextBounds = nextItem?.let { itemBounds[it.id] }
                                val target = when {
                                    dragDirection < 0 &&
                                    previousItem != null &&
                                        previousBounds != null &&
                                        draggedCenterY < previousBounds.center.y -> {
                                        previousItem to previousBounds
                                    }

                                    dragDirection > 0 &&
                                    nextItem != null &&
                                        nextBounds != null &&
                                        draggedCenterY > nextBounds.center.y -> {
                                        nextItem to nextBounds
                                    }

                                    else -> null
                                } ?: break

                                val (targetItem, targetBounds) = target
                                val targetIndex = reorderedItems.indexOfFirst {
                                    it.id == targetItem.id
                                }
                                reorderedItems = reorderedItems.toMutableList().apply {
                                    add(targetIndex, removeAt(fromIndex))
                                }
                                itemBounds[item.id] = targetBounds
                                itemBounds[targetItem.id] = draggedBounds
                                updated = updated.copy(
                                    dragOffsetY = updated.dragOffsetY -
                                        (targetBounds.top - draggedBounds.top),
                                    didReorder = true
                                )
                                draggedBounds = targetBounds
                                remainingSwaps -= 1
                            }

                            draggedItems = reorderedItems
                            dragState = updated
                        }
                    },
                    onDragEnd = {
                        val finalItems = draggedItems ?: items
                        val orderWasCommitted = finalizeOverlayItemDrag(
                            dragState = dragState,
                            items = finalItems,
                            onMoveItem = onMoveItem
                        )
                        dragState = null
                        if (orderWasCommitted) {
                            // Keep the dropped order visible until Room acknowledges the
                            // same order. Clearing it immediately exposes one old-data frame.
                            draggedItems = finalItems
                            pendingOrderIds = finalItems.map(WatchItem::id)
                        } else {
                            draggedItems = null
                            pendingOrderIds = null
                        }
                        onDraggingChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun OverlaySelectedItemRow(
    item: WatchItem,
    position: Int,
    isCurrentlyVisible: Boolean,
    dragOffsetY: Float,
    modifier: Modifier = Modifier,
    onToggleItem: () -> Unit,
    onDragStart: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    val dragDescription = stringResource(R.string.overlay_drag_handle_description, item.symbol)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragBy by rememberUpdatedState(onDragBy)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("overlay-selected-item-${item.id}")
            .graphicsLayer { translationY = dragOffsetY }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = position.toString(),
            modifier = Modifier.width(24.dp),
            style = MaterialTheme.typography.labelLarge,
            color = colors.tertiaryText
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.symbol,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.primaryText
                )
                if (!isCurrentlyVisible) {
                    Text(
                        text = stringResource(R.string.overlay_not_currently_visible),
                        modifier = Modifier.testTag("overlay-selected-hidden-${item.id}"),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.tertiaryText
                    )
                }
            }
            Text(
                text = overlayItemSubtitle(item),
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText
            )
        }
        Switch(
            checked = true,
            onCheckedChange = { onToggleItem() },
            colors = CoinMonitorComponentDefaults.switchColors()
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .testTag("overlay-drag-handle-${item.id}")
                .semantics { contentDescription = dragDescription }
                .pointerInput(item.id) {
                    awaitEachGesture {
                        val down = awaitOverlayDragDown()
                        down.consume()
                        currentOnDragStart()
                        try {
                            var change = down
                            while (change.pressed) {
                                val event = awaitPointerEvent()
                                change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val dragAmount = change.position.y - change.previousPosition.y
                                if (dragAmount != 0f) {
                                    change.consume()
                                    currentOnDragBy(dragAmount)
                                }
                            }
                        } finally {
                            currentOnDragEnd()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = null,
                tint = colors.secondaryText
            )
        }
    }
}

@Composable
private fun AvailableOverlayItemGroup(
    title: String,
    items: List<WatchItem>,
    onToggleItem: (String) -> Unit
) {
    if (items.isEmpty()) return
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = CoinMonitorThemeTokens.colors.tertiaryText
    )
    items.forEach { item ->
        Box(modifier = Modifier.testTag("overlay-available-item-${item.id}")) {
            OverlaySettingSwitchRow(
                title = item.symbol,
                subtitle = overlayItemSubtitle(item),
                horizontalPadding = 0.dp,
                verticalPadding = 0.dp,
                checked = false,
                onCheckedChange = { onToggleItem(item.id) }
            )
        }
    }
}

@Composable
private fun overlayItemSubtitle(item: WatchItem): String {
    val marketLabel = when (item.marketType) {
        MarketType.CEX_SPOT -> stringResource(R.string.overlay_market_spot)
        MarketType.CEX_USDT_FUTURES -> stringResource(R.string.market_tag_usdt_futures)
        MarketType.ONCHAIN_TOKEN -> stringResource(R.string.search_mode_onchain)
    }
    return "${item.exchangeSource.title} · $marketLabel"
}

private fun finalizeOverlayItemDrag(
    dragState: OverlayItemDragState?,
    items: List<WatchItem>,
    onMoveItem: (String, String?) -> Unit
): Boolean {
    val current = dragState ?: return false
    if (!current.didReorder) return false
    val index = items.indexOfFirst { it.id == current.itemId }
    if (index < 0) return false
    onMoveItem(current.itemId, items.getOrNull(index + 1)?.id)
    return true
}

private data class OverlayItemDragState(
    val itemId: String,
    val dragOffsetY: Float = 0f,
    val didReorder: Boolean = false
)

private suspend fun AwaitPointerEventScope.awaitOverlayDragDown(): PointerInputChange {
    while (true) {
        val event = awaitPointerEvent()
        event.changes.firstOrNull { it.pressed }?.let { return it }
    }
}
