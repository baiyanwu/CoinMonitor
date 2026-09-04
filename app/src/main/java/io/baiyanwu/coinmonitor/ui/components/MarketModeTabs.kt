package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

private const val MARKET_MODE_PAGE_COUNT = 2

@Composable
fun MarketModeTabs(
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = CoinMonitorThemeTokens.colors
    val tabWidth = 96.dp
    val indicatorWidth = 32.dp
    val resolvedPage = selectedPage.coerceIn(0, MARKET_MODE_PAGE_COUNT - 1)
    val indicatorOffset by animateDpAsState(
        targetValue = if (resolvedPage == 0) 32.dp else 128.dp,
        animationSpec = tween(durationMillis = 180),
        label = "market-mode-indicator"
    )

    Box(
        modifier = modifier
            .width(tabWidth * MARKET_MODE_PAGE_COUNT)
            .height(44.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            repeat(MARKET_MODE_PAGE_COUNT) { index ->
                val selected = index == resolvedPage
                val labelRes = if (index == 0) {
                    R.string.search_mode_exchange
                } else {
                    R.string.search_mode_onchain
                }
                Box(
                    modifier = Modifier
                        .width(tabWidth)
                        .height(44.dp)
                        .clickable { onSelectPage(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(labelRes),
                        color = if (selected) colors.accent else colors.secondaryText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                .width(indicatorWidth)
                .height(3.dp)
                .background(colors.accent, RoundedCornerShape(2.dp))
        )
    }
}
