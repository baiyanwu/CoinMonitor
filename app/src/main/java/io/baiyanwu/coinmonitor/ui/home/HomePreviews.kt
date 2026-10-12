package io.baiyanwu.coinmonitor.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.data.repository.InMemoryQuoteRepository
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorBottomBar
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorBottomBarItem
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorIcons
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorDesignTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorTheme

@Preview(name = "首页 · 浅色", widthDp = 360, heightDp = 800)
@Composable
private fun HomeLightPreview() = HomePreview(darkTheme = false)

@Preview(name = "首页 · 深色", widthDp = 360, heightDp = 800)
@Composable
private fun HomeDarkPreview() = HomePreview(darkTheme = true)

@Preview(name = "首页 · 大字体", widthDp = 320, heightDp = 800, fontScale = 1.5f)
@Composable
private fun HomeLargeFontPreview() = HomePreview(darkTheme = false)

/** 预览数据只在 @Preview 调用，不参与实际行情或发起网络请求。 */
@Composable
private fun HomePreview(darkTheme: Boolean) {
    val items = remember {
        listOf("BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "AVAX/USDT", "LINK/USDT", "DOGE/USDT", "XRP/USDT").mapIndexed { index, symbol ->
            WatchItem(
                id = "preview-$index", symbol = symbol, name = symbol,
                exchangeSource = ExchangeSource.BINANCE, marketType = MarketType.CEX_SPOT,
                addedAt = index.toLong(), lastPrice = listOf(64200.0, 2400.0, 148.5, 621.3, 27.84, 12.68, 0.1234, 0.5812)[index],
                change24hPercent = if (index % 3 == 1) -0.6 else 1.8, lastUpdatedAt = 1L
            )
        }
    }
    val quotes = remember { InMemoryQuoteRepository().apply { seedFromWatchItems(items) } }
    CoinMonitorTheme(darkTheme = darkTheme) {
        CoinMonitorDesignTheme {
            Scaffold(bottomBar = {
                CoinMonitorBottomBar(
                    listOf(
                        CoinMonitorBottomBarItem("home", "行情", CoinMonitorIcons.Market),
                        CoinMonitorBottomBarItem("trending", "热门", CoinMonitorIcons.Trending),
                        CoinMonitorBottomBarItem("wallet", "钱包", CoinMonitorIcons.Wallet),
                        CoinMonitorBottomBarItem("settings", "设置", CoinMonitorIcons.Settings)
                    ), selectedId = "home", onSelect = {}
                )
            }) { insets ->
                Box(Modifier.fillMaxSize().padding(insets)) {
                    HomeScreen(
                        state = HomeUiState(isLoaded = true, items = items, overlayEnabled = true),
                        contentTopInset = 0.dp, contentBottomInset = 0.dp,
                        onNavigateSearch = {}, onNavigateOverlaySettings = {}, onNavigateOverlayItems = {},
                        quoteRepository = quotes, onRemoveWatchItem = {}, onToggleOverlay = {},
                        onSetHomePinned = { _, _ -> }, onMoveHomeItem = { _, _ -> },
                        onMovePinnedHomeItem = { _, _ -> }, onRefresh = {}
                    )
                }
            }
        }
    }
}
