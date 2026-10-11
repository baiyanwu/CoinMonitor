package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.domain.model.*
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorDesignTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.ui.trending.TrendingScreen
import io.baiyanwu.coinmonitor.ui.trending.TrendingSnapshot
import io.baiyanwu.coinmonitor.ui.trending.TrendingUiState
import io.baiyanwu.coinmonitor.ui.wallet.WalletActivityPage
import io.baiyanwu.coinmonitor.ui.wallet.WalletAssetsPage
import io.baiyanwu.coinmonitor.ui.wallet.WalletUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import kotlin.math.abs

/** Exercise the actual page lists with isolated fixture data, without a wallet or ViewModel. */
class FixedListsScrollTest {
    @get:Rule val rule = createComposeRule()
    private var background = Color.Unspecified

    @Test fun walletAssetsKeepFixedRoundedViewport() = verifyWallet(false)
    @Test fun walletActivityKeepsFixedRoundedViewport() = verifyWallet(true)
    @Test fun sharedTrendingKeepsFixedRoundedViewport() = verifyTrending(TrendingBoard.SHARED)
    @Test fun gmgnTrendingKeepsFixedRoundedViewport() = verifyTrending(TrendingBoard.GMGN)
    @Test fun pumpTrendingKeepsFixedRoundedViewport() = verifyTrending(TrendingBoard.PUMP)
    @Test fun okxTrendingKeepsFixedRoundedViewport() = verifyTrending(TrendingBoard.OKX)

    private fun setContent(content: @Composable () -> Unit) {
        rule.setContent {
            CoinMonitorTheme(darkTheme = false) {
                CoinMonitorDesignTheme {
                    val color = CoinMonitorThemeTokens.design.colors.pageBackground
                    SideEffect { background = color }
                    Box(Modifier.width(340.dp).height(560.dp).background(color)) { content() }
                }
            }
        }
    }

    private fun verifyWallet(activity: Boolean) {
        val assets = List(80) { index ->
            SelfCustodyAsset("asset-$index", WalletNetwork.SOLANA, null, "Solana", "SOL", 9,
                "1000000000", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, null, true, true)
        }
        val activities = List(80) { index ->
            WalletActivity("activity-$index", "fixture", WalletNetwork.SOLANA, "hash-$index",
                WalletActivityDirection.INCOMING, "SOL", BigDecimal.ONE, null, index.toLong(),
                WalletActivityStatus.CONFIRMED, false)
        }
        val state = WalletUiState(
            portfolio = WalletPortfolio(assets, activities, tokenIndexAvailable = true),
            okxCredentialsReady = true,
            networkConfiguration = WalletNetworkConfiguration(alchemyApiKey = "fixture-only")
        )
        setContent {
            val listState = rememberLazyListState()
            if (activity) WalletActivityPage(state, listState, {}, true)
            else WalletAssetsPage(state, listState, {}, true, {})
        }
        val tag = if (activity) "wallet-activity-container" else "wallet-assets-container"
        verifyViewport(tag, "$tag-list")
    }

    private fun verifyTrending(board: TrendingBoard) {
        val rows = List(80) { index ->
            TrendingToken("501", "Solana", "fixture-address-$index", "T$index", marketCap = 100.0,
                links = TrendingSource.entries.associateWith { "https://example.com" })
        }
        val snapshot = TrendingSnapshot(rows, updatedAt = 1L)
        val state = TrendingUiState(board = board, ready = TrendingSource.entries.toSet(),
            snapshots = TrendingSource.entries.associateWith { snapshot }, sharedSnapshot = snapshot)
        setContent { TrendingScreen(state, 0.dp, 0.dp, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}) }
        verifyViewport("trending-${board.name}-container", "trending-${board.name}-list")
    }

    private fun verifyViewport(containerTag: String, listTag: String) {
        val container = rule.onNodeWithTag(containerTag)
        val list = rule.onNodeWithTag(listTag)
        val bounds = container.fetchSemanticsNode().boundsInRoot
        val listBounds = list.fetchSemanticsNode().boundsInRoot
        for (index in listOf(0, 30, 79, 0)) {
            list.performScrollToIndex(index)
            rule.waitForIdle()
            assertEquals("Rounded container must stay fixed", bounds, container.fetchSemanticsNode().boundsInRoot)
            assertEquals("Internal list viewport must stay fixed", listBounds, list.fetchSemanticsNode().boundsInRoot)
            val pixels = container.captureToImage().toPixelMap()
            for ((x, y) in listOf(1 to 1, pixels.width - 2 to 1,
                1 to pixels.height - 2, pixels.width - 2 to pixels.height - 2)) {
                val actual = pixels[x, y]
                assertTrue("All four corners must keep clipping the scrolling rows",
                    abs(actual.red - background.red) < 0.02f &&
                        abs(actual.green - background.green) < 0.02f &&
                        abs(actual.blue - background.blue) < 0.02f)
            }
        }
    }
}
