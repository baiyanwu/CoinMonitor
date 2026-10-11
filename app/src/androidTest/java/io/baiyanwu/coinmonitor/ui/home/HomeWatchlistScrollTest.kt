package io.baiyanwu.coinmonitor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorDesignTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class HomeWatchlistScrollTest {
    @get:Rule val rule = createComposeRule()

    @Test fun roundedViewportStaysFixedWhileRowsScroll() {
        lateinit var state: LazyListState
        var background = Color.Unspecified
        rule.setContent {
            CoinMonitorTheme(darkTheme = false) {
                CoinMonitorDesignTheme {
                    val listState = rememberLazyListState()
                    val pageColor = CoinMonitorThemeTokens.design.colors.pageBackground
                    SideEffect { state = listState; background = pageColor }
                    Box(Modifier.width(320.dp).height(380.dp).background(pageColor)) {
                        HomeWatchlistContainer(listState) {
                            items(80, key = { it }, contentType = { "test-row" }) { index ->
                                Box(Modifier.fillMaxWidth().height(52.dp)
                                    .background(Color(0xFFEAE4FA)).testTag("row-$index")) {
                                    Text("Row $index")
                                }
                            }
                        }
                    }
                }
            }
        }
        val container = rule.onNodeWithTag("home-watchlist-container")
        val list = rule.onNodeWithTag("home-watchlist")
        val bounds = container.fetchSemanticsNode().boundsInRoot
        val listBounds = list.fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("row-79").assertDoesNotExist()
        for (index in listOf(0, 30, 79, 0)) {
            list.performScrollToIndex(index)
            rule.waitForIdle()
            assertEquals("Container must not move with its rows", bounds,
                container.fetchSemanticsNode().boundsInRoot)
            assertEquals("Scroll viewport must stay inside the fixed container", listBounds,
                list.fetchSemanticsNode().boundsInRoot)
            if (index > 0) rule.runOnIdle { assertTrue(state.firstVisibleItemIndex > 0) }
            val pixels = container.captureToImage().toPixelMap()
            for ((x, y) in listOf(1 to 1, pixels.width - 2 to 1,
                1 to pixels.height - 2, pixels.width - 2 to pixels.height - 2)) {
                val actual = pixels[x, y]
                assertTrue("Rows must remain clipped at all four rounded corners",
                    abs(actual.red - background.red) < 0.02f &&
                        abs(actual.green - background.green) < 0.02f &&
                        abs(actual.blue - background.blue) < 0.02f)
            }
        }
    }
}
