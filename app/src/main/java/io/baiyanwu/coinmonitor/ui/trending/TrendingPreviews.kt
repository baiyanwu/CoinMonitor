package io.baiyanwu.coinmonitor.ui.trending

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.network.TrendingFailure
import io.baiyanwu.coinmonitor.domain.model.*
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorBottomBar
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorBottomBarItem
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorIcons
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

@Preview(name = "热门 · 浅色", widthDp = 390, heightDp = 840)
@Composable
private fun TrendingLightPreview() = TrendingPagePreview()

@Preview(name = "热门 · 深色", widthDp = 390, heightDp = 840)
@Composable
private fun TrendingDarkPreview() = TrendingPagePreview(darkTheme = true)

@Preview(name = "热门 · 大字体英文", widthDp = 320, heightDp = 800, fontScale = 1.5f, locale = "en")
@Composable
private fun TrendingLargeFontPreview() = TrendingPagePreview()

@Preview(name = "热门 · 缓存与刷新失败", widthDp = 390, heightDp = 840)
@Composable
private fun TrendingRetainedPreview() = TrendingPagePreview(failedRefresh = true)

@Preview(name = "热门 · 凭证未配置", widthDp = 390, heightDp = 840)
@Composable
private fun TrendingEmptyPreview() = TrendingPagePreview(empty = true)

/** 只用于 IDE Preview，不创建 ViewModel，也不访问网络或钱包。 */
@Composable
private fun TrendingPagePreview(darkTheme: Boolean = false, failedRefresh: Boolean = false, empty: Boolean = false) {
    val now = 1_791_519_120_000L
    val rows = listOf("PICO", "MIRA", "SABLE", "NOVA", "LUMI", "ORBIT", "ASTRA", "EMBER", "VELA", "ION")
        .mapIndexed { index, symbol -> TrendingToken(
            chainIndex = "501", chainTitle = "Solana", address = "PreviewAddress${index}OnlyForLayoutExample",
            symbol = symbol, marketCap = 4_800_000.0 + index * 100_000, change = if (index % 2 == 0) 12.4 else -4.6,
            smartMoney = 12.0 + index, links = TrendingSource.entries.associateWith { "https://example.invalid" }, risk = index == 3
        ) }
    val snapshot = TrendingSnapshot(rows, now - 10_000)
    val state = TrendingUiState(
        ready = if (empty) setOf(TrendingSource.PUMP) else TrendingSource.entries.toSet(),
        snapshots = if (empty) emptyMap() else TrendingSource.entries.associateWith { source ->
            snapshot.copy(failure = if (failedRefresh && source == TrendingSource.GMGN) TrendingFailure.NETWORK else null)
        },
        sharedSnapshot = if (empty) null else snapshot.copy(newDates = mapOf(rows[0].id to now - 10_000)),
        now = now
    )
    CoinMonitorTheme(darkTheme = darkTheme) {
        Scaffold(
            containerColor = CoinMonitorThemeTokens.design.colors.pageBackground,
            contentWindowInsets = WindowInsets(0),
            bottomBar = { CoinMonitorBottomBar(
                items = listOf(
                    CoinMonitorBottomBarItem("home", stringResource(R.string.tab_market), CoinMonitorIcons.Market),
                    CoinMonitorBottomBarItem("trending", stringResource(R.string.tab_trending), CoinMonitorIcons.Trending),
                    CoinMonitorBottomBarItem("wallet", stringResource(R.string.tab_wallet), CoinMonitorIcons.Wallet),
                    CoinMonitorBottomBarItem("settings", stringResource(R.string.tab_settings), CoinMonitorIcons.Settings)
                ), selectedId = "trending", onSelect = {}, windowInsets = WindowInsets(0)
            ) }
        ) { padding ->
            TrendingScreen(
                state, padding.calculateTopPadding(), padding.calculateBottomPadding(),
                onSelectBoard = {}, onToggleSource = {}, onOptions = { _, _ -> }, onDisplay = { _, _ -> },
                onRefresh = {}, onOpenApiSettings = {}, onOpenBrowser = {}
            )
        }
    }
}
