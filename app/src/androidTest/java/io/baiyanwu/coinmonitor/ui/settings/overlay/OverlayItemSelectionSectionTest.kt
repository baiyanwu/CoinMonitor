package io.baiyanwu.coinmonitor.ui.settings.overlay

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import io.baiyanwu.coinmonitor.domain.model.ArrangedOverlaySettings
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarqueeOverlaySettings
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OverlayDisplayType
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.ui.settings.OverlayItemsSettingsScreen
import io.baiyanwu.coinmonitor.ui.settings.OverlayItemsSettingsUiState
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorTheme
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayItemSelectionSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedAndAvailableItemsUseSeparateSectionsAndActiveDisplayLimit() {
        val selectedItems = listOf(
            item("exchange-a", "BTCUSDT", ExchangeSource.BINANCE, MarketType.CEX_SPOT, 1024L),
            item("onchain", "WETH", ExchangeSource.ONCHAIN, MarketType.ONCHAIN_TOKEN, 2048L),
            item("exchange-b", "ETHUSDT", ExchangeSource.OKX, MarketType.CEX_USDT_FUTURES, 3072L)
        )
        val availableItems = listOf(
            item("available-exchange", "SOLUSDT", ExchangeSource.BINANCE, MarketType.CEX_SPOT),
            item("available-onchain", "BONK", ExchangeSource.ONCHAIN, MarketType.ONCHAIN_TOKEN)
        )

        composeRule.setContent {
            CoinMonitorTheme {
                OverlayItemSelectionSection(
                    settings = OverlaySettings(
                        arranged = ArrangedOverlaySettings(maxItems = 2)
                    ),
                    selectedItems = selectedItems,
                    availableItems = availableItems,
                    onToggleItem = {},
                    onMoveItem = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("overlay-selected-summary").assertExists()
        selectedItems.forEach { selected ->
            composeRule.onNodeWithTag("overlay-selected-item-${selected.id}").assertExists()
            composeRule.onNodeWithTag("overlay-drag-handle-${selected.id}").assertExists()
        }
        composeRule.onNodeWithTag("overlay-selected-hidden-exchange-a").assertDoesNotExist()
        composeRule.onNodeWithTag("overlay-selected-hidden-onchain").assertDoesNotExist()
        composeRule.onNodeWithTag("overlay-selected-hidden-exchange-b").assertExists()
        composeRule.onNodeWithTag("overlay-available-item-available-exchange").assertExists()
        composeRule.onNodeWithTag("overlay-available-item-available-onchain").assertExists()
        val visibleRowHeight = composeRule
            .onNodeWithTag("overlay-selected-item-exchange-a")
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        val hiddenRowHeight = composeRule
            .onNodeWithTag("overlay-selected-item-exchange-b")
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        assertEquals(visibleRowHeight, hiddenRowHeight, 0.5f)
    }

    @Test
    fun marqueeDisplayLimitControlsTheTemporarilyHiddenMarker() {
        val selectedItems = listOf(
            item("first", "BTCUSDT", ExchangeSource.BINANCE, MarketType.CEX_SPOT, 1024L),
            item("second", "WETH", ExchangeSource.ONCHAIN, MarketType.ONCHAIN_TOKEN, 2048L)
        )

        composeRule.setContent {
            CoinMonitorTheme {
                OverlayItemSelectionSection(
                    settings = OverlaySettings(
                        displayType = OverlayDisplayType.MARQUEE,
                        marquee = MarqueeOverlaySettings(maxItems = 1)
                    ),
                    selectedItems = selectedItems,
                    availableItems = emptyList(),
                    onToggleItem = {},
                    onMoveItem = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("overlay-selected-hidden-first").assertDoesNotExist()
        composeRule.onNodeWithTag("overlay-selected-hidden-second").assertExists()
    }

    @Test
    fun dedicatedPageDragHandleCompletesReorderInsideScrollableContent() {
        var moveRequest: Pair<String, String?>? = null
        val selectedItems = listOf(
            item("first", "BTCUSDT", ExchangeSource.BINANCE, MarketType.CEX_SPOT, 1024L),
            item("second", "ETHUSDT", ExchangeSource.OKX, MarketType.CEX_SPOT, 2048L),
            item("third", "SOLUSDT", ExchangeSource.BINANCE, MarketType.CEX_SPOT, 3072L),
            item("fourth", "WETH", ExchangeSource.ONCHAIN, MarketType.ONCHAIN_TOKEN, 4096L)
        )

        composeRule.setContent {
            CoinMonitorTheme {
                OverlayItemsSettingsScreen(
                    state = OverlayItemsSettingsUiState(
                        settings = OverlaySettings(
                            arranged = ArrangedOverlaySettings(maxItems = 3)
                        ),
                        selectedItems = selectedItems,
                        isLoaded = true
                    ),
                    onBack = {},
                    onToggleItem = {},
                    onMoveItem = { id, targetBeforeId ->
                        moveRequest = id to targetBeforeId
                    }
                )
            }
        }

        val thirdCenterY = composeRule
            .onNodeWithTag("overlay-selected-item-third")
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .y
        val fourthCenterY = composeRule
            .onNodeWithTag("overlay-selected-item-fourth")
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .y
        composeRule.onNodeWithTag("overlay-drag-handle-fourth").performTouchInput {
            down(center)
            moveBy(
                Offset(x = 0f, y = thirdCenterY - fourthCenterY - 1f),
                delayMillis = 100L
            )
            up()
        }

        composeRule.runOnIdle {
            assertEquals("fourth" to "third", moveRequest)
        }
        val thirdCenterAfterDrop = composeRule
            .onNodeWithTag("overlay-selected-item-third")
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .y
        val fourthCenterAfterDrop = composeRule
            .onNodeWithTag("overlay-selected-item-fourth")
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .y
        assertTrue(
            "Dropped order must stay visible while persistence is pending",
            fourthCenterAfterDrop < thirdCenterAfterDrop
        )
    }

    @Test
    fun largeDragMoveCanCrossTheWholeListWithoutLooping() {
        var moveRequest: Pair<String, String?>? = null
        val selectedItems = listOf(
            item("first", "BTCUSDT", ExchangeSource.BINANCE, MarketType.CEX_SPOT, 1024L),
            item("second", "ETHUSDT", ExchangeSource.OKX, MarketType.CEX_SPOT, 2048L),
            item("third", "SOLUSDT", ExchangeSource.BINANCE, MarketType.CEX_SPOT, 3072L),
            item("fourth", "WETH", ExchangeSource.ONCHAIN, MarketType.ONCHAIN_TOKEN, 4096L)
        )

        composeRule.setContent {
            CoinMonitorTheme {
                OverlayItemsSettingsScreen(
                    state = OverlayItemsSettingsUiState(
                        settings = OverlaySettings(),
                        selectedItems = selectedItems,
                        isLoaded = true
                    ),
                    onBack = {},
                    onToggleItem = {},
                    onMoveItem = { id, targetBeforeId ->
                        moveRequest = id to targetBeforeId
                    }
                )
            }
        }

        val firstCenterY = composeRule
            .onNodeWithTag("overlay-selected-item-first")
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .y
        val fourthCenterY = composeRule
            .onNodeWithTag("overlay-selected-item-fourth")
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .y
        composeRule.onNodeWithTag("overlay-drag-handle-fourth").performTouchInput {
            down(center)
            moveBy(
                Offset(x = 0f, y = firstCenterY - fourthCenterY - 1f),
                delayMillis = 100L
            )
            up()
        }

        composeRule.runOnIdle {
            assertEquals("fourth" to "first", moveRequest)
        }
    }

    @Test
    fun consecutiveDragsUseTheLatestPersistedOrder() {
        val moveRequests = mutableListOf<Pair<String, String?>>()
        val selectedItemsState = mutableStateOf(
            listOf(
                item("first", "BTCUSDT", ExchangeSource.BINANCE, MarketType.CEX_SPOT, 1024L),
                item("second", "ETHUSDT", ExchangeSource.OKX, MarketType.CEX_SPOT, 2048L),
                item("third", "SOLUSDT", ExchangeSource.BINANCE, MarketType.CEX_SPOT, 3072L),
                item("fourth", "WETH", ExchangeSource.ONCHAIN, MarketType.ONCHAIN_TOKEN, 4096L)
            )
        )

        composeRule.setContent {
            CoinMonitorTheme {
                OverlayItemsSettingsScreen(
                    state = OverlayItemsSettingsUiState(
                        settings = OverlaySettings(),
                        selectedItems = selectedItemsState.value,
                        isLoaded = true
                    ),
                    onBack = {},
                    onToggleItem = {},
                    onMoveItem = { id, targetBeforeId ->
                        moveRequests += id to targetBeforeId
                        val currentItems = selectedItemsState.value
                        val movingItem = currentItems.first { it.id == id }
                        val remainingItems = currentItems.filterNot { it.id == id }
                        val targetIndex = targetBeforeId?.let { targetId ->
                            remainingItems.indexOfFirst { it.id == targetId }
                                .takeIf { it >= 0 }
                        } ?: remainingItems.size
                        selectedItemsState.value = remainingItems.toMutableList().apply {
                            add(targetIndex, movingItem)
                        }
                    }
                )
            }
        }

        dragItemBefore(itemId = "fourth", targetId = "third")
        composeRule.runOnIdle {
            assertEquals(listOf("fourth" to "third"), moveRequests)
        }

        dragItemBefore(itemId = "fourth", targetId = "second")
        composeRule.runOnIdle {
            assertEquals(
                listOf("fourth" to "third", "fourth" to "second"),
                moveRequests
            )
        }
    }

    @Test
    fun pendingDropDoesNotMaskASelectionMembershipChange() {
        val selectedItemsState = mutableStateOf(
            listOf(
                item("first", "BTCUSDT", ExchangeSource.BINANCE, MarketType.CEX_SPOT, 1024L),
                item("second", "ETHUSDT", ExchangeSource.OKX, MarketType.CEX_SPOT, 2048L),
                item("third", "SOLUSDT", ExchangeSource.BINANCE, MarketType.CEX_SPOT, 3072L),
                item("fourth", "WETH", ExchangeSource.ONCHAIN, MarketType.ONCHAIN_TOKEN, 4096L)
            )
        )

        composeRule.setContent {
            CoinMonitorTheme {
                OverlayItemsSettingsScreen(
                    state = OverlayItemsSettingsUiState(
                        settings = OverlaySettings(),
                        selectedItems = selectedItemsState.value,
                        isLoaded = true
                    ),
                    onBack = {},
                    onToggleItem = {},
                    onMoveItem = { _, _ -> }
                )
            }
        }

        dragItemBefore(itemId = "fourth", targetId = "third")
        composeRule.runOnIdle {
            selectedItemsState.value = selectedItemsState.value.filterNot { it.id == "third" }
        }

        composeRule.onNodeWithTag("overlay-selected-item-third").assertDoesNotExist()
        composeRule.onNodeWithTag("overlay-selected-item-fourth").assertExists()
    }

    private fun dragItemBefore(itemId: String, targetId: String) {
        val targetCenterY = composeRule
            .onNodeWithTag("overlay-selected-item-$targetId")
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .y
        val itemCenterY = composeRule
            .onNodeWithTag("overlay-selected-item-$itemId")
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .y

        composeRule.onNodeWithTag("overlay-drag-handle-$itemId").performTouchInput {
            down(center)
            moveBy(
                Offset(x = 0f, y = targetCenterY - itemCenterY - 1f),
                delayMillis = 100L
            )
            up()
        }
    }

    private fun item(
        id: String,
        symbol: String,
        source: ExchangeSource,
        marketType: MarketType,
        overlayOrder: Long? = null
    ): WatchItem {
        return WatchItem(
            id = id,
            symbol = symbol,
            name = symbol,
            exchangeSource = source,
            marketType = marketType,
            overlaySelected = overlayOrder != null,
            overlayOrder = overlayOrder,
            addedAt = overlayOrder ?: 0L
        )
    }
}
